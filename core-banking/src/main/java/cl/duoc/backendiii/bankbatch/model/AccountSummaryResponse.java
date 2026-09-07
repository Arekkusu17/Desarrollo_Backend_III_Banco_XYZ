package cl.duoc.backendiii.bankbatch.model;

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

