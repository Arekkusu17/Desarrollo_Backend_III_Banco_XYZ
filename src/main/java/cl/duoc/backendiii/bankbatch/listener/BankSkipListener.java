package cl.duoc.backendiii.bankbatch.listener;

import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import cl.duoc.backendiii.bankbatch.processor.RejectedRecordWriter;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

@Component
@StepScope
// Skip listener for fault-tolerant Steps.
// It complements the business rejections already registered by the processors.
// This listener covers errors skipped by Spring Batch during read, process, or write stages.
public class BankSkipListener implements SkipListener<Object, Object> {

    private static final String NO_ITEM = "SIN_REGISTRO";

    private final RejectedRecordWriter rejectedRecordWriter;
    private String processName = "batchInfrastructure";

    public BankSkipListener(RejectedRecordWriter rejectedRecordWriter) {
        this.rejectedRecordWriter = rejectedRecordWriter;
    }

    @BeforeStep
    // Before each Step runs, the current Job name is stored for traceability.
    public void beforeStep(StepExecution stepExecution) {
        processName = stepExecution.getJobExecution().getJobInstance().getJobName();
    }

    @Override
    // Registers errors skipped during CSV reading, when no domain item exists yet.
    public void onSkipInRead(Throwable throwable) {
        reject("READ", NO_ITEM, throwable.getMessage(), throwable.toString());
    }

    @Override
    // Registers errors skipped while processing an item.
    public void onSkipInProcess(Object item, Throwable throwable) {
        reject("PROCESS", keyFrom(item), throwable.getMessage(), String.valueOf(item));
    }

    @Override
    // Registers errors skipped while writing an item or chunk.
    public void onSkipInWrite(Object item, Throwable throwable) {
        reject("WRITE", keyFrom(item), throwable.getMessage(), String.valueOf(item));
    }

    // Reuses the rejected_records table so all audit data stays centralized.
    private void reject(String stage, String key, String reason, String payload) {
        rejectedRecordWriter.reject(new RejectedRecord(
                processName,
                stage + ":" + key,
                sanitize(reason),
                sanitize(payload)));
    }

    private String keyFrom(Object item) {
        return item == null ? NO_ITEM : Integer.toHexString(item.hashCode());
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').trim();
    }
}
