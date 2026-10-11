package cl.duoc.backendiii.bankbatch.reader;

import cl.duoc.backendiii.bankbatch.domain.*;
import cl.duoc.backendiii.bankbatch.processor.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LegacyDeterminismTest {
    @Test
    void interestResultsDoNotDependOnWorkerScheduling() throws Exception {
        var rows = new ArrayList<LegacyInterestAccount>();
        var reader = new LegacyInterestAccountCsvReader("input/eft/intereses.csv");
        LegacyInterestAccount row;
        while ((row = reader.read()) != null) rows.add(row);
        var first = processor();
        var expected = rows.stream().map(first::process).filter(Objects::nonNull).toList();
        Collections.reverse(rows);
        var reordered = processor();
        var actual = rows.stream().map(reordered::process).filter(Objects::nonNull).toList();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(expected).isNotEmpty();
    }

    private MonthlyInterestProcessor processor() {
        return new MonthlyInterestProcessor(new RejectedRecordWriter(null, 80, 120, 255) {
            @Override public void reject(RejectedRecord record) { }
        }, "ahorro:0.0050,prestamo:0.0180", 18, 100);
    }
}
