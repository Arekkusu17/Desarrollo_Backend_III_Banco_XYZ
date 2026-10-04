package cl.duoc.backendiii.bff.atm.controller;

import cl.duoc.backendiii.bff.atm.model.AtmBalanceResponse;
import cl.duoc.backendiii.bff.atm.model.WithdrawalRequest;
import cl.duoc.backendiii.bff.atm.model.WithdrawalTransactionResponse;
import cl.duoc.backendiii.bff.atm.service.AtmService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/atm")
public class AtmController {

    private final AtmService service;

    public AtmController(AtmService service) {
        this.service = service;
    }

    @GetMapping("/cuentas/{accountId}/saldo")
    public AtmBalanceResponse balance(@PathVariable long accountId) {
        return service.balance(accountId);
    }

    @PostMapping("/cuentas/{accountId}/retiros")
    public ResponseEntity<WithdrawalTransactionResponse> withdraw(@PathVariable long accountId,
                                                                  @RequestBody WithdrawalRequest request) {
        WithdrawalTransactionResponse response = service.withdraw(accountId, request);
        HttpStatus status = response.status().name().equals("PENDING") ? HttpStatus.ACCEPTED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/transacciones/{transactionId}")
    public ResponseEntity<WithdrawalTransactionResponse> transaction(@PathVariable UUID transactionId) {
        return ResponseEntity.of(service.findTransaction(transactionId));
    }

    @GetMapping("/transacciones")
    public List<WithdrawalTransactionResponse> transactions() {
        return service.transactions();
    }

    @PostMapping("/transacciones/{transactionId}/replay-requested")
    public ResponseEntity<WithdrawalTransactionResponse> replayRequested(@PathVariable UUID transactionId) {
        return ResponseEntity.of(service.replayWithdrawalRequested(transactionId));
    }
}
