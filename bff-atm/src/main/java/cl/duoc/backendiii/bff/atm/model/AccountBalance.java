package cl.duoc.backendiii.bff.atm.model;

import java.math.BigDecimal;

public record AccountBalance(
        long accountId,
        String customerName,
        String accountType,
        BigDecimal availableBalance
) {
}

