package cl.duoc.backendiii.bankbatch.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
// Custom skip policy.
// Spring Batch asks this bean what to do when a fault-tolerant Step finds an exception.
// The goal is to skip controlled errors from isolated records, but stop the Job when the limit is exceeded.
public class BankRecordSkipPolicy implements SkipPolicy {

    private static final Logger log = LoggerFactory.getLogger(BankRecordSkipPolicy.class);

    private final int skipLimit;

    public BankRecordSkipPolicy(@Value("${batch.skip-limit}") int skipLimit) {
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) throws SkipLimitExceededException {
        String errorType = controlledErrorType(throwable);
        boolean controlledError = errorType != null;
        boolean insideLimit = skipCount < skipLimit;

        if (controlledError && insideLimit) {
            log.warn("Record skipped by custom policy. Type: {}. Current skips: {}. Reason: {}",
                    errorType,
                    skipCount,
                    throwable.getMessage());
            return true;
        }

        return false;
    }

    private String controlledErrorType(Throwable throwable) {
        // Controlled errors:
        // - malformed CSV lines,
        // - validations represented as IllegalArgumentException,
        // - recoverable IO errors within the configured limit.
        if (throwable instanceof FlatFileParseException) {
            return "CSV_FORMAT";
        }
        if (throwable instanceof IllegalArgumentException) {
            return "VALIDATION";
        }
        if (throwable instanceof IOException) {
            return "IO";
        }
        return null;
    }
}
