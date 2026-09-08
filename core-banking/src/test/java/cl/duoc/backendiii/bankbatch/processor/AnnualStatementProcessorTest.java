package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualStatementProcessorTest {

    @Test
    void flagsWithdrawalsForAuditReview() {
        AnnualStatementProcessor processor = new AnnualStatementProcessor(
                new TestRejectedRecordWriter(),
                "deposito,retiro,compra,pago",
                "REVISION_EGRESO",
                "OK");

        AnnualStatementEntry result = processor.process(new LegacyAnnualEntry("101", "2024/03/15", "retiro", "-500", "Retiro parcial"));

        assertThat(result).isNotNull();
        assertThat(result.auditFlag()).isEqualTo("REVISION_EGRESO");
    }

    @Test
    void usesConfiguredAuditFlagsAndTransactionTypes() {
        AnnualStatementProcessor processor = new AnnualStatementProcessor(
                new TestRejectedRecordWriter(),
                "ajuste",
                "CONTROL_EGRESO",
                "SIN_OBSERVACION");

        AnnualStatementEntry result = processor.process(new LegacyAnnualEntry("101", "2024/03/15", "ajuste", "-500", "Ajuste manual"));

        assertThat(result).isNotNull();
        assertThat(result.transactionType()).isEqualTo("ajuste");
        assertThat(result.auditFlag()).isEqualTo("CONTROL_EGRESO");
    }

    @Test
    void acceptsDayFirstDateFormatsFromLegacyFiles() {
        AnnualStatementProcessor processor = new AnnualStatementProcessor(
                new TestRejectedRecordWriter(),
                "deposito,retiro,compra,pago",
                "REVISION_EGRESO",
                "OK");

        AnnualStatementEntry dashDate = processor.process(new LegacyAnnualEntry("101", "08-03-2024", "deposito", "3000", "Ingreso"));
        AnnualStatementEntry slashDate = processor.process(new LegacyAnnualEntry("102", "24/03/2024", "deposito", "1000", "Ingreso"));

        assertThat(dashDate).isNotNull();
        assertThat(dashDate.transactionDate()).hasToString("2024-03-08");
        assertThat(slashDate).isNotNull();
        assertThat(slashDate.transactionDate()).hasToString("2024-03-24");
    }
}
