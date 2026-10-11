package cl.duoc.backendiii.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(UUID eventId, UUID transactionId, Instant occurredAt,
                                    String type, Long sourceAccountId, Long targetAccountId,
                                    BigDecimal amount) implements DomainEvent {
}
