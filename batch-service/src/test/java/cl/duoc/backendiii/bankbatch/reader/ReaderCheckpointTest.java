package cl.duoc.backendiii.bankbatch.reader;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import static org.assertj.core.api.Assertions.*;

class ReaderCheckpointTest {
    @Test
    void dailyReaderResumesCommittedOffsetAndRepeatsUncommittedItems() throws Exception {
        var reader = new LegacyTransactionCsvReader("input/eft/transacciones.csv", 0, 99);
        var context = new ExecutionContext();
        reader.open(context);
        reader.read(); reader.read();
        reader.update(context);
        var uncommitted = reader.read();
        reader.read();
        var restarted = new LegacyTransactionCsvReader("input/eft/transacciones.csv", 0, 99);
        restarted.open(context);
        assertThat(restarted.read()).isEqualTo(uncommitted);
        assertThat(context.getInt("csv.nextIndex")).isEqualTo(2);
    }

    @Test
    void interestAndAnnualReadersResumeWithinTheirOwnPartition() throws Exception {
        var context = new ExecutionContext();
        var interest = new LegacyInterestAccountCsvReader("input/eft/intereses.csv", 10, 20);
        interest.open(context); interest.read(); interest.update(context);
        var next = interest.read();
        var restarted = new LegacyInterestAccountCsvReader("input/eft/intereses.csv", 10, 20);
        restarted.open(context);
        assertThat(restarted.read()).isEqualTo(next);
        var annual = new LegacyAnnualEntryCsvReader("input/eft/cuentas_anuales.csv", 10, 20);
        context = new ExecutionContext();
        annual.open(context); annual.read(); annual.update(context);
        var annualNext = annual.read();
        var annualRestarted = new LegacyAnnualEntryCsvReader("input/eft/cuentas_anuales.csv", 10, 20);
        annualRestarted.open(context);
        assertThat(annualRestarted.read()).isEqualTo(annualNext);
    }

    @Test
    void rejectsCheckpointOutsidePartition() throws Exception {
        var reader = new LegacyTransactionCsvReader("input/eft/transacciones.csv", 10, 20);
        var context = new ExecutionContext();
        context.putInt("csv.nextIndex", 9);
        assertThatThrownBy(() -> reader.open(context)).isInstanceOf(org.springframework.batch.item.ItemStreamException.class);
    }
}
