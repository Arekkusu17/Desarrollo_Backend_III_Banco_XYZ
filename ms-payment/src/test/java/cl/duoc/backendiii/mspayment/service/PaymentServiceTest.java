package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.PaymentOperationResponse;
import cl.duoc.backendiii.mspayment.model.PaymentRequest;
import cl.duoc.backendiii.mspayment.model.PaymentStatus;
import cl.duoc.backendiii.mspayment.model.PaymentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest {

    private final FakePaymentRepository repository = new FakePaymentRepository();
    private final FakeAccountClient accounts = new FakeAccountClient();
    private final PaymentService service = new PaymentService(repository, accounts);

    @Test
    void createsTransferBetweenDifferentAccounts() {
        PaymentOperationResponse response = service.transfer(new PaymentRequest(
                101L,
                102L,
                BigDecimal.valueOf(15000),
                "Transferencia familiar"));

        assertThat(response.type()).isEqualTo(PaymentType.TRANSFER);
        assertThat(response.status()).isEqualTo(PaymentStatus.ACCEPTED);
        assertThat(repository.saved).containsExactly(response);
        assertThat(accounts.executions).isEqualTo(1);
    }

    @Test
    void rejectsTransferToSameAccount() {
        PaymentRequest request = new PaymentRequest(101L, 101L, BigDecimal.valueOf(1000), "Misma cuenta");

        assertThatThrownBy(() -> service.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distintas");
    }

    @Test
    void clampsOperationLimit() {
        service.operations(500);

        assertThat(repository.lastLimit).isEqualTo(100);
    }

    @Test
    void repeatedRequestDoesNotExecuteTwice() {
        UUID id = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(null, 101L, BigDecimal.TEN, "test");
        var first = service.deposit(request, id);
        var repeated = service.deposit(request, id);
        assertThat(repeated).isEqualTo(first);
        assertThat(accounts.executions).isEqualTo(1);
    }

    @Test
    void temporaryFailureLeavesPaymentPendingAndRetryCompletesIt() {
        UUID id = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(null, 101L, BigDecimal.TEN, "test");
        accounts.fail = true;
        assertThatThrownBy(() -> service.deposit(request, id))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.operation(id).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);
        accounts.fail = false;
        assertThat(service.deposit(request, id).status()).isEqualTo(PaymentStatus.ACCEPTED);
    }

    @Test
    void idempotencyKeyCannotBeUsedWithAnotherAmount() {
        UUID id = UUID.randomUUID();
        service.deposit(new PaymentRequest(null, 101L, BigDecimal.TEN, "test"), id);
        assertThatThrownBy(() -> service.deposit(new PaymentRequest(null, 101L, BigDecimal.ONE, "test"), id))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(accounts.executions).isEqualTo(1);
    }

    private static class FakeAccountClient extends AccountClient {
        private int executions;
        private boolean fail;
        FakeAccountClient() { super(null, 3, 0, 5, 3, 10); }
        @Override
        public void execute(PaymentOperationResponse operation) {
            if (fail) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            executions++;
        }
    }

    private static class FakePaymentRepository extends PaymentRepository {
        private final List<PaymentOperationResponse> saved = new ArrayList<>();
        private int lastLimit;

        FakePaymentRepository() {
            super(null);
        }

        @Override
        public PaymentOperationResponse save(PaymentOperationResponse operation) {
            var existing = findById(operation.operationId());
            if (existing.isPresent()) return existing.get();
            saved.add(operation);
            return operation;
        }

        @Override
        public PaymentOperationResponse complete(UUID id) {
            var current = findById(id).orElseThrow();
            var completed = new PaymentOperationResponse(id, current.type(), current.sourceAccountId(),
                    current.targetAccountId(), current.amount(), PaymentStatus.ACCEPTED,
                    current.description(), current.createdAt());
            saved.remove(current);
            saved.add(completed);
            return completed;
        }

        @Override
        public Optional<PaymentOperationResponse> findById(UUID operationId) {
            return saved.stream()
                    .filter(operation -> operation.operationId().equals(operationId))
                    .findFirst();
        }

        @Override
        public List<PaymentOperationResponse> findRecent(int limit) {
            lastLimit = limit;
            return saved;
        }
    }
}
