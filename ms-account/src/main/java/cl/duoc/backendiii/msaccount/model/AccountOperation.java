package cl.duoc.backendiii.msaccount.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountOperation(UUID operationId, String type, Long sourceAccountId,
                               Long targetAccountId, BigDecimal amount, String description) {
}
