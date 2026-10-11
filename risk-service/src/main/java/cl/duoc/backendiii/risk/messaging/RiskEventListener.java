package cl.duoc.backendiii.risk.messaging;

import cl.duoc.backendiii.events.FundsReservedEvent;
import cl.duoc.backendiii.events.RiskApprovedEvent;
import cl.duoc.backendiii.events.RiskRejectedEvent;
import cl.duoc.backendiii.events.TopicNames;
import cl.duoc.backendiii.risk.service.RiskDecision;
import cl.duoc.backendiii.risk.service.RiskEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import cl.duoc.backendiii.events.store.JdbcEventStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RiskEventListener {
    private static final Logger log = LoggerFactory.getLogger(RiskEventListener.class);

    private final JdbcEventStore events;
    private final RiskEvaluationService riskEvaluationService;

    public RiskEventListener(JdbcEventStore events,
                             RiskEvaluationService riskEvaluationService) {
        this.events = events;
        this.riskEvaluationService = riskEvaluationService;
    }

    @KafkaListener(topics = TopicNames.FUNDS_RESERVED, groupId = "risk-service-group")
    public void onFundsReserved(FundsReservedEvent event) {
        events.consume(event, () -> evaluate(event));
    }

    private void evaluate(FundsReservedEvent event) {
        log.info("[CONSUMER] FundsReserved recibido transactionId={} amount={}",
                event.transactionId(), event.amount());

        RiskDecision decision = riskEvaluationService.evaluate(event);
        if (decision.approved()) {
            RiskApprovedEvent approved = new RiskApprovedEvent(
                    UUID.randomUUID(),
                    event.transactionId(),
                    Instant.now(),
                    event.accountId(),
                    event.amount(),
                    decision.authorizationCode()
            );
            events.enqueue(TopicNames.RISK_APPROVED, approved);
            log.info("[PRODUCER] RiskApproved publicado transactionId={}", event.transactionId());
            return;
        }

        RiskRejectedEvent rejected = new RiskRejectedEvent(
                UUID.randomUUID(),
                event.transactionId(),
                Instant.now(),
                event.accountId(),
                event.amount(),
                decision.reason()
        );
        events.enqueue(TopicNames.RISK_REJECTED, rejected);
        log.info("[PRODUCER] RiskRejected publicado transactionId={} reason={}",
                event.transactionId(), decision.reason());
    }
}
