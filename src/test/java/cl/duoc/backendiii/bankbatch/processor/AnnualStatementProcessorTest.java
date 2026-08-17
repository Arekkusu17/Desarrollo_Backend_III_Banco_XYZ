package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnnualStatementProcessorTest {

    @Test
    void flagsWithdrawalsForAuditReview() {
        AnnualStatementProcessor processor = new AnnualStatementProcessor(mock(RejectedRecordWriter.class));

        AnnualStatementEntry result = processor.process(new LegacyAnnualEntry("101", "2024/03/15", "retiro", "-500", "Retiro parcial"));

        assertThat(result).isNotNull();
        assertThat(result.auditFlag()).isEqualTo("REVISION_EGRESO");
    }
}
