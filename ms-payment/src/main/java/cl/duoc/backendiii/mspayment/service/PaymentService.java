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
import java.util.Objects;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PaymentService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PaymentRepository repository;
    private final AccountClient accounts;

    public PaymentService(PaymentRepository repository, AccountClient accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    public PaymentOperationResponse processPayment(PaymentRequest request) {
        return processPayment(request, UUID.randomUUID());
    }

    public PaymentOperationResponse processPayment(PaymentRequest request, UUID id) {
        requireSourceAccount(request);
        requirePositiveAmount(request);
        return create(PaymentType.PAYMENT, request, id);
    }

    public PaymentOperationResponse deposit(PaymentRequest request) {
        return deposit(request, UUID.randomUUID());
    }

    public PaymentOperationResponse deposit(PaymentRequest request, UUID id) {
        requireTargetAccount(request);
        requirePositiveAmount(request);
        return create(PaymentType.DEPOSIT, request, id);
    }

    public PaymentOperationResponse transfer(PaymentRequest request) {
        return transfer(request, UUID.randomUUID());
    }

    public PaymentOperationResponse transfer(PaymentRequest request, UUID id) {
        requireSourceAccount(request);
        requireTargetAccount(request);
        requirePositiveAmount(request);
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new IllegalArgumentException("La cuenta de origen y destino deben ser distintas");
        }
        return create(PaymentType.TRANSFER, request, id);
    }

    public Optional<PaymentOperationResponse> operation(UUID operationId) {
        return repository.findById(operationId);
    }

    public List<PaymentOperationResponse> operations(Integer limit) {
        return repository.findRecent(normalizeLimit(limit));
    }

    private PaymentOperationResponse create(PaymentType type, PaymentRequest request, UUID id) {
        String description = request.description() == null || request.description().isBlank()
                ? type.name()
                : request.description().trim();
        if (description.length() > 255) {
            throw new IllegalArgumentException("La descripcion supera 255 caracteres");
        }
        PaymentOperationResponse operation = repository.save(new PaymentOperationResponse(
                id,
                type,
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount(),
                PaymentStatus.PENDING,
                description,
                LocalDateTime.now()));
        if (operation.type() != type || !Objects.equals(operation.sourceAccountId(), request.sourceAccountId())
                || !Objects.equals(operation.targetAccountId(), request.targetAccountId())
                || operation.amount().compareTo(request.amount()) != 0 || !operation.description().equals(description)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key usado con otro pago");
        }
        if (operation.status() == PaymentStatus.ACCEPTED) return operation;
        if (operation.status() == PaymentStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operacion rechazada; usar un nuevo identificador");
        }
        try {
            accounts.execute(operation);
        } catch (HttpClientErrorException ex) {
            repository.reject(id);
            throw new ResponseStatusException(ex.getStatusCode(), "Operacion rechazada por cuentas", ex);
        }
        return repository.complete(id);
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
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0
                || request.amount().stripTrailingZeros().scale() > 2
                || request.amount().precision() - request.amount().scale() > 16) {
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
