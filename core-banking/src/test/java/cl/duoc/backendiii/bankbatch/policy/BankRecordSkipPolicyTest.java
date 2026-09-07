package cl.duoc.backendiii.bankbatch.policy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.file.FlatFileParseException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class BankRecordSkipPolicyTest {

    @Test
    void skipsControlledCsvValidationAndIoErrorsInsideLimit() throws Exception {
        BankRecordSkipPolicy policy = new BankRecordSkipPolicy(3);

        assertThat(policy.shouldSkip(new FlatFileParseException("csv invalido", "linea", 1), 0))
                .isTrue();
        assertThat(policy.shouldSkip(new IllegalArgumentException("registro invalido"), 1))
                .isTrue();
        assertThat(policy.shouldSkip(new IOException("lectura interrumpida"), 2))
                .isTrue();
    }

    @Test
    void doesNotSkipControlledErrorsWhenLimitIsReached() throws Exception {
        BankRecordSkipPolicy policy = new BankRecordSkipPolicy(3);

        assertThat(policy.shouldSkip(new IllegalArgumentException("registro invalido"), 3))
                .isFalse();
    }

    @Test
    void doesNotSkipUnexpectedErrors() throws Exception {
        BankRecordSkipPolicy policy = new BankRecordSkipPolicy(3);

        assertThat(policy.shouldSkip(new NullPointerException("fallo no controlado"), 0))
                .isFalse();
    }
}
