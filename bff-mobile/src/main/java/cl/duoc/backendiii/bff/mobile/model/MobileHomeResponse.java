package cl.duoc.backendiii.bff.mobile.model;

import java.math.BigDecimal;
import java.util.List;

public record MobileHomeResponse(
        String channel,
        long accountId,
        String customerName,
        String accountType,
        BigDecimal availableBalance,
        List<MobileMovementItem> latestMovements,
        List<String> quickActions
) {
}

