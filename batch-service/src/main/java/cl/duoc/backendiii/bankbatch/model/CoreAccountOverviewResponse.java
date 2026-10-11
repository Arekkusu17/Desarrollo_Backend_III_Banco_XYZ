package cl.duoc.backendiii.bankbatch.model;

import java.util.List;

public record CoreAccountOverviewResponse(
        AccountSummaryResponse account,
        List<AccountMovementResponse> recentMovements,
        List<DailyTransactionResponse> anomalousTransactions
) {
}

