package cl.duoc.backendiii.bff.atm.controller;

import cl.duoc.backendiii.bff.atm.model.AtmBalanceResponse;
import cl.duoc.backendiii.bff.atm.model.WithdrawalRequest;
import cl.duoc.backendiii.bff.atm.model.WithdrawalResponse;
import cl.duoc.backendiii.bff.atm.service.AtmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public WithdrawalResponse withdraw(@PathVariable long accountId,
                                       @RequestBody WithdrawalRequest request) {
        return service.withdraw(accountId, request);
    }
}

