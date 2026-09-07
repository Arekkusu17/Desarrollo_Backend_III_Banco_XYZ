package cl.duoc.backendiii.bankbatch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// This record represents a summary of daily transactions, containing details about a specific transaction.
// It's the output of the DailyTransactionProcessor and can be used for further processing or reporting.
// It's used for both database persistence and CSV report generation.
public record DailyTransactionSummary(
        long transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String transactionType,
        boolean anomaly,
        String anomalyReason
) {
}
