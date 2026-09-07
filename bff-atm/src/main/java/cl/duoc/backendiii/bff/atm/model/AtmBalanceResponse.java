package cl.duoc.backendiii.bff.atm.model;

import java.math.BigDecimal;

public record AtmBalanceResponse(
        String channel,
        long accountId,
        BigDecimal availableBalance,
        String currency
) {
}

