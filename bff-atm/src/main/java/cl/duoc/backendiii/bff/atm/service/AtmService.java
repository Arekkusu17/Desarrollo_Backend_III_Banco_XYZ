package cl.duoc.backendiii.bff.atm.service;

import cl.duoc.backendiii.bff.atm.client.CoreBankingClient;
import cl.duoc.backendiii.bff.atm.model.AccountBalance;
import cl.duoc.backendiii.bff.atm.model.AtmBalanceResponse;
import cl.duoc.backendiii.bff.atm.model.WithdrawalRequest;
import cl.duoc.backendiii.bff.atm.model.WithdrawalResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AtmService {

    private final CoreBankingClient coreBankingClient;
    private final BigDecimal maxWithdrawalAmount;

    public AtmService(CoreBankingClient coreBankingClient,
                      @Value("${atm.max-withdrawal-amount}") BigDecimal maxWithdrawalAmount) {
        this.coreBankingClient = coreBankingClient;
        this.maxWithdrawalAmount = maxWithdrawalAmount;
    }

    public AtmBalanceResponse balance(long accountId) {
        AccountBalance balance = coreBankingClient.balance(accountId);
        return new AtmBalanceResponse("ATM", balance.accountId(), balance.availableBalance(), "CLP");
    }

    public WithdrawalResponse withdraw(long accountId, WithdrawalRequest request) {
        AccountBalance balance = coreBankingClient.balance(accountId);
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return rejected(accountId, request.amount(), "Monto de retiro invalido");
        }
        if (request.pin() == null || request.pin().length() != 4) {
            return rejected(accountId, request.amount(), "PIN invalido");
        }
        if (request.amount().compareTo(maxWithdrawalAmount) > 0) {
            return rejected(accountId, request.amount(), "Monto supera limite permitido para cajero");
        }
        if (request.amount().compareTo(balance.availableBalance()) > 0) {
            return rejected(accountId, request.amount(), "Saldo insuficiente");
        }

        return new WithdrawalResponse(
                "ATM",
                accountId,
                request.amount(),
                true,
                "Retiro simulado aprobado por BFF ATM",
                UUID.randomUUID().toString()
        );
    }

    private WithdrawalResponse rejected(long accountId, BigDecimal amount, String message) {
        return new WithdrawalResponse("ATM", accountId, amount, false, message, null);
    }
}

