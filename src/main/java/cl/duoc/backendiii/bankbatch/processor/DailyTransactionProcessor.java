package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.DailyTransactionSummary;
import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;
import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
// This processor is responsible for transforming LegacyTransaction objects into DailyTransactionSummary objects.
public class DailyTransactionProcessor implements ItemProcessor<LegacyTransaction, DailyTransactionSummary> {

    private static final BigDecimal ANOMALY_LIMIT = new BigDecimal("2500");

    // As the original code does not provide the valid transaction types, we will assume "debito" and "credito" as 
    // valid types for demonstration purposes.
    private static final Set<String> VALID_TYPES = Set.of("debito", "credito");

    private final RejectedRecordWriter rejectedRecordWriter;
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    public DailyTransactionProcessor(RejectedRecordWriter rejectedRecordWriter) {
        this.rejectedRecordWriter = rejectedRecordWriter;
    }

    @Override
    // The process method takes a LegacyTransaction item, validates it, and transforms it into a DailyTransactionSummary object.
    public DailyTransactionSummary process(LegacyTransaction item) {
        try {
            long id = LegacyParsing.parseLong(item.id(), "id vacio");
            LocalDate date = LegacyParsing.parseDate(item.date());
            BigDecimal amount = LegacyParsing.parseMoney(item.amount(), "monto vacio");
            String type = LegacyParsing.requireText(item.type(), "tipo vacio").toLowerCase();
            String key = date + "|" + amount + "|" + type;

            if (!processedKeys.add(key)) {
                reject(item.id(), "transaccion diaria duplicada", item);
                return null;
            }
            if (!VALID_TYPES.contains(type)) {
                reject(item.id(), "tipo de transaccion invalido: " + type, item);
                return null;
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                reject(item.id(), "monto debe ser mayor que cero", item);
                return null;
            }

            boolean anomaly = amount.compareTo(ANOMALY_LIMIT) > 0;
            String reason = anomaly ? "monto sobre limite diario" : "";
            return new DailyTransactionSummary(id, date, amount, type, anomaly, reason);
        } catch (RuntimeException ex) {
            reject(item.id(), ex.getMessage(), item);
            return null;
        }
    }

    private void reject(String key, String reason, LegacyTransaction item) {
        rejectedRecordWriter.reject(new RejectedRecord(
                "dailyTransactionsJob",
                String.valueOf(key),
                reason,
                item.toString()));
    }
}
