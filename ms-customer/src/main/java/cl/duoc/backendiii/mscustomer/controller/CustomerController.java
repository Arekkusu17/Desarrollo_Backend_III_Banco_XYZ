package cl.duoc.backendiii.mscustomer.controller;

import cl.duoc.backendiii.mscustomer.model.CustomerProfileRequest;
import cl.duoc.backendiii.mscustomer.model.CustomerProfileResponse;
import cl.duoc.backendiii.mscustomer.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerProfileResponse> customers(@RequestParam(required = false) Integer limit) {
        return service.customers(limit);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerProfileResponse> customer(@PathVariable long customerId) {
        return ResponseEntity.of(service.customer(customerId));
    }

    @PostMapping
    public ResponseEntity<CustomerProfileResponse> create(@RequestBody CustomerProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerProfileResponse> update(@PathVariable long customerId,
                                                          @RequestBody CustomerProfileRequest request) {
        return ResponseEntity.ok(service.update(customerId, request));
    }
}
