package cl.duoc.backendiii.bankbatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;

class BankJobCompletionDeciderTest {

    @Test
    void completesCleanlyWhenThereAreNoSkips() {
        BankJobCompletionDecider decider = new BankJobCompletionDecider(3);
        JobExecution jobExecution = new JobExecution(new JobInstance(1L, "dailyTransactionsJob"), new JobParameters());
        StepExecution stepExecution = new StepExecution("dailyTransactionsStep", jobExecution);

        assertThat(decider.decide(jobExecution, stepExecution).getName())
                .isEqualTo(BankJobCompletionDecider.COMPLETED);
    }

    @Test
    void completesWithSkipsWhenSkippedRecordsStayBelowReviewThreshold() {
        BankJobCompletionDecider decider = new BankJobCompletionDecider(3);
        JobExecution jobExecution = new JobExecution(new JobInstance(1L, "monthlyInterestJob"), new JobParameters());
        StepExecution stepExecution = new StepExecution("monthlyInterestStep", jobExecution);
        stepExecution.setProcessSkipCount(1);

        assertThat(decider.decide(jobExecution, stepExecution).getName())
                .isEqualTo(BankJobCompletionDecider.COMPLETED_WITH_SKIPS);
    }

    @Test
    void requiresReviewWhenSkippedRecordsReachReviewThreshold() {
        BankJobCompletionDecider decider = new BankJobCompletionDecider(3);
        JobExecution jobExecution = new JobExecution(new JobInstance(1L, "annualStatementsJob"), new JobParameters());
        StepExecution stepExecution = new StepExecution("annualStatementsStep", jobExecution);
        stepExecution.setProcessSkipCount(3);

        assertThat(decider.decide(jobExecution, stepExecution).getName())
                .isEqualTo(BankJobCompletionDecider.REVIEW_REQUIRED);
    }
}
