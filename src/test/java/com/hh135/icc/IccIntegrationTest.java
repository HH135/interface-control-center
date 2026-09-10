package com.hh135.icc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IccIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IccService service;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM interface_history");
        jdbc.update("DELETE FROM batch_history");
        jdbc.update("DELETE FROM interface_message");
        jdbc.update("DELETE FROM contract");
    }
    String create(String number) throws Exception {
        var result = mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("contractNumber", number, "customerName", "테스트 고객", "amount", 10000))))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
    String send(String id, String scenario, String expected) throws Exception {
        var result = mvc.perform(post("/api/contracts/{id}/send", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"scenario\":\"" + scenario + "\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
    @Test void failureRetryHistoryAndReport() throws Exception {
        var contract = create("C-001");
        var message = send(contract, "FAIL_ONCE", "FAILED");
        mvc.perform(post("/api/messages/{id}/retry", message)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS")).andExpect(jsonPath("$.attemptCount").value(2))
            .andExpect(jsonPath("$.lastError").isEmpty());
        mvc.perform(get("/api/messages/{id}/history", message)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].errorCode").value("SAP_UNAVAILABLE"))
            .andExpect(jsonPath("$[1].status").value("SUCCESS"));
        mvc.perform(get("/api/reports/daily").param("date", LocalDate.now(ZoneId.of("Asia/Seoul")).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.success").value(1)).andExpect(jsonPath("$.failed").value(1));
        mvc.perform(get("/api/batches")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/messages/{id}/retry", message)).andExpect(status().isConflict());
        mvc.perform(post("/api/contracts/{id}/send", contract).contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"SUCCESS\"}"))
            .andExpect(status().isConflict());
    }
    @Test void successAndReadEndpoints() throws Exception {
        var id = create("C-002");
        var message = send(id, "SUCCESS", "SUCCESS");
        mvc.perform(get("/api/contracts/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.contractNumber").value("C-002"));
        mvc.perform(get("/api/contracts")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/messages/{id}", message)).andExpect(status().isOk());
        mvc.perform(get("/api/messages")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/messages").param("offset", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void permanentFailureAndTimeoutAreRecorded() throws Exception {
        for (String scenario : List.of("ALWAYS_FAIL", "TIMEOUT")) {
            var message = send(create(scenario), scenario, "FAILED");
            mvc.perform(post("/api/messages/{id}/retry", message)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.attemptCount").value(2));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interface_history WHERE status='FAILED'", Integer.class)).isEqualTo(4);
    }
    @Test void validatesRequestsAndDuplicates() throws Exception {
        create("DUPLICATE");
        mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"contractNumber\":\"DUPLICATE\",\"customerName\":\"A\",\"amount\":1}"))
            .andExpect(status().isConflict());
        for (String amount : List.of("0", "-1", "1.001")) {
            mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractNumber\":\"BAD\",\"customerName\":\"A\",\"amount\":"+amount+"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/contracts").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/messages").param("limit", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/messages").param("offset", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/contracts/bad-id")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/contracts/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/api/messages/{id}/retry", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports/daily").param("date", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/contracts/{id}/send", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void concurrentRetriesOnlyOneSucceedsAfterRecovery() throws Exception {
        var id = UUID.fromString(send(create("CONCURRENT"), "FAIL_ONCE", "FAILED"));
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                gate.await();
                try { service.retry(id); return true; }
                catch (org.springframework.web.server.ResponseStatusException e) {
                    assertThat(e.getStatusCode().value()).isEqualTo(409); return false;
                }
            };
            var first = executor.submit(task); var second = executor.submit(task); gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        assertThat(service.message(id).attemptCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interface_history", Integer.class)).isEqualTo(2);
    }
    @Test void reportUsesKoreanMidnightAndExclusiveEnd() throws Exception {
        var message = send(create("BOUNDARY"), "FAIL_ONCE", "FAILED");
        service.retry(UUID.fromString(message));
        jdbc.update("UPDATE interface_history SET processed_at=? WHERE attempt_number=1", OffsetDateTime.parse("2026-09-09T15:00:00Z"));
        jdbc.update("UPDATE interface_history SET processed_at=? WHERE attempt_number=2", OffsetDateTime.parse("2026-09-10T15:00:00Z"));
        var report = service.summary(LocalDate.of(2026,9,10));
        assertThat(report.total()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(service.summary(LocalDate.of(2026,9,11)).success()).isEqualTo(1);
        assertThat(service.summary(LocalDate.of(2026,9,12)).total()).isZero();
    }
}
