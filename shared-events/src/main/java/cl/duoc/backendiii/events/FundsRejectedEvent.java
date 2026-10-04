package cl.duoc.backendiii.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundsRejectedEvent(
        UUID eventId,
        UUID transactionId,
        Instant occurredAt,
        long accountId,
        BigDecimal amount,
        String reason
) implements DomainEvent {
}
