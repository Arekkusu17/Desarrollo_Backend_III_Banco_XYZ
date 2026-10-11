package cl.duoc.backendiii.msaccount.model;

import java.math.BigDecimal;

public record AccountRequest(Long accountId, String customerName, String accountType,
                             Integer age, BigDecimal initialBalance) {
}
