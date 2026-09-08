package cl.duoc.backendiii.bff.web.service;

import cl.duoc.backendiii.bff.web.client.CoreBankingClient;
import cl.duoc.backendiii.bff.web.model.CoreAccountOverview;
import cl.duoc.backendiii.bff.web.model.WebDashboardResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebDashboardService {

    private final CoreBankingClient coreBankingClient;

    public WebDashboardService(CoreBankingClient coreBankingClient) {
        this.coreBankingClient = coreBankingClient;
    }

    public WebDashboardResponse dashboard(long accountId) {
        CoreAccountOverview overview = coreBankingClient.accountOverview(accountId);
        return new WebDashboardResponse(
                "WEB",
                overview.account(),
                overview.account().finalBalance(),
                overview.recentMovements(),
                overview.anomalousTransactions(),
                List.of("datos_cliente", "saldo", "intereses", "ultimos_movimientos", "alertas_operativas")
        );
    }
}

