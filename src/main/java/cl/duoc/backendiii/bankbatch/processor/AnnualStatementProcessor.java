package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;
import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
// This processor is responsible for transforming LegacyAnnualEntry objects into AnnualStatementEntry objects.
public class AnnualStatementProcessor implements ItemProcessor<LegacyAnnualEntry, AnnualStatementEntry> {

    // As the original code does not provide the valid transaction types, we will assume "deposito", 
    // "retiro", "compra", and "pago" as valid types for demonstration purposes.
    private static final Set<String> VALID_TYPES = Set.of("deposito", "retiro", "compra", "pago");

    private final RejectedRecordWriter rejectedRecordWriter;
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    public AnnualStatementProcessor(RejectedRecordWriter rejectedRecordWriter) {
        this.rejectedRecordWriter = rejectedRecordWriter;
    }

    @Override
    // The process method takes a LegacyAnnualEntry item, validates and transforms it into an AnnualStatementEntry.
    public AnnualStatementEntry process(LegacyAnnualEntry item) {
        try {
            long accountId = LegacyParsing.parseLong(item.accountId(), "cuenta_id vacio");
            LocalDate date = LegacyParsing.parseDate(item.date());
            String transactionType = LegacyParsing.requireText(item.transactionType(), "transaccion vacia").toLowerCase();
            BigDecimal amount = LegacyParsing.parseMoney(item.amount(), "monto vacio");
            String description = LegacyParsing.requireText(item.description(), "descripcion vacia");
            String key = accountId + "|" + date + "|" + transactionType + "|" + amount + "|" + description;

            if (!processedKeys.add(key)) {
                reject(item.accountId(), "movimiento anual duplicado", item);
                return null;
            }
            if (!VALID_TYPES.contains(transactionType)) {
                reject(item.accountId(), "tipo de movimiento anual invalido: " + transactionType, item);
                return null;
            }
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                reject(item.accountId(), "monto no puede ser cero", item);
                return null;
            }

            String auditFlag = amount.compareTo(BigDecimal.ZERO) < 0 ? "REVISION_EGRESO" : "OK";
            return new AnnualStatementEntry(accountId, date, transactionType, amount, description, auditFlag);
        } catch (RuntimeException ex) {
            reject(item.accountId(), ex.getMessage(), item);
            return null;
        }
    }

    // This method is used to reject a LegacyAnnualEntry that fails validation.
    private void reject(String key, String reason, LegacyAnnualEntry item) {
        rejectedRecordWriter.reject(new RejectedRecord(
                "annualStatementsJob",
                String.valueOf(key),
                reason,
                item.toString()));
    }
}
