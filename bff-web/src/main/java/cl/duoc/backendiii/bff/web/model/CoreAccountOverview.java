package cl.duoc.backendiii.bff.web.model;

import cl.duoc.backendiii.bff.common.model.AccountMovement;

import java.util.List;

public record CoreAccountOverview(
        AccountSummary account,
        List<AccountMovement> recentMovements,
        List<DailyTransaction> anomalousTransactions
) {
}
