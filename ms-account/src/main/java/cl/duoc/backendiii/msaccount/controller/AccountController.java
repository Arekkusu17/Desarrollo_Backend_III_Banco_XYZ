package cl.duoc.backendiii.msaccount.controller;

import cl.duoc.backendiii.msaccount.model.AccountBalanceResponse;
import cl.duoc.backendiii.msaccount.model.AccountMovementResponse;
import cl.duoc.backendiii.msaccount.model.AccountOverviewResponse;
import cl.duoc.backendiii.msaccount.model.AccountStatusResponse;
import cl.duoc.backendiii.msaccount.model.AccountSummaryResponse;
import cl.duoc.backendiii.msaccount.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/estado")
    public AccountStatusResponse status() {
        return service.status();
    }

    @GetMapping
    public List<AccountSummaryResponse> accounts() {
        return service.accounts();
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountSummaryResponse> account(@PathVariable long accountId) {
        return ResponseEntity.of(service.account(accountId));
    }

    @GetMapping("/{accountId}/resumen")
    public ResponseEntity<AccountOverviewResponse> overview(@PathVariable long accountId) {
        return ResponseEntity.of(service.overview(accountId));
    }

    @GetMapping("/{accountId}/saldo")
    public ResponseEntity<AccountBalanceResponse> balance(@PathVariable long accountId) {
        return ResponseEntity.of(service.balance(accountId));
    }

    @GetMapping("/{accountId}/movimientos")
    public List<AccountMovementResponse> movements(@PathVariable long accountId,
                                                   @RequestParam(required = false) Integer limit) {
        return service.movements(accountId, limit);
    }
}
