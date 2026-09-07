package cl.duoc.backendiii.bankbatch.model;

public record CoreStatusResponse(
        long accounts,
        long annualMovements,
        long dailyTransactions,
        long rejectedRecords
) {
}

