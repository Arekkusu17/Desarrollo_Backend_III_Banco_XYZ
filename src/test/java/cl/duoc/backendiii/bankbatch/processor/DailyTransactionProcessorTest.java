package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.DailyTransactionSummary;
import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DailyTransactionProcessorTest {

    @Test
    void marksHighAmountAsAnomaly() {
        DailyTransactionProcessor processor = new DailyTransactionProcessor(mock(RejectedRecordWriter.class));

        DailyTransactionSummary result = processor.process(new LegacyTransaction("1", "2024-01-07", "3000", "debito"));

        assertThat(result).isNotNull();
        assertThat(result.anomaly()).isTrue();
        assertThat(result.anomalyReason()).isEqualTo("monto sobre limite diario");
    }

    @Test
    void rejectsNegativeAmount() {
        RejectedRecordWriter rejectedRecordWriter = mock(RejectedRecordWriter.class);
        DailyTransactionProcessor processor = new DailyTransactionProcessor(rejectedRecordWriter);

        DailyTransactionSummary result = processor.process(new LegacyTransaction("3", "2024-01-03", "-200", "debito"));

        assertThat(result).isNull();
        verify(rejectedRecordWriter).reject(org.mockito.ArgumentMatchers.argThat(rejected ->
                rejected.reason().equals("monto debe ser mayor que cero")));
    }
}
