package cl.duoc.backendiii.bankbatch.api.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        long accountId,
        String customerName,
        String accountType,
        int age,
        BigDecimal initialBalance,
        BigDecimal monthlyRate,
        BigDecimal interestAmount,
        BigDecimal finalBalance
) {
}

