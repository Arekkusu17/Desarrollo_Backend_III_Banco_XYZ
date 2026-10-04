package cl.duoc.backendiii.bff.atm.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class WithdrawalTransaction {
    private final UUID transactionId;
    private final UUID withdrawalRequestedEventId;
    private final long accountId;
    private final BigDecimal amount;
    private final Instant createdAt;
    private WithdrawalStatus status;
    private String message;
    private String authorizationCode;

    public WithdrawalTransaction(UUID transactionId,
                                 UUID withdrawalRequestedEventId,
                                 long accountId,
                                 BigDecimal amount,
                                 Instant createdAt,
                                 WithdrawalStatus status,
                                 String message) {
        this.transactionId = transactionId;
        this.withdrawalRequestedEventId = withdrawalRequestedEventId;
        this.accountId = accountId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = status;
        this.message = message;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getWithdrawalRequestedEventId() {
        return withdrawalRequestedEventId;
    }

    public long getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void confirm(String authorizationCode) {
        this.status = WithdrawalStatus.CONFIRMED;
        this.authorizationCode = authorizationCode;
        this.message = "Retiro confirmado por saga asincrona";
    }

    public void cancel(WithdrawalStatus status, String reason) {
        this.status = status;
        this.message = reason;
    }
}
