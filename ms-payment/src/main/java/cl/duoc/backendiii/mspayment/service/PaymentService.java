package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.PaymentOperationResponse;
import cl.duoc.backendiii.mspayment.model.PaymentRequest;
import cl.duoc.backendiii.mspayment.model.PaymentStatus;
import cl.duoc.backendiii.mspayment.model.PaymentType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public PaymentOperationResponse processPayment(PaymentRequest request) {
        requireSourceAccount(request);
        requirePositiveAmount(request);
        return create(PaymentType.PAYMENT, request);
    }

    public PaymentOperationResponse deposit(PaymentRequest request) {
        requireTargetAccount(request);
        requirePositiveAmount(request);
        return create(PaymentType.DEPOSIT, request);
    }

    public PaymentOperationResponse transfer(PaymentRequest request) {
        requireSourceAccount(request);
        requireTargetAccount(request);
        requirePositiveAmount(request);
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new IllegalArgumentException("La cuenta de origen y destino deben ser distintas");
        }
        return create(PaymentType.TRANSFER, request);
    }

    public Optional<PaymentOperationResponse> operation(UUID operationId) {
        return repository.findById(operationId);
    }

    public List<PaymentOperationResponse> operations(Integer limit) {
        return repository.findRecent(normalizeLimit(limit));
    }

    private PaymentOperationResponse create(PaymentType type, PaymentRequest request) {
        String description = request.description() == null || request.description().isBlank()
                ? type.name()
                : request.description().trim();
        return repository.save(new PaymentOperationResponse(
                UUID.randomUUID(),
                type,
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount(),
                PaymentStatus.ACCEPTED,
                description,
                LocalDateTime.now()));
    }

    private void requireSourceAccount(PaymentRequest request) {
        if (request.sourceAccountId() == null || request.sourceAccountId() <= 0) {
            throw new IllegalArgumentException("La cuenta de origen es obligatoria");
        }
    }

    private void requireTargetAccount(PaymentRequest request) {
        if (request.targetAccountId() == null || request.targetAccountId() <= 0) {
            throw new IllegalArgumentException("La cuenta de destino es obligatoria");
        }
    }

    private void requirePositiveAmount(PaymentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
    }

    int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
