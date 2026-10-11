package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.events.*;
import cl.duoc.backendiii.events.store.JdbcEventStore;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;

@Component
public class WithdrawalFundsListener {
    private final AccountLedger ledger;
    private final JdbcEventStore events;

    public WithdrawalFundsListener(AccountLedger ledger, JdbcEventStore events) {
        this.ledger = ledger;
        this.events = events;
    }

    @KafkaListener(topics = TopicNames.WITHDRAWAL_REQUESTED, groupId = "ms-account-funds")
    public void requested(WithdrawalRequestedEvent event) {
        ledger.importLegacyAccounts();
        events.consume(event, () -> {
            try {
                var balance = ledger.reserve(event.transactionId(), event.accountId(), event.amount());
                events.enqueue(TopicNames.FUNDS_RESERVED, new FundsReservedEvent(UUID.randomUUID(),
                        event.transactionId(), Instant.now(), event.accountId(), event.amount(), balance));
            } catch (ResponseStatusException ex) {
                events.enqueue(TopicNames.FUNDS_REJECTED, new FundsRejectedEvent(UUID.randomUUID(),
                        event.transactionId(), Instant.now(), event.accountId(), event.amount(), ex.getReason()));
            }
        });
    }

    @KafkaListener(topics = TopicNames.FUNDS_RELEASE_REQUESTED, groupId = "ms-account-funds")
    public void released(FundsReleaseRequestedEvent event) {
        events.consume(event, () -> {
            ledger.release(event.transactionId(), event.accountId(), event.amount());
            events.enqueue(TopicNames.FUNDS_RELEASED, new FundsReleasedEvent(UUID.randomUUID(),
                    event.transactionId(), Instant.now(), event.accountId(), event.amount()));
        });
    }

    @KafkaListener(topics = TopicNames.WITHDRAWAL_CONFIRMED, groupId = "ms-account-funds")
    public void confirmed(WithdrawalConfirmedEvent event) {
        events.consume(event, () -> ledger.confirm(event.transactionId()));
    }
}
