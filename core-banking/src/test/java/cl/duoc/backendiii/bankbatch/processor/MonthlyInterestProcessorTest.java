package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;
import cl.duoc.backendiii.bankbatch.domain.MonthlyInterestResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInterestProcessorTest {

    @Test
    void calculatesSavingsInterest() {
        MonthlyInterestProcessor processor = new MonthlyInterestProcessor(
                new TestRejectedRecordWriter(),
                "ahorro:0.0050,prestamo:0.0180",
                18,
                100);

        MonthlyInterestResult result = processor.process(new LegacyInterestAccount("101", "John Doe", "5000", "30", "ahorro"));

        assertThat(result).isNotNull();
        assertThat(result.monthlyRate()).isEqualByComparingTo(new BigDecimal("0.0050"));
        assertThat(result.interestAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.finalBalance()).isEqualByComparingTo(new BigDecimal("5025.00"));
    }

    @Test
    void calculatesConfiguredAccountTypeRate() {
        MonthlyInterestProcessor processor = new MonthlyInterestProcessor(
                new TestRejectedRecordWriter(),
                "corriente:0.0025",
                18,
                100);

        MonthlyInterestResult result = processor.process(new LegacyInterestAccount("202", "Jane Doe", "1000", "40", "corriente"));

        assertThat(result).isNotNull();
        assertThat(result.monthlyRate()).isEqualByComparingTo(new BigDecimal("0.0025"));
        assertThat(result.interestAmount()).isEqualByComparingTo(new BigDecimal("2.50"));
    }
}
