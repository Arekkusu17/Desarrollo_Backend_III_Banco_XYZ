package cl.duoc.backendiii.notification.messaging;

import cl.duoc.backendiii.events.FundsReleasedEvent;
import cl.duoc.backendiii.events.TopicNames;
import cl.duoc.backendiii.events.WithdrawalCancelledEvent;
import cl.duoc.backendiii.events.WithdrawalConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final ProcessedEventStore processedEvents;
    private final String instanceId;

    public NotificationListener(ProcessedEventStore processedEvents,
                                @Value("${notification.instance-id}") String instanceId) {
        this.processedEvents = processedEvents;
        this.instanceId = instanceId;
    }

    @KafkaListener(topics = TopicNames.WITHDRAWAL_CONFIRMED, groupId = "notification-service-group")
    public void onWithdrawalConfirmed(WithdrawalConfirmedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[{}][IDEMPOTENCIA] WithdrawalConfirmed duplicado ignorado eventId={}",
                    instanceId, event.eventId());
            return;
        }
        log.info("[{}][NOTIFICACION] RETIRO CONFIRMADO transactionId={} accountId={} amount={} authorizationCode={}",
                instanceId, event.transactionId(), event.accountId(), event.amount(), event.authorizationCode());
    }

    @KafkaListener(topics = TopicNames.WITHDRAWAL_CANCELLED, groupId = "notification-service-group")
    public void onWithdrawalCancelled(WithdrawalCancelledEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[{}][IDEMPOTENCIA] WithdrawalCancelled duplicado ignorado eventId={}",
                    instanceId, event.eventId());
            return;
        }
        log.info("[{}][NOTIFICACION] RETIRO CANCELADO transactionId={} accountId={} amount={} reason={}",
                instanceId, event.transactionId(), event.accountId(), event.amount(), event.reason());
    }

    @KafkaListener(topics = TopicNames.FUNDS_RELEASED, groupId = "notification-service-audit-group")
    public void onFundsReleased(FundsReleasedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[{}][IDEMPOTENCIA] FundsReleased duplicado ignorado eventId={}",
                    instanceId, event.eventId());
            return;
        }
        log.info("[{}][AUDITORIA] Reserva de fondos liberada transactionId={} accountId={} amount={}",
                instanceId, event.transactionId(), event.accountId(), event.amount());
    }
}
