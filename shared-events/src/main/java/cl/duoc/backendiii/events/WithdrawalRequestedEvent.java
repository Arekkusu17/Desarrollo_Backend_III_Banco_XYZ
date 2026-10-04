package cl.duoc.backendiii.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalRequestedEvent(
        UUID eventId,
        UUID transactionId,
        Instant occurredAt,
        long accountId,
        BigDecimal amount,
        String channel
) implements DomainEvent {
}
