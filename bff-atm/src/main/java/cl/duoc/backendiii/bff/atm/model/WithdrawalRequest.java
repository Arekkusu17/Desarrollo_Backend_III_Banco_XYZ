package cl.duoc.backendiii.bff.atm.model;

import java.math.BigDecimal;

public record WithdrawalRequest(
        BigDecimal amount,
        String pin
) {
}

