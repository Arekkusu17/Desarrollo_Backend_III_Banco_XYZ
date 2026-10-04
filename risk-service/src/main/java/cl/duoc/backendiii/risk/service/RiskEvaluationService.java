package cl.duoc.backendiii.risk.service;

import cl.duoc.backendiii.events.FundsReservedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RiskEvaluationService {
    private final AtomicBoolean forcedFailure = new AtomicBoolean(false);
    private final BigDecimal maxApprovedAmount;

    public RiskEvaluationService(@Value("${risk.max-approved-amount}") BigDecimal maxApprovedAmount) {
        this.maxApprovedAmount = maxApprovedAmount;
    }

    @CircuitBreaker(name = "riskService", fallbackMethod = "fallback")
    public RiskDecision evaluate(FundsReservedEvent event) {
        if (forcedFailure.get()) {
            throw new IllegalStateException("Risk service unavailable");
        }
        if (event.amount().compareTo(maxApprovedAmount) > 0) {
            return RiskDecision.rejected("Monto requiere revision manual de riesgo");
        }
        return RiskDecision.approved("RISK-" + UUID.randomUUID());
    }

    RiskDecision fallback(FundsReservedEvent event, Throwable ex) {
        return RiskDecision.rejected("Risk service unavailable");
    }

    public void setForcedFailure(boolean enabled) {
        forcedFailure.set(enabled);
    }

    public boolean isForcedFailure() {
        return forcedFailure.get();
    }
}
