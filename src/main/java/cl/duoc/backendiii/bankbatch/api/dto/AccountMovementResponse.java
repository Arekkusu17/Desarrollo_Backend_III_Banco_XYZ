package cl.duoc.backendiii.bankbatch.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountMovementResponse(
        long accountId,
        LocalDate transactionDate,
        String transactionType,
        BigDecimal amount,
        String description,
        String auditFlag
) {
}

