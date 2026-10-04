package cl.duoc.backendiii.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundsReservedEvent(
        UUID eventId,
        UUID transactionId,
        Instant occurredAt,
        long accountId,
        BigDecimal amount,
        BigDecimal availableBalanceAfterReserve
) implements DomainEvent {
}
