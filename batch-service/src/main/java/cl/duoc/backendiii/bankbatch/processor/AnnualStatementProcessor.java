package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;
import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@StepScope
// This processor is responsible for transforming LegacyAnnualEntry objects into AnnualStatementEntry objects.
public class AnnualStatementProcessor implements ItemProcessor<LegacyAnnualEntry, AnnualStatementEntry> {

    private final RejectedRecordWriter rejectedRecordWriter;
    private final Set<String> validTypes;
    private final String negativeAmountAuditFlag;
    private final String defaultAuditFlag;
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    public AnnualStatementProcessor(RejectedRecordWriter rejectedRecordWriter,
                                    @Value("${bank.annual.valid-transaction-types}") String validTypes,
                                    @Value("${bank.annual.negative-amount-audit-flag}") String negativeAmountAuditFlag,
                                    @Value("${bank.annual.default-audit-flag}") String defaultAuditFlag) {
        this.rejectedRecordWriter = rejectedRecordWriter;
        this.validTypes = parseCsvSet(validTypes);
        this.negativeAmountAuditFlag = negativeAmountAuditFlag;
        this.defaultAuditFlag = defaultAuditFlag;
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
            if (!validTypes.contains(transactionType)) {
                reject(item.accountId(), "tipo de movimiento anual invalido: " + transactionType, item);
                return null;
            }
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                reject(item.accountId(), "monto no puede ser cero", item);
                return null;
            }

            String auditFlag = amount.compareTo(BigDecimal.ZERO) < 0 ? negativeAmountAuditFlag : defaultAuditFlag;
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

    private Set<String> parseCsvSet(String values) {
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
