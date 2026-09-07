package cl.duoc.backendiii.bankbatch.api.dto;

public record CoreStatusResponse(
        long accounts,
        long annualMovements,
        long dailyTransactions,
        long rejectedRecords
) {
}

