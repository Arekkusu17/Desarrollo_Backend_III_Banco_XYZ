package cl.duoc.backendiii.msaccount.controller;

import cl.duoc.backendiii.msaccount.model.AccountOperation;
import cl.duoc.backendiii.msaccount.model.AccountRequest;
import cl.duoc.backendiii.msaccount.model.ManagedAccount;
import cl.duoc.backendiii.msaccount.service.AccountLedger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cuentas")
public class AccountManagementController {
    private final AccountLedger ledger;

    public AccountManagementController(AccountLedger ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedAccount open(@RequestBody AccountRequest request) {
        return ledger.open(request);
    }

    @PutMapping("/{id}")
    public ManagedAccount maintain(@PathVariable long id, @RequestBody AccountRequest request) {
        return ledger.maintain(id, request);
    }

    @PostMapping("/{id}/cierre")
    public ManagedAccount close(@PathVariable long id) {
        return ledger.close(id);
    }

    @GetMapping("/{id}/gestion")
    public ManagedAccount details(@PathVariable long id) {
        return ledger.details(id);
    }

    @PostMapping("/operaciones")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apply(@RequestBody AccountOperation operation) {
        ledger.apply(operation);
    }
}
