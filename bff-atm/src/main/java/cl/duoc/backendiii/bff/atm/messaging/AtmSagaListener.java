package cl.duoc.backendiii.bff.atm.messaging;

import cl.duoc.backendiii.bff.atm.model.WithdrawalStatus;
import cl.duoc.backendiii.bff.atm.repository.WithdrawalTransactionRepository;
import cl.duoc.backendiii.events.*;
import cl.duoc.backendiii.events.store.JdbcEventStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class AtmSagaListener {
    private final WithdrawalTransactionRepository repository;
    private final JdbcEventStore events;

    public AtmSagaListener(WithdrawalTransactionRepository repository, JdbcEventStore events) {
        this.repository = repository;
        this.events = events;
    }

    @KafkaListener(topics = TopicNames.FUNDS_REJECTED, groupId = "bff-atm-group")
    public void onFundsRejected(FundsRejectedEvent event) {
        events.consume(event, () -> {
            var transaction = repository.findById(event.transactionId()).orElseThrow();
            if (transaction.getStatus() != WithdrawalStatus.PENDING) return;
            transaction.cancel(WithdrawalStatus.FUNDS_REJECTED, event.reason());
            repository.save(transaction);
            cancelled(event.transactionId(), event.accountId(), event.amount(), event.reason());
        });
    }

    @KafkaListener(topics = TopicNames.RISK_APPROVED, groupId = "bff-atm-group")
    public void onRiskApproved(RiskApprovedEvent event) {
        events.consume(event, () -> {
            var transaction = repository.findById(event.transactionId()).orElseThrow();
            if (transaction.getStatus() != WithdrawalStatus.PENDING) return;
            transaction.confirm(event.authorizationCode());
            repository.save(transaction);
            events.enqueue(TopicNames.WITHDRAWAL_CONFIRMED, new WithdrawalConfirmedEvent(
                    UUID.randomUUID(), event.transactionId(), Instant.now(), event.accountId(),
                    event.amount(), event.authorizationCode()));
        });
    }

    @KafkaListener(topics = TopicNames.RISK_REJECTED, groupId = "bff-atm-group")
    public void onRiskRejected(RiskRejectedEvent event) {
        events.consume(event, () -> {
            var transaction = repository.findById(event.transactionId()).orElseThrow();
            if (transaction.getStatus() != WithdrawalStatus.PENDING) return;
            transaction.cancel(WithdrawalStatus.RISK_REJECTED, event.reason());
            repository.save(transaction);
            events.enqueue(TopicNames.FUNDS_RELEASE_REQUESTED, new FundsReleaseRequestedEvent(
                    UUID.randomUUID(), event.transactionId(), Instant.now(), event.accountId(),
                    event.amount(), event.reason()));
            cancelled(event.transactionId(), event.accountId(), event.amount(), event.reason());
        });
    }

    private void cancelled(UUID transactionId, long accountId, java.math.BigDecimal amount, String reason) {
        events.enqueue(TopicNames.WITHDRAWAL_CANCELLED, new WithdrawalCancelledEvent(
                UUID.randomUUID(), transactionId, Instant.now(), accountId, amount, reason));
    }
}
