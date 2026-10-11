package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AccountClientTest {
    private PaymentOperationResponse operation() {
        return new PaymentOperationResponse(UUID.randomUUID(), PaymentType.PAYMENT, 101L, null,
                BigDecimal.ONE, PaymentStatus.PENDING, "test", LocalDateTime.now());
    }

    @Test
    void retriesDiscoveryFailureAndOpensCircuitWithoutRejectingPayment() {
        AtomicInteger calls = new AtomicInteger();
        RestClient rest = RestClient.builder().baseUrl("http://accounts")
                .requestInterceptor((request, body, execution) -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("Service Instance cannot be null");
                }).build();
        AccountClient client = new AccountClient(rest, 3, 0, 5, 3, 10);
        assertThatThrownBy(() -> client.execute(operation())).isInstanceOfSatisfying(
                ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(calls).hasValue(3);
        assertThatThrownBy(() -> client.execute(operation())).isInstanceOf(ResponseStatusException.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void definitiveClientErrorIsNotRetriedOrConvertedToUnavailable() {
        AtomicInteger calls = new AtomicInteger();
        RestClient rest = RestClient.builder().baseUrl("http://accounts")
                .requestInterceptor((request, body, execution) -> {
                    calls.incrementAndGet();
                    throw new HttpClientErrorException(HttpStatus.CONFLICT);
                }).build();
        AccountClient client = new AccountClient(rest, 3, 0, 5, 3, 10);
        assertThatThrownBy(() -> client.execute(operation())).isInstanceOf(HttpClientErrorException.class);
        assertThat(calls).hasValue(1);
    }
}
