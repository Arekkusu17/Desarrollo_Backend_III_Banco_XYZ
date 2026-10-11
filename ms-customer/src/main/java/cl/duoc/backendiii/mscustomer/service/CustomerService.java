package cl.duoc.backendiii.mscustomer.service;

import cl.duoc.backendiii.mscustomer.model.CustomerProfileRequest;
import cl.duoc.backendiii.mscustomer.model.CustomerProfileResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CustomerProfileResponse create(CustomerProfileRequest request) {
        if (request.customerId() == null || request.customerId() <= 0) {
            throw new IllegalArgumentException("El identificador de cliente es obligatorio");
        }
        return repository.save(toProfile(request.customerId(), request));
    }

    public CustomerProfileResponse update(long customerId, CustomerProfileRequest request) {
        if (customerId <= 0) {
            throw new IllegalArgumentException("El identificador de cliente es obligatorio");
        }
        return repository.save(toProfile(customerId, request));
    }

    public Optional<CustomerProfileResponse> customer(long customerId) {
        return repository.findById(customerId);
    }

    public List<CustomerProfileResponse> customers(Integer limit) {
        return repository.findRecent(normalizeLimit(limit));
    }

    private CustomerProfileResponse toProfile(long customerId, CustomerProfileRequest request) {
        return new CustomerProfileResponse(
                customerId,
                requiredText(request.fullName(), "El nombre del cliente es obligatorio"),
                normalizeOptional(request.email()),
                normalizeOptional(request.phone()),
                defaultText(request.segment(), "GENERAL"),
                defaultText(request.status(), "ACTIVE"),
                LocalDateTime.now());
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
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
