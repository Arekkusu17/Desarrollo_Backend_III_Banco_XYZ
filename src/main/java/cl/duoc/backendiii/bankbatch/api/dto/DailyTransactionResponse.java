package cl.duoc.backendiii.bankbatch.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyTransactionResponse(
        long transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String transactionType,
        boolean anomaly,
        String anomalyReason
) {
}

