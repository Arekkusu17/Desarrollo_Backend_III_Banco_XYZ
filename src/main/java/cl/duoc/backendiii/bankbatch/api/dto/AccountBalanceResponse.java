package cl.duoc.backendiii.bankbatch.api.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        long accountId,
        String customerName,
        String accountType,
        BigDecimal availableBalance
) {
}

