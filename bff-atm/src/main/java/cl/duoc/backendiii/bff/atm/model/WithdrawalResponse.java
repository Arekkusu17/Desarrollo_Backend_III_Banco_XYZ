package cl.duoc.backendiii.bff.atm.model;

import java.math.BigDecimal;

public record WithdrawalResponse(
        String channel,
        long accountId,
        BigDecimal requestedAmount,
        boolean approved,
        String message,
        String authorizationCode
) {
}

