package cl.duoc.backendiii.bff.mobile.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MobileMovementItem(
        LocalDate date,
        String label,
        BigDecimal amount
) {
}

