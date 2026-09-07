package cl.duoc.backendiii.bankbatch.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// This class provides utility methods for parsing and validating legacy transaction data.
final class LegacyParsing {

    private static final List<DateTimeFormatter> ACCEPTED_DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    private LegacyParsing() {
    }

    static LocalDate parseDate(String value) {
        String cleanValue = requireText(value, "fecha vacia");
        for (DateTimeFormatter formatter : ACCEPTED_DATE_FORMATS) {
            try {
                return LocalDate.parse(cleanValue, formatter);
            } catch (RuntimeException ignored) {
                // Try the next known legacy format before rejecting the row.
            }
        }
        throw new IllegalArgumentException("fecha invalida: " + cleanValue);
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
