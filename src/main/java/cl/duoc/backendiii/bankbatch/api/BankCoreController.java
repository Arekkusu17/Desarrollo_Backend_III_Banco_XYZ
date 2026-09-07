package cl.duoc.backendiii.bankbatch.api;

import cl.duoc.backendiii.bankbatch.api.dto.AccountBalanceResponse;
import cl.duoc.backendiii.bankbatch.api.dto.AccountMovementResponse;
import cl.duoc.backendiii.bankbatch.api.dto.AccountSummaryResponse;
import cl.duoc.backendiii.bankbatch.api.dto.CoreAccountOverviewResponse;
import cl.duoc.backendiii.bankbatch.api.dto.CoreStatusResponse;
import cl.duoc.backendiii.bankbatch.api.dto.DailyTransactionResponse;
import cl.duoc.backendiii.bankbatch.api.dto.RejectedRecordResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BankCoreController {

    private final BankCoreService service;

    public BankCoreController(BankCoreService service) {
        this.service = service;
    }

    @GetMapping("/estado")
    public CoreStatusResponse status() {
        return service.status();
    }

    @GetMapping("/cuentas")
    public List<AccountSummaryResponse> accounts() {
        return service.accounts();
    }

    @GetMapping("/cuentas/{accountId}")
    public ResponseEntity<AccountSummaryResponse> account(@PathVariable long accountId) {
        return ResponseEntity.of(service.account(accountId));
    }

    @GetMapping("/cuentas/{accountId}/resumen")
    public ResponseEntity<CoreAccountOverviewResponse> overview(@PathVariable long accountId) {
        return ResponseEntity.of(service.overview(accountId));
    }

    @GetMapping("/cuentas/{accountId}/saldo")
    public ResponseEntity<AccountBalanceResponse> balance(@PathVariable long accountId) {
        return ResponseEntity.of(service.balance(accountId));
    }

    @GetMapping("/cuentas/{accountId}/movimientos")
    public List<AccountMovementResponse> movements(@PathVariable long accountId,
                                                   @RequestParam(required = false) Integer limit) {
        return service.movements(accountId, limit);
    }

    @GetMapping("/transacciones")
    public List<DailyTransactionResponse> dailyTransactions(
            @RequestParam(defaultValue = "false") boolean onlyAnomalies,
            @RequestParam(required = false) Integer limit) {
        return service.dailyTransactions(onlyAnomalies, limit);
    }

    @GetMapping("/rechazos")
    public List<RejectedRecordResponse> rejectedRecords(@RequestParam(required = false) Integer limit) {
        return service.rejectedRecords(limit);
    }
}

