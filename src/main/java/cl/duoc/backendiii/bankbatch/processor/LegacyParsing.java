package cl.duoc.backendiii.bankbatch.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// This class provides utility methods for parsing and validating legacy transaction data.
final class LegacyParsing {

    // Accepted date formats use year first. Day-first dates are rejected to avoid ambiguity.
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private LegacyParsing() {
    }

    static LocalDate parseDate(String value) {
        String cleanValue = requireText(value, "fecha vacia");
        try {
            return LocalDate.parse(cleanValue, ISO_DATE);
        } catch (DateTimeParseException ignored) {
            return LocalDate.parse(cleanValue, SLASH_DATE);
        }
    }

    static long parseLong(String value, String errorMessage) {
        return Long.parseLong(requireText(value, errorMessage));
    }

    static int parseInt(String value, String errorMessage) {
        return Integer.parseInt(requireText(value, errorMessage));
    }

    static BigDecimal parseMoney(String value, String errorMessage) {
        return new BigDecimal(requireText(value, errorMessage));
    }

    static String requireText(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }
}
