package cl.duoc.backendiii.bff.common.model;

import java.math.BigDecimal;

public record AccountBalance(
        long accountId,
        String customerName,
        String accountType,
        BigDecimal availableBalance
) {
}
