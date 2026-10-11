package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.events.PaymentCompletedEvent;
import cl.duoc.backendiii.events.TopicNames;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
public class PaymentOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final PaymentRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final TransactionTemplate transactions;

    public PaymentOutboxPublisher(JdbcTemplate jdbc, PaymentRepository repository,
                                  KafkaTemplate<String, Object> kafka, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.kafka = kafka;
        transactions = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${payment.outbox.delay:2000}")
    public void publish() {
        try {
            transactions.executeWithoutResult(status -> {
                var ids = jdbc.query("""
                        SELECT operation_id FROM payment_outbox WHERE published=FALSE
                        ORDER BY operation_id LIMIT 20 FOR UPDATE SKIP LOCKED
                        """, (rs, row) -> (UUID) rs.getObject(1));
                for (UUID id : ids) {
                    var operation = repository.findById(id).orElseThrow();
                    var event = new PaymentCompletedEvent(id, id, Instant.now(), operation.type().name(),
                            operation.sourceAccountId(), operation.targetAccountId(), operation.amount());
                    try {
                        kafka.send(TopicNames.PAYMENT_COMPLETED, id.toString(), event).get(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    jdbc.update("UPDATE payment_outbox SET published=TRUE WHERE operation_id=?", id);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Publicacion pendiente; se reintentara el outbox de pagos: {}", ex.getMessage());
        }
    }
}
