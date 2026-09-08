package cl.duoc.backendiii.bff.web.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountMovement(
        long accountId,
        LocalDate transactionDate,
        String transactionType,
        BigDecimal amount,
        String description,
        String auditFlag
) {
}

