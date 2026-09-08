package cl.duoc.backendiii.bff.web.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyTransaction(
        long transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String transactionType,
        boolean anomaly,
        String anomalyReason
) {
}

