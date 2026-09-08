package cl.duoc.backendiii.bff.web.model;

import java.math.BigDecimal;

public record AccountSummary(
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

