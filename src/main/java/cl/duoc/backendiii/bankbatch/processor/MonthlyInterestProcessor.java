package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;
import cl.duoc.backendiii.bankbatch.domain.MonthlyInterestResult;
import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@StepScope
// This processor is responsible for calculating monthly interest for LegacyInterestAccount objects and transforming them into MonthlyInterestResult objects.
public class MonthlyInterestProcessor implements ItemProcessor<LegacyInterestAccount, MonthlyInterestResult> {

    private final RejectedRecordWriter rejectedRecordWriter;
    private final Map<String, BigDecimal> monthlyRates;
    private final int minAge;
    private final int maxAge;
    private final Set<Long> processedAccounts = ConcurrentHashMap.newKeySet();

    public MonthlyInterestProcessor(RejectedRecordWriter rejectedRecordWriter,
                                    @Value("${bank.interest.monthly-rates}") String monthlyRates,
                                    @Value("${bank.interest.min-age}") int minAge,
                                    @Value("${bank.interest.max-age}") int maxAge) {
        this.rejectedRecordWriter = rejectedRecordWriter;
        this.monthlyRates = parseRateMap(monthlyRates);
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    @Override
    // The process method takes a LegacyInterestAccount item, validates it, calculates the monthly interest, and transforms it into a MonthlyInterestResult.
    public MonthlyInterestResult process(LegacyInterestAccount item) {
        try {
            long accountId = LegacyParsing.parseLong(item.accountId(), "cuenta_id vacio");
            String customerName = LegacyParsing.requireText(item.customerName(), "nombre vacio");
            BigDecimal balance = LegacyParsing.parseMoney(item.balance(), "saldo vacio");
            int age = LegacyParsing.parseInt(item.age(), "edad vacia");
            String accountType = LegacyParsing.requireText(item.accountType(), "tipo vacio").toLowerCase();

            if (!processedAccounts.add(accountId)) {
                reject(item.accountId(), "cuenta duplicada", item);
                return null;
            }
            if (!monthlyRates.containsKey(accountType)) {
                reject(item.accountId(), "tipo de cuenta no soportado: " + accountType, item);
                return null;
            }
            if (age < minAge || age > maxAge) {
                reject(item.accountId(), "edad fuera de rango", item);
                return null;
            }
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                reject(item.accountId(), "saldo debe ser mayor que cero", item);
                return null;
            }

            BigDecimal rate = monthlyRates.get(accountType);
            BigDecimal interest = balance.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal finalBalance = accountType.equals("prestamo")
                    ? balance.add(interest).setScale(2, RoundingMode.HALF_UP)
                    : balance.add(interest).setScale(2, RoundingMode.HALF_UP);

            return new MonthlyInterestResult(accountId, customerName, accountType, age, balance, rate, interest, finalBalance);
        } catch (RuntimeException ex) {
            reject(item.accountId(), ex.getMessage(), item);
            return null;
        }
    }

    private void reject(String key, String reason, LegacyInterestAccount item) {
        rejectedRecordWriter.reject(new RejectedRecord(
                "monthlyInterestJob",
                String.valueOf(key),
                reason,
                item.toString()));
    }

    private Map<String, BigDecimal> parseRateMap(String values) {
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.split(":", 2))
                .collect(Collectors.toUnmodifiableMap(
                        parts -> parts[0].trim().toLowerCase(),
                        parts -> new BigDecimal(parts[1].trim())));
    }
}
