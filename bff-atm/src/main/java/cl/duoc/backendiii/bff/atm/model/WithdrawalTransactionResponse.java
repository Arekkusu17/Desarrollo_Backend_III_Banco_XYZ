package cl.duoc.backendiii.bff.atm.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalTransactionResponse(
        String channel,
        UUID transactionId,
        long accountId,
        BigDecimal requestedAmount,
        WithdrawalStatus status,
        String message,
        String authorizationCode,
        Instant createdAt
) {
    public static WithdrawalTransactionResponse from(WithdrawalTransaction transaction) {
        return new WithdrawalTransactionResponse(
                "ATM",
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getMessage(),
                transaction.getAuthorizationCode(),
                transaction.getCreatedAt()
        );
    }
}
