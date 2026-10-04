package cl.duoc.backendiii.bff.atm.messaging;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessedEventStore {
    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    public boolean isFirstTime(UUID eventId) {
        return processedEventIds.add(eventId);
    }
}
