package cl.duoc.backendiii.bankbatch.domain;

// This record represents a legacy annual entry, containing details about a specific transaction in the legacy format.
// It's used for parsing and transforming legacy transaction data into the new format for further processing or reporting
public record LegacyAnnualEntry(
        String accountId,
        String date,
        String transactionType,
        String amount,
        String description
) {
}
