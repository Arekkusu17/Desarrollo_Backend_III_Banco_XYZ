package cl.duoc.backendiii.bankbatch.domain;

// This record represents a legacy interest account, containing details about a specific account in the legacy format.
// It's used for parsing and transforming legacy account data into the new format for further processing or reporting.
public record LegacyInterestAccount(
        String accountId,
        String customerName,
        String balance,
        String age,
        String accountType
) {
}
