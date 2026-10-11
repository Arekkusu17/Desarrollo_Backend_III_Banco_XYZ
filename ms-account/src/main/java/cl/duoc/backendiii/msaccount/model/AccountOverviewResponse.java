package cl.duoc.backendiii.msaccount.model;

import java.util.List;

public record AccountOverviewResponse(
        AccountSummaryResponse account,
        List<AccountMovementResponse> recentMovements,
        List<DailyTransactionResponse> anomalousTransactions
) {
}
