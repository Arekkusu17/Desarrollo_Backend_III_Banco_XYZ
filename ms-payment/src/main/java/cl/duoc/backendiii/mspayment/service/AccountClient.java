package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.PaymentOperationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccountClient {
    private final RestClient client;

    public AccountClient(RestClient accountRestClient) {
        client = accountRestClient;
    }

    @Retry(name = "accounts")
    @CircuitBreaker(name = "accounts", fallbackMethod = "unavailable")
    public void execute(PaymentOperationResponse operation) {
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
