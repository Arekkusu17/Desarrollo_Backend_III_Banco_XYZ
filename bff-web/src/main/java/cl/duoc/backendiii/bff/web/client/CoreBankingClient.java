package cl.duoc.backendiii.bff.web.client;

import cl.duoc.backendiii.bff.web.model.CoreAccountOverview;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CoreBankingClient {

    private final RestClient restClient;

    public CoreBankingClient(RestClient coreBankingRestClient) {
        this.restClient = coreBankingRestClient;
    }

    public CoreAccountOverview accountOverview(long accountId) {
        return restClient.get()
                .uri("/api/cuentas/{accountId}/resumen", accountId)
                .retrieve()
                .body(CoreAccountOverview.class);
    }
}

