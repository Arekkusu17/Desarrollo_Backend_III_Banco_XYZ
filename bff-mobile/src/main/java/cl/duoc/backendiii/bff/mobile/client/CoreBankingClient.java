package cl.duoc.backendiii.bff.mobile.client;

import cl.duoc.backendiii.bff.mobile.model.AccountBalance;
import cl.duoc.backendiii.bff.mobile.model.AccountMovement;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class CoreBankingClient {

    private final RestClient restClient;

    public CoreBankingClient(RestClient coreBankingRestClient) {
        this.restClient = coreBankingRestClient;
    }

    public AccountBalance balance(long accountId) {
        return restClient.get()
                .uri("/api/cuentas/{accountId}/saldo", accountId)
                .retrieve()
                .body(AccountBalance.class);
    }

    public List<AccountMovement> latestMovements(long accountId) {
        return restClient.get()
                .uri("/api/cuentas/{accountId}/movimientos?limit=3", accountId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}

