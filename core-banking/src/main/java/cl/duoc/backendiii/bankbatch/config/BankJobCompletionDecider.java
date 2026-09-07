package cl.duoc.backendiii.bankbatch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// Decides the final route after a Step finishes.
// This makes partial states explicit: clean completion, completion with skips, or review required.
public class BankJobCompletionDecider implements JobExecutionDecider {

    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETED_WITH_SKIPS = "COMPLETED_WITH_SKIPS";
    public static final String REVIEW_REQUIRED = "REVIEW_REQUIRED";
    public static final String FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(BankJobCompletionDecider.class);

    private final int reviewSkipThreshold;

    public BankJobCompletionDecider(@Value("${batch.review-skip-threshold}") int reviewSkipThreshold) {
        this.reviewSkipThreshold = reviewSkipThreshold;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        long totalSkips = stepExecution == null ? 0 : stepExecution.getSkipCount();
        String jobName = jobExecution.getJobInstance().getJobName();
        String stepName = stepExecution == null ? "unknown" : stepExecution.getStepName();

        if (stepExecution != null && stepExecution.getStatus() == BatchStatus.FAILED) {
            log.error("Completion decision: {} failed because {} ended with FAILED status.", jobName, stepName);
            return new FlowExecutionStatus(FAILED);
        }

        if (totalSkips >= reviewSkipThreshold) {
            log.warn("Completion decision: {} requires review. Step {} skipped {} records and threshold is {}.",
                    jobName,
                    stepName,
                    totalSkips,
                    reviewSkipThreshold);
            return new FlowExecutionStatus(REVIEW_REQUIRED);
        }

        if (totalSkips > 0) {
            log.warn("Completion decision: {} completed with controlled skips. Step {} skipped {} records.",
                    jobName,
                    stepName,
                    totalSkips);
            return new FlowExecutionStatus(COMPLETED_WITH_SKIPS);
        }

        log.info("Completion decision: {} completed without skipped records.", jobName);
        return new FlowExecutionStatus(COMPLETED);
    }
}
