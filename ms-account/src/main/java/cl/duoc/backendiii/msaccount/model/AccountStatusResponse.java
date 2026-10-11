package cl.duoc.backendiii.msaccount.model;

public record AccountStatusResponse(
        long accounts,
        long annualMovements,
        long dailyTransactions
) {
}
