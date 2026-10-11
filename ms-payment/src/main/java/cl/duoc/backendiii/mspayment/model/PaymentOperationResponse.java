package cl.duoc.backendiii.mspayment.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentOperationResponse(
        UUID operationId,
        PaymentType type,
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        PaymentStatus status,
        String description,
        LocalDateTime createdAt
) {
}
