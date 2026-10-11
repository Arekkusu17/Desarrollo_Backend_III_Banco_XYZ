package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.PaymentOperationResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccountClient {
    private final RestClient client;
    private final CircuitBreaker breaker;
    private final Retry retry;

    public AccountClient(RestClient accountRestClient,
            @Value("${resilience4j.retry.instances.accounts.max-attempts:3}") int attempts,
            @Value("${account.retry.wait-ms:300}") long waitMillis,
            @Value("${resilience4j.circuitbreaker.instances.accounts.sliding-window-size:5}") int window,
            @Value("${resilience4j.circuitbreaker.instances.accounts.minimum-number-of-calls:3}") int minimum,
            @Value("${account.circuitbreaker.open-seconds:10}") long openSeconds) {
        client = accountRestClient;
        breaker = CircuitBreaker.of("accounts", CircuitBreakerConfig.custom()
                .slidingWindowSize(window).minimumNumberOfCalls(minimum)
                .waitDurationInOpenState(Duration.ofSeconds(openSeconds))
                .ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class).build());
        retry = Retry.of("accounts", RetryConfig.custom().maxAttempts(attempts)
                .waitDuration(Duration.ofMillis(waitMillis))
                .retryOnException(ex -> !(ex instanceof org.springframework.web.client.HttpClientErrorException))
                .build());
    }

    public void execute(PaymentOperationResponse operation) {
        try {
            Retry.decorateRunnable(retry,
                    CircuitBreaker.decorateRunnable(breaker, () -> invoke(operation))).run();
        } catch (Exception failure) {
            unavailable(operation, failure);
        }
    }

    private void invoke(PaymentOperationResponse operation) {
        client.post().uri("/api/cuentas/operaciones")
                .body(new BalanceOperation(operation.operationId(), operation.type().name(),
                        operation.sourceAccountId(), operation.targetAccountId(),
                        operation.amount(), operation.description()))
                .retrieve().toBodilessEntity();
    }

    private void unavailable(PaymentOperationResponse operation, Exception failure) {
        if (failure instanceof org.springframework.web.client.HttpClientErrorException clientFailure) {
            throw clientFailure;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Cuenta no disponible; reintentar con Idempotency-Key=" + operation.operationId(), failure);
    }

    record BalanceOperation(java.util.UUID operationId, String type, Long sourceAccountId,
                            Long targetAccountId, java.math.BigDecimal amount, String description) {
    }
}
