package cl.duoc.backendiii.mspayment.controller;

import cl.duoc.backendiii.mspayment.model.PaymentOperationResponse;
import cl.duoc.backendiii.mspayment.model.PaymentRequest;
import cl.duoc.backendiii.mspayment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaymentOperationResponse> operations(@RequestParam(required = false) Integer limit) {
        return service.operations(limit);
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<PaymentOperationResponse> operation(@PathVariable UUID operationId) {
        return ResponseEntity.of(service.operation(operationId));
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentOperationResponse> processPayment(@RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.processPayment(request));
    }

    @PostMapping("/deposits")
    public ResponseEntity<PaymentOperationResponse> deposit(@RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposit(request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<PaymentOperationResponse> transfer(@RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transfer(request));
    }
}
