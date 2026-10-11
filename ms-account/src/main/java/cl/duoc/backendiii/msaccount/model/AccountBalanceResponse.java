package cl.duoc.backendiii.msaccount.model;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        long accountId,
        String customerName,
        String accountType,
        BigDecimal availableBalance
) {
}
