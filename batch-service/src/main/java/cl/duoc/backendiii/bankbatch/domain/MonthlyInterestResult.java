package cl.duoc.backendiii.bankbatch.domain;

import java.math.BigDecimal;

// This record represents the result of calculating monthly interest for a specific account.
// It contains details about the account, the calculated interest, and the final balance after applying the
public record MonthlyInterestResult(
        long accountId,
        String customerName,
        String accountType,
        int age,
        BigDecimal initialBalance,
        BigDecimal monthlyRate,
        BigDecimal interestAmount,
        BigDecimal finalBalance
) {
}
