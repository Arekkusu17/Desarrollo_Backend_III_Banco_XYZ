package cl.duoc.backendiii.bff.web.model;

import java.math.BigDecimal;
import java.util.List;

public record WebDashboardResponse(
        String channel,
        AccountSummary account,
        BigDecimal availableBalance,
        List<AccountMovement> recentMovements,
        List<DailyTransaction> riskAlerts,
        List<String> visibleSections
) {
}

