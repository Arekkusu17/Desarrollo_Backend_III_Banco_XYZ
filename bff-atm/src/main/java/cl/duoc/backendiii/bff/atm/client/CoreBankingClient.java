package cl.duoc.backendiii.bff.atm.client;

import cl.duoc.backendiii.bff.atm.model.AccountBalance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}

