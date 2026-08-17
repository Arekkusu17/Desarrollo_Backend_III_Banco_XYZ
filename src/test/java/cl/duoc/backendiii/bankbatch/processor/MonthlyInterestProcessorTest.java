package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;
import cl.duoc.backendiii.bankbatch.domain.MonthlyInterestResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonthlyInterestProcessorTest {

    @Test
    void calculatesSavingsInterest() {
        MonthlyInterestProcessor processor = new MonthlyInterestProcessor(mock(RejectedRecordWriter.class));

        MonthlyInterestResult result = processor.process(new LegacyInterestAccount("101", "John Doe", "5000", "30", "ahorro"));

        assertThat(result).isNotNull();
        assertThat(result.monthlyRate()).isEqualByComparingTo(new BigDecimal("0.0050"));
        assertThat(result.interestAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.finalBalance()).isEqualByComparingTo(new BigDecimal("5025.00"));
    }
}
