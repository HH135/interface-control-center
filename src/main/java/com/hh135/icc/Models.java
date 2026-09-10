package com.hh135.icc;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class Models {
    private Models() {}
    public enum Scenario { SUCCESS, FAIL_ONCE, ALWAYS_FAIL, TIMEOUT }
    public enum Status { PENDING, SUCCESS, FAILED }
    public record CreateContract(
        @NotBlank @Size(max=64) String contractNumber,
        @NotBlank @Size(max=200) String customerName,
        @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal amount) {}
    public record SendRequest(@NotNull Scenario scenario) {}
    public record Contract(UUID id, String contractNumber, String customerName, BigDecimal amount,
                           OffsetDateTime createdAt) {}
    public record Message(UUID id, UUID contractId, Status status, Scenario scenario, int attemptCount,
                          String lastError, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record History(UUID id, UUID messageId, UUID batchId, int attemptNumber, Status status,
                          String errorCode, String detail, OffsetDateTime processedAt) {}
    public record Batch(UUID id, String jobName, String status, int totalCount, int successCount,
                        int failureCount, OffsetDateTime startedAt, OffsetDateTime finishedAt) {}
    public record DailySummary(String date, String timezone, long total, long success, long failed) {}
}
