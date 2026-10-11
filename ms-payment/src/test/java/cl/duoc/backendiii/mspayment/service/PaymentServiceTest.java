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
    private final PaymentService service = new PaymentService(repository);

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

    private static class FakePaymentRepository extends PaymentRepository {
        private final List<PaymentOperationResponse> saved = new ArrayList<>();
        private int lastLimit;

        FakePaymentRepository() {
            super(null);
        }

        @Override
        public PaymentOperationResponse save(PaymentOperationResponse operation) {
            saved.add(operation);
            return operation;
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
