package cl.duoc.backendiii.bff.web.service;

import cl.duoc.backendiii.bff.web.client.CoreBankingClient;
import cl.duoc.backendiii.bff.web.model.CoreAccountOverview;
import cl.duoc.backendiii.bff.web.model.WebDashboardResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
public class WebDashboardService {

    private final CoreBankingClient coreBankingClient;

    public WebDashboardService(CoreBankingClient coreBankingClient) {
        this.coreBankingClient = coreBankingClient;
    }

    @CircuitBreaker(name = "coreBanking", fallbackMethod = "dashboardFallback")
    public WebDashboardResponse dashboard(long accountId) {
        CoreAccountOverview overview = coreBankingClient.accountOverview(accountId);
        return new WebDashboardResponse(
                "WEB",
                "CORE_BANKING_DISPONIBLE",
                overview.account(),
                overview.account().finalBalance(),
                overview.recentMovements(),
                overview.anomalousTransactions(),
                List.of("datos_cliente", "saldo", "intereses", "ultimos_movimientos", "alertas_operativas")
        );
    }

    WebDashboardResponse dashboardFallback(long accountId, Throwable ex) {
        return new WebDashboardResponse(
                "WEB",
                "CORE_BANKING_NO_DISPONIBLE_CIRCUIT_BREAKER",
                null,
                BigDecimal.ZERO,
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("mensaje_degradado", "reintentar_mas_tarde")
        );
    }
}
