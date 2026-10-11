package cl.duoc.backendiii.bankbatch.messaging;

import cl.duoc.backendiii.bankbatch.model.AccountBalanceResponse;
import cl.duoc.backendiii.bankbatch.service.BankCoreService;
import cl.duoc.backendiii.bankbatch.service.FundReservationStore;
import cl.duoc.backendiii.events.FundsRejectedEvent;
import cl.duoc.backendiii.events.FundsReleaseRequestedEvent;
import cl.duoc.backendiii.events.FundsReleasedEvent;
import cl.duoc.backendiii.events.FundsReservedEvent;
import cl.duoc.backendiii.events.TopicNames;
import cl.duoc.backendiii.events.WithdrawalRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class WithdrawalFundsListener {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalFundsListener.class);

    private final BankCoreService bankCoreService;
    private final FundReservationStore reservationStore;
    private final ProcessedEventStore processedEvents;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WithdrawalFundsListener(BankCoreService bankCoreService,
                                   FundReservationStore reservationStore,
                                   ProcessedEventStore processedEvents,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        this.bankCoreService = bankCoreService;
        this.reservationStore = reservationStore;
        this.processedEvents = processedEvents;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.WITHDRAWAL_REQUESTED, groupId = "core-banking-group")
    public void onWithdrawalRequested(WithdrawalRequestedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[IDEMPOTENCIA] WithdrawalRequested duplicado ignorado eventId={}", event.eventId());
            return;
        }
        log.info("[CONSUMER] WithdrawalRequested recibido transactionId={} accountId={} amount={}",
                event.transactionId(), event.accountId(), event.amount());

        Optional<AccountBalanceResponse> balance = bankCoreService.balance(event.accountId());
        if (balance.isEmpty()) {
            reject(event, "Cuenta no existe");
            return;
        }
        if (event.amount() == null || event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            reject(event, "Monto de retiro invalido");
            return;
        }

        BigDecimal alreadyReserved = reservationStore.reservedAmountForAccount(event.accountId());
        BigDecimal availableAfterPriorReservations = balance.get().availableBalance().subtract(alreadyReserved);
        if (event.amount().compareTo(availableAfterPriorReservations) > 0) {
            reject(event, "Saldo insuficiente");
            return;
        }

        reservationStore.reserve(event.transactionId(), event.accountId(), event.amount());
        FundsReservedEvent reserved = new FundsReservedEvent(
                UUID.randomUUID(),
                event.transactionId(),
                Instant.now(),
                event.accountId(),
                event.amount(),
                availableAfterPriorReservations.subtract(event.amount())
        );
        kafkaTemplate.send(TopicNames.FUNDS_RESERVED, event.transactionId().toString(), reserved);
        log.info("[PRODUCER] FundsReserved publicado transactionId={} availableAfterReserve={}",
                event.transactionId(), reserved.availableBalanceAfterReserve());
    }

    @KafkaListener(topics = TopicNames.FUNDS_RELEASE_REQUESTED, groupId = "core-banking-group")
    public void onFundsReleaseRequested(FundsReleaseRequestedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[IDEMPOTENCIA] FundsReleaseRequested duplicado ignorado eventId={}", event.eventId());
            return;
        }
        reservationStore.release(event.transactionId()).ifPresentOrElse(reservation -> {
            FundsReleasedEvent released = new FundsReleasedEvent(
                    UUID.randomUUID(),
                    event.transactionId(),
                    Instant.now(),
                    reservation.accountId(),
                    reservation.amount()
            );
            kafkaTemplate.send(TopicNames.FUNDS_RELEASED, event.transactionId().toString(), released);
            log.info("[COMPENSACION] FundsReleased publicado transactionId={}", event.transactionId());
        }, () -> log.warn("[COMPENSACION] No habia reserva para liberar transactionId={}", event.transactionId()));
    }

    private void reject(WithdrawalRequestedEvent event, String reason) {
        FundsRejectedEvent rejected = new FundsRejectedEvent(
                UUID.randomUUID(),
                event.transactionId(),
                Instant.now(),
                event.accountId(),
                event.amount(),
                reason
        );
        kafkaTemplate.send(TopicNames.FUNDS_REJECTED, event.transactionId().toString(), rejected);
        log.info("[PRODUCER] FundsRejected publicado transactionId={} reason={}", event.transactionId(), reason);
    }
}
