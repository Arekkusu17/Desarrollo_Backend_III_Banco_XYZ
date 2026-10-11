package cl.duoc.backendiii.bankbatch.domain;

// This record represents a legacy transaction, containing details about a specific transaction in the legacy format.
// It's used for parsing and transforming legacy transaction data into the new format for further processing or reporting
public record LegacyTransaction(
        String id,
        String date,
        String amount,
        String type,
        Boolean sourceDuplicate
) {
    public LegacyTransaction(String id, String date, String amount, String type) {
        this(id, date, amount, type, null);
    }
}
