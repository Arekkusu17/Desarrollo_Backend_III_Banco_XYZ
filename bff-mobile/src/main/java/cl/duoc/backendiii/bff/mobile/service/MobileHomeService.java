package cl.duoc.backendiii.bff.mobile.service;

import cl.duoc.backendiii.bff.mobile.client.CoreBankingClient;
import cl.duoc.backendiii.bff.mobile.model.AccountBalance;
import cl.duoc.backendiii.bff.mobile.model.MobileHomeResponse;
import cl.duoc.backendiii.bff.mobile.model.MobileMovementItem;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MobileHomeService {

    private final CoreBankingClient coreBankingClient;

    public MobileHomeService(CoreBankingClient coreBankingClient) {
        this.coreBankingClient = coreBankingClient;
    }

    public MobileHomeResponse home(long accountId) {
        AccountBalance balance = coreBankingClient.balance(accountId);
        List<MobileMovementItem> latestMovements = coreBankingClient.latestMovements(accountId).stream()
                .map(movement -> new MobileMovementItem(
                        movement.transactionDate(),
                        movement.transactionType(),
                        movement.amount()
                ))
                .toList();

        return new MobileHomeResponse(
                "MOBILE",
                balance.accountId(),
                balance.customerName(),
                balance.accountType(),
                balance.availableBalance(),
                latestMovements,
                List.of("consultar_saldo", "ver_movimientos", "transferir")
        );
    }
}

