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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RiskEventListener {
    private static final Logger log = LoggerFactory.getLogger(RiskEventListener.class);

    private final ProcessedEventStore processedEvents;
    private final RiskEvaluationService riskEvaluationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RiskEventListener(ProcessedEventStore processedEvents,
                             RiskEvaluationService riskEvaluationService,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.processedEvents = processedEvents;
        this.riskEvaluationService = riskEvaluationService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.FUNDS_RESERVED, groupId = "risk-service-group")
    public void onFundsReserved(FundsReservedEvent event) {
        if (!processedEvents.isFirstTime(event.eventId())) {
            log.warn("[IDEMPOTENCIA] FundsReserved duplicado ignorado eventId={}", event.eventId());
            return;
        }
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
            kafkaTemplate.send(TopicNames.RISK_APPROVED, event.transactionId().toString(), approved);
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
        kafkaTemplate.send(TopicNames.RISK_REJECTED, event.transactionId().toString(), rejected);
        log.info("[PRODUCER] RiskRejected publicado transactionId={} reason={}",
                event.transactionId(), decision.reason());
    }
}
