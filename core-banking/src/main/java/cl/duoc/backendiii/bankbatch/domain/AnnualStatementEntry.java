package cl.duoc.backendiii.bankbatch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// This record represents an entry in the annual statement, containing details about a specific transaction.
// It's the output of the AnnualStatementReportWriter and can be used for further processing or reporting.
// It's used for both database persistence and CSV report generation.
public record AnnualStatementEntry(
        long accountId,
        LocalDate transactionDate,
        String transactionType,
        BigDecimal amount,
        String description,
        String auditFlag
) {
}
