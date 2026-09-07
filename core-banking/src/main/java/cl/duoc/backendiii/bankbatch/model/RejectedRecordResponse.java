package cl.duoc.backendiii.bankbatch.model;

import java.time.LocalDateTime;

public record RejectedRecordResponse(
        long id,
        String processName,
        String recordKey,
        String reason,
        String payload,
        LocalDateTime createdAt
) {
}

