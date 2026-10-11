package cl.duoc.backendiii.mspayment.model;

import java.math.BigDecimal;

public record PaymentRequest(
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        String description
) {
}
