package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.DailyTransactionSummary;
import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DailyTransactionProcessorTest {

    @Test
    void marksHighAmountAsAnomaly() {
        DailyTransactionProcessor processor = newProcessor(mock(RejectedRecordWriter.class));

        DailyTransactionSummary result = processor.process(new LegacyTransaction("1", "2024-01-07", "3000", "debito"));

        assertThat(result).isNotNull();
        assertThat(result.anomaly()).isTrue();
        assertThat(result.anomalyReason()).isEqualTo("monto sobre limite diario");
    }

    @Test
    void rejectsNegativeAmount() {
        RejectedRecordWriter rejectedRecordWriter = mock(RejectedRecordWriter.class);
        DailyTransactionProcessor processor = newProcessor(rejectedRecordWriter);

        DailyTransactionSummary result = processor.process(new LegacyTransaction("3", "2024-01-03", "-200", "debito"));

        assertThat(result).isNull();
        verify(rejectedRecordWriter).reject(org.mockito.ArgumentMatchers.argThat(rejected ->
                rejected.reason().equals("monto debe ser mayor que cero")));
    }

    @Test
    void acceptsTransactionTypesConfiguredByProperties() {
        DailyTransactionProcessor processor = new DailyTransactionProcessor(
                mock(RejectedRecordWriter.class),
                new BigDecimal("1000"),
                "transferencia");

        DailyTransactionSummary result = processor.process(new LegacyTransaction("10", "2024-01-07", "900", "transferencia"));

        assertThat(result).isNotNull();
        assertThat(result.transactionType()).isEqualTo("transferencia");
    }

    @Test
    void acceptsDayFirstDateFormatsFromLegacyFiles() {
        DailyTransactionProcessor processor = newProcessor(mock(RejectedRecordWriter.class));

        DailyTransactionSummary dashDate = processor.process(new LegacyTransaction("20", "03-04-2024", "900", "credito"));
        DailyTransactionSummary slashDate = processor.process(new LegacyTransaction("21", "20/07/2024", "900", "debito"));

        assertThat(dashDate).isNotNull();
        assertThat(dashDate.transactionDate()).hasToString("2024-04-03");
        assertThat(slashDate).isNotNull();
        assertThat(slashDate.transactionDate()).hasToString("2024-07-20");
    }

    @Test
    void doesNotRejectDifferentIdsWithSameDateAmountAndTypeAsDuplicates() {
        DailyTransactionProcessor processor = newProcessor(mock(RejectedRecordWriter.class));

        DailyTransactionSummary first = processor.process(new LegacyTransaction("30", "2024-01-07", "900", "credito"));
        DailyTransactionSummary second = processor.process(new LegacyTransaction("31", "2024-01-07", "900", "credito"));

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
    }

    private DailyTransactionProcessor newProcessor(RejectedRecordWriter rejectedRecordWriter) {
        return new DailyTransactionProcessor(rejectedRecordWriter, new BigDecimal("2500"), "debito,credito");
    }
}
