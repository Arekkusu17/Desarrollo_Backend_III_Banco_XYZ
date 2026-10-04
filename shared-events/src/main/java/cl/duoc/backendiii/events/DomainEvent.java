package cl.duoc.backendiii.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();

    UUID transactionId();

    Instant occurredAt();
}
