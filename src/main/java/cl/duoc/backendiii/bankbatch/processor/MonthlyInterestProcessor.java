package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;
import cl.duoc.backendiii.bankbatch.domain.MonthlyInterestResult;
import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
// This processor is responsible for calculating monthly interest for LegacyInterestAccount objects and transforming them into MonthlyInterestResult objects.
public class MonthlyInterestProcessor implements ItemProcessor<LegacyInterestAccount, MonthlyInterestResult> {

    private static final Map<String, BigDecimal> MONTHLY_RATES = Map.of(
            // Define monthly interest rates for different account types
            // As the original code does not provide these values, we will assume 
            // some example rates for demonstration purposes.
            "ahorro", new BigDecimal("0.0050"),
            "prestamo", new BigDecimal("0.0180")
    );

    private final RejectedRecordWriter rejectedRecordWriter;
    private final Set<Long> processedAccounts = ConcurrentHashMap.newKeySet();

    public MonthlyInterestProcessor(RejectedRecordWriter rejectedRecordWriter) {
        this.rejectedRecordWriter = rejectedRecordWriter;
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
            if (!MONTHLY_RATES.containsKey(accountType)) {
                reject(item.accountId(), "tipo de cuenta no soportado: " + accountType, item);
                return null;
            }
            if (age < 18 || age > 100) {
                reject(item.accountId(), "edad fuera de rango", item);
                return null;
            }
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                reject(item.accountId(), "saldo debe ser mayor que cero", item);
                return null;
            }

            BigDecimal rate = MONTHLY_RATES.get(accountType);
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
}
