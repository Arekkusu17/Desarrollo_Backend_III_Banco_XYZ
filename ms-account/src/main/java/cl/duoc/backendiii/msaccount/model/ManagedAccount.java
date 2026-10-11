package cl.duoc.backendiii.msaccount.model;

import java.math.BigDecimal;

public record ManagedAccount(long accountId, String customerName, String accountType,
                             int age, BigDecimal balance, String status) {
}
