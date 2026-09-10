package com.hh135.icc;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.UUID;
import static com.hh135.icc.Models.*;

@Service
public class IccService {
    private final IccMapper mapper;
    private final SapSimulator simulator;
    public IccService(IccMapper mapper, SapSimulator simulator) { this.mapper = mapper; this.simulator = simulator; }
    @Transactional
    public Contract create(CreateContract request) {
        var contract = new Contract(UUID.randomUUID(), request.contractNumber().trim(), request.customerName().trim(), request.amount(), now());
        mapper.insertContract(contract);
        return contract;
    }
    public Contract contract(UUID id) { return required(mapper.contract(id)); }
    public Message message(UUID id) { return required(mapper.message(id)); }
    @Transactional
    public Message send(UUID contractId, Scenario scenario) {
        contract(contractId);
        var time = now();
        var message = new Message(UUID.randomUUID(), contractId, Status.PENDING, scenario, 0, null, time, time);
        // The unique contract_id constraint also protects concurrent initial sends.
        mapper.insertMessage(message);
        return process(message, "INITIAL_SEND");
    }
    @Transactional
    public Message retry(UUID id) {
        var message = required(mapper.lockMessage(id));
        if (message.status() != Status.FAILED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only FAILED messages can be retried");
        return process(message, "MANUAL_RETRY");
    }
    private Message process(Message message, String job) {
        var started = now();
        int attempt = message.attemptCount() + 1;
        var result = simulator.send(message.scenario(), attempt);
        var status = result.success() ? Status.SUCCESS : Status.FAILED;
        var finished = now();
        var batchId = UUID.randomUUID();
        mapper.insertBatch(new Batch(batchId, job, status.name(), 1, result.success() ? 1 : 0, result.success() ? 0 : 1, started, finished));
        var updated = new Message(message.id(), message.contractId(), status, message.scenario(), attempt,
            result.success() ? null : result.detail(), message.createdAt(), finished);
        mapper.updateMessage(updated);
        mapper.insertHistory(new History(UUID.randomUUID(), message.id(), batchId, attempt, status, result.errorCode(), result.detail(), finished));
        return updated;
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DailySummary summary(LocalDate date) {
        var zone = ZoneId.of("Asia/Seoul");
        var from = date.atStartOfDay(zone).toOffsetDateTime();
        var until = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        long success = mapper.count(from, until, Status.SUCCESS);
        long failed = mapper.count(from, until, Status.FAILED);
        return new DailySummary(date.toString(), zone.getId(), success + failed, success, failed);
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private static <T> T required(T value) {
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        return value;
    }
}
