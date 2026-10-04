package cl.duoc.backendiii.bff.atm.messaging;

import cl.duoc.backendiii.bff.atm.model.WithdrawalStatus;
import cl.duoc.backendiii.bff.atm.repository.WithdrawalTransactionRepository;
import cl.duoc.backendiii.events.FundsRejectedEvent;
import cl.duoc.backendiii.events.FundsReleaseRequestedEvent;
import cl.duoc.backendiii.events.RiskApprovedEvent;
import cl.duoc.backendiii.events.RiskRejectedEvent;
import cl.duoc.backendiii.events.TopicNames;
import cl.duoc.backendiii.events.WithdrawalCancelledEvent;
import cl.duoc.backendiii.events.WithdrawalConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AtmSagaListener {
    private static final Logger log = LoggerFactory.getLogger(AtmSagaListener.class);

    private final WithdrawalTransactionRepository repository;
    private final ProcessedEventStore processedEvents;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AtmSagaListener(WithdrawalTransactionRepository repository,
                           ProcessedEventStore processedEvents,
                           KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.processedEvents = processedEvents;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.FUNDS_REJECTED, groupId = "bff-atm-group")
    public void onFundsRejected(FundsRejectedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[IDEMPOTENCIA] FundsRejected duplicado ignorado eventId={}", event.eventId());
            return;
        }
        log.info("[CONSUMER] FundsRejected recibido transactionId={} reason={}",
                event.transactionId(), event.reason());
        repository.findById(event.transactionId()).ifPresent(transaction -> {
            transaction.cancel(WithdrawalStatus.FUNDS_REJECTED, event.reason());
            publishCancelled(event.transactionId(), event.accountId(), event.amount(), event.reason());
        });
    }

    @KafkaListener(topics = TopicNames.RISK_APPROVED, groupId = "bff-atm-group")
    public void onRiskApproved(RiskApprovedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[IDEMPOTENCIA] RiskApproved duplicado ignorado eventId={}", event.eventId());
            return;
        }
        log.info("[CONSUMER] RiskApproved recibido transactionId={}", event.transactionId());
        repository.findById(event.transactionId()).ifPresent(transaction -> {
            transaction.confirm(event.authorizationCode());
            WithdrawalConfirmedEvent confirmed = new WithdrawalConfirmedEvent(
                    UUID.randomUUID(),
                    event.transactionId(),
                    Instant.now(),
                    event.accountId(),
                    event.amount(),
                    event.authorizationCode()
            );
            kafkaTemplate.send(TopicNames.WITHDRAWAL_CONFIRMED, event.transactionId().toString(), confirmed);
            log.info("[PRODUCER] WithdrawalConfirmed publicado transactionId={}", event.transactionId());
        });
    }

    @KafkaListener(topics = TopicNames.RISK_REJECTED, groupId = "bff-atm-group")
    public void onRiskRejected(RiskRejectedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[IDEMPOTENCIA] RiskRejected duplicado ignorado eventId={}", event.eventId());
            return;
        }
        log.info("[CONSUMER] RiskRejected recibido transactionId={} reason={}",
                event.transactionId(), event.reason());
        repository.findById(event.transactionId()).ifPresent(transaction -> {
            transaction.cancel(WithdrawalStatus.RISK_REJECTED, event.reason());
            FundsReleaseRequestedEvent releaseRequested = new FundsReleaseRequestedEvent(
                    UUID.randomUUID(),
                    event.transactionId(),
                    Instant.now(),
                    event.accountId(),
                    event.amount(),
                    event.reason()
            );
            kafkaTemplate.send(TopicNames.FUNDS_RELEASE_REQUESTED, event.transactionId().toString(), releaseRequested);
            log.info("[COMPENSACION] FundsReleaseRequested publicado transactionId={}", event.transactionId());
            publishCancelled(event.transactionId(), event.accountId(), event.amount(), event.reason());
        });
    }

    private void publishCancelled(UUID transactionId, long accountId, java.math.BigDecimal amount, String reason) {
        WithdrawalCancelledEvent cancelled = new WithdrawalCancelledEvent(
                UUID.randomUUID(),
                transactionId,
                Instant.now(),
                accountId,
                amount,
                reason
        );
        kafkaTemplate.send(TopicNames.WITHDRAWAL_CANCELLED, transactionId.toString(), cancelled);
        log.info("[PRODUCER] WithdrawalCancelled publicado transactionId={}", transactionId);
    }
}
