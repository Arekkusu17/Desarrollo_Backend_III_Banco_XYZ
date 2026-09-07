package cl.duoc.backendiii.bankbatch.domain;

// This record represents a rejected record, containing details about the process name, record key, reason for rejection, and the payload of the rejected record.
public record RejectedRecord(
        String processName,
        String recordKey,
        String reason,
        String payload
) {
}
