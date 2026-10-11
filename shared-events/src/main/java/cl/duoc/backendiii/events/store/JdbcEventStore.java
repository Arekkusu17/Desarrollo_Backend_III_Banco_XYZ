package cl.duoc.backendiii.events.store;

import cl.duoc.backendiii.events.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JdbcEventStore {
    private final String owner;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, Object> kafka;
    private final TransactionTemplate transactions;

    public JdbcEventStore(String owner, JdbcTemplate jdbc, ObjectMapper mapper,
                          KafkaTemplate<String, Object> kafka, PlatformTransactionManager manager) {
        this.owner = owner;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.kafka = kafka;
        transactions = new TransactionTemplate(manager);
    }

    public void consume(DomainEvent event, Runnable action) {
        transactions.executeWithoutResult(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO event_inbox(service_name, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING
                    """, owner, event.eventId());
            if (inserted > 0) action.run();
        });
    }

    public void enqueue(String topic, DomainEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO event_outbox(service_name, event_id, transaction_id, topic, event_type, payload)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, owner, event.eventId(), event.transactionId(), topic,
                    event.getClass().getName(), mapper.writeValueAsString(event));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void replay(String topic, DomainEvent event) {
        enqueue(topic, event);
        jdbc.update("UPDATE event_outbox SET published=FALSE WHERE service_name=? AND event_id=?",
                owner, event.eventId());
    }

    public void publish() {
        try {
            transactions.executeWithoutResult(status -> {
                var pending = jdbc.query("""
                        SELECT event_id, transaction_id, topic, event_type, payload FROM event_outbox
                        WHERE service_name=? AND published=FALSE ORDER BY created_at
                        LIMIT 20 FOR UPDATE SKIP LOCKED
                        """, (rs, row) -> new Pending((UUID) rs.getObject("event_id"),
                        rs.getObject("transaction_id").toString(), rs.getString("topic"),
                        rs.getString("event_type"), rs.getString("payload")), owner);
                for (Pending item : pending) {
                    try {
                        Class<?> type = Class.forName(item.type());
                        if (!DomainEvent.class.isAssignableFrom(type)
                                || !type.getPackageName().equals("cl.duoc.backendiii.events")) {
                            throw new IllegalStateException("Tipo de evento invalido");
                        }
                        Object event = mapper.readValue(item.payload(), type);
                        kafka.send(item.topic(), item.key(), event).get(5, TimeUnit.SECONDS);
                        jdbc.update("UPDATE event_outbox SET published=TRUE WHERE service_name=? AND event_id=?",
                                owner, item.id());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            });
        } catch (RuntimeException ex) {
            LoggerFactory.getLogger(JdbcEventStore.class)
                    .warn("Outbox {} pendiente; se reintentara: {}", owner, ex.getMessage());
        }
    }

    private record Pending(UUID id, String key, String topic, String type, String payload) {
    }
}
