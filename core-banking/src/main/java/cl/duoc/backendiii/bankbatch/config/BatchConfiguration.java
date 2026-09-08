package cl.duoc.backendiii.bankbatch.config;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import cl.duoc.backendiii.bankbatch.domain.DailyTransactionSummary;
import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;
import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;
import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;
import cl.duoc.backendiii.bankbatch.domain.MonthlyInterestResult;
import cl.duoc.backendiii.bankbatch.listener.BankSkipListener;
import cl.duoc.backendiii.bankbatch.partition.AnnualStatementPartitioner;
import cl.duoc.backendiii.bankbatch.policy.BankRecordSkipPolicy;
import cl.duoc.backendiii.bankbatch.processor.AnnualStatementProcessor;
import cl.duoc.backendiii.bankbatch.processor.DailyTransactionProcessor;
import cl.duoc.backendiii.bankbatch.processor.MonthlyInterestProcessor;
import cl.duoc.backendiii.bankbatch.reader.LegacyAnnualEntryCsvReader;
import cl.duoc.backendiii.bankbatch.reader.LegacyInterestAccountCsvReader;
import cl.duoc.backendiii.bankbatch.reader.LegacyTransactionCsvReader;
import cl.duoc.backendiii.bankbatch.writer.AnnualStatementReportWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;

// This class contains the configuration for the batch jobs, steps, readers, processors, and writers. 
// It defines beans for reading legacy data from CSV files, processing it, and writing the results to a database. 
// The configuration is annotated with @Configuration to indicate that it is a source of bean definitions for the application context.
@Configuration
public class BatchConfiguration {

    @Bean
    // The @StepScope annotation indicates that the bean is scoped to the lifecycle of a step execution.
    // This reader loads the daily transactions file for the configured week.
    // It follows the professor's Week 2 approach: a simple in-memory ItemReader with synchronized read().
    // Since it does not implement ItemStream, Spring Batch does not store restart offset metadata for this parallel Step.
    @StepScope
    public LegacyTransactionCsvReader transactionReader(@Value("${legacy.data.week}") String dataWeek) throws IOException {
        return new LegacyTransactionCsvReader("input/" + dataWeek + "/transacciones.csv");
    }

    @Bean
    // This reader loads the accounts used to calculate monthly interest.
    // It keeps CSV input configurable by week and inherits synchronized read() for parallel execution.
    @StepScope
    public LegacyInterestAccountCsvReader interestReader(@Value("${legacy.data.week}") String dataWeek) throws IOException {
        return new LegacyInterestAccountCsvReader("input/" + dataWeek + "/intereses.csv");
    }

    @Bean
    // This reader loads only the annual movement range assigned to the current partition.
    // start/end are zero-based data-row indexes provided through the partition ExecutionContext.
    @StepScope
    public LegacyAnnualEntryCsvReader annualReader(@Value("${legacy.data.week}") String dataWeek,
                                                   @Value("#{stepExecutionContext['start']}") Integer start,
                                                   @Value("#{stepExecutionContext['end']}") Integer end) throws IOException {
        return new LegacyAnnualEntryCsvReader(
                "input/" + dataWeek + "/cuentas_anuales.csv",
                start == null ? 0 : start,
                end);
    }

    @Bean
    // Scaling bean.
    // It configures a thread pool to process chunks in parallel; by default it uses 3 threads.
    // The prefix helps show in the console that processing is using BankBatch-* threads.
    public TaskExecutor batchTaskExecutor(@Value("${batch.thread-pool-size}") int threadPoolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(threadPoolSize * 4);
        executor.setThreadNamePrefix("BankBatch-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    // The transactionWriter bean is responsible for writing the processed daily transaction summaries to the database.
    // It uses data already processed by the DailyTransactionProcessor and maps the fields to the corresponding columns in the daily_transaction_summary table.
    public JdbcBatchItemWriter<DailyTransactionSummary> transactionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<DailyTransactionSummary>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO daily_transaction_summary(transaction_id, transaction_date, amount, transaction_type, anomaly, anomaly_reason)
                        VALUES (:transactionId, :transactionDate, :amount, :transactionType, :anomaly, :anomalyReason)
                        -- specifies that if a record with the same transaction_id already exists, it should be updated instead of inserted
                        ON CONFLICT (transaction_id)
                        DO UPDATE SET transaction_date = EXCLUDED.transaction_date,
                                      amount = EXCLUDED.amount,
                                      transaction_type = EXCLUDED.transaction_type,
                                      anomaly = EXCLUDED.anomaly,
                                      anomaly_reason = EXCLUDED.anomaly_reason
                        """)
                .beanMapped() // indicates that the fields in the DailyTransactionSummary object should be mapped to the corresponding columns in the database table
                .build();
    }

    // The interestWriter bean is responsible for writing the processed monthly interest results to the database.
    // It uses data already processed by the MonthlyInterestProcessor and maps the fields to the corresponding columns in the monthly_interest_results table.
    @Bean
    public JdbcBatchItemWriter<MonthlyInterestResult> interestWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<MonthlyInterestResult>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO monthly_interest_results(account_id, customer_name, account_type, age, initial_balance,
                                                             monthly_rate, interest_amount, final_balance)
                        VALUES (:accountId, :customerName, :accountType, :age, :initialBalance,
                                :monthlyRate, :interestAmount, :finalBalance)
                        -- specifies that if a record with the same account_id already exists, it should be updated instead of inserted
                        ON CONFLICT (account_id) -- specifies that if a record with the same account_id already exists, it should be updated instead of inserted
                        DO UPDATE SET customer_name = EXCLUDED.customer_name,
                                      account_type = EXCLUDED.account_type,
                                      age = EXCLUDED.age,
                                      initial_balance = EXCLUDED.initial_balance,
                                      monthly_rate = EXCLUDED.monthly_rate,
                                      interest_amount = EXCLUDED.interest_amount,
                                      final_balance = EXCLUDED.final_balance
                        """)
                .beanMapped() // indicates that the fields in the MonthlyInterestResult object should be mapped to the corresponding columns in the database table
                .build();
    }

    @Bean
    // The dailyTransactionsStep bean defines a step in the batch job that processes daily transactions. 
    // It reads legacy transaction data from a CSV file, processes it using the DailyTransactionProcessor, 
    // and writes the results to the database using the transactionWriter.
    // Flow: transacciones.csv -> transactionReader -> DailyTransactionProcessor
    //       -> transactionWriter -> daily_transaction_summary.
    // Uses chunks of size 5, fault tolerance, retry for transient database failures,
    // and parallel execution through batchTaskExecutor.
    public Step dailyTransactionsStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      ItemReader<LegacyTransaction> transactionReader,
                                      DailyTransactionProcessor processor,
                                      JdbcBatchItemWriter<DailyTransactionSummary> transactionWriter,
                                      BankRecordSkipPolicy bankRecordSkipPolicy,
                                      BankSkipListener bankSkipListener,
                                      TaskExecutor batchTaskExecutor,
                                      @Value("${batch.chunk-size}") int chunkSize,
                                      @Value("${batch.retry-limit}") int retryLimit) {
        return new StepBuilder("dailyTransactionsStep", jobRepository)
                .<LegacyTransaction, DailyTransactionSummary>chunk(chunkSize, transactionManager)
                .reader(transactionReader)
                .processor(processor)
                .writer(transactionWriter)
                // Enables Spring Batch's formal error handling.
                .faultTolerant()
                // Retries temporary data access failures before marking the chunk as failed.
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                // Custom policy that decides which controlled errors can be skipped.
                .skipPolicy(bankRecordSkipPolicy)
                // Listener that stores read, process, and write skips in rejected_records.
                .listener(bankSkipListener)
                // Scaling: allows chunks to be processed in parallel with the configured pool.
                .taskExecutor(batchTaskExecutor)
                .build();
    }

    @Bean
    // The monthlyInterestStep bean defines a step in the batch job that processes monthly interest calculations.
    // It reads legacy interest account data from a CSV file, processes it using the MonthlyInterestProcessor, 
    // and writes the results to the database using the interestWriter.
    // Flow: intereses.csv -> interestReader -> MonthlyInterestProcessor
    //       -> interestWriter -> monthly_interest_results.
    // Uses the same fault-tolerance and scaling policy used by the other Steps.
    public Step monthlyInterestStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    ItemReader<LegacyInterestAccount> interestReader,
                                    MonthlyInterestProcessor processor,
                                    JdbcBatchItemWriter<MonthlyInterestResult> interestWriter,
                                    BankRecordSkipPolicy bankRecordSkipPolicy,
                                    BankSkipListener bankSkipListener,
                                    TaskExecutor batchTaskExecutor,
                                    @Value("${batch.chunk-size}") int chunkSize,
                                    @Value("${batch.retry-limit}") int retryLimit) {
        return new StepBuilder("monthlyInterestStep", jobRepository)
                .<LegacyInterestAccount, MonthlyInterestResult>chunk(chunkSize, transactionManager)
                .reader(interestReader)
                .processor(processor)
                .writer(interestWriter)
                // Allows the Job to continue when controlled errors affect isolated records.
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                // Limits and classifies the errors that can be skipped.
                .skipPolicy(bankRecordSkipPolicy)
                // Centralizes skip traceability in the rejected_records table.
                .listener(bankSkipListener)
                // Runs interest chunks using the configured 3-thread pool.
                .taskExecutor(batchTaskExecutor)
                .build();
    }

    @Bean
    // The annualStatementsWorkerStep bean processes one partition of the annual statements file.
    // It reads legacy annual entry data from a CSV file, processes it using the AnnualStatementProcessor, 
    // and writes the results to a report using the AnnualStatementReportWriter.
    // Flow: cuentas_anuales.csv -> annualReader -> AnnualStatementProcessor
    //       -> AnnualStatementReportWriter -> annual_statement_entries + annual_statement_report.csv.
    // Parallel execution is controlled by the partition handler, so the worker Step itself stays single-threaded.
    public Step annualStatementsWorkerStep(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           ItemReader<LegacyAnnualEntry> annualReader,
                                           AnnualStatementProcessor processor,
                                           AnnualStatementReportWriter annualStatementReportWriter,
                                           BankRecordSkipPolicy bankRecordSkipPolicy,
                                           BankSkipListener bankSkipListener,
                                           @Value("${batch.chunk-size}") int chunkSize,
                                           @Value("${batch.retry-limit}") int retryLimit) {
        return new StepBuilder("annualStatementsWorkerStep", jobRepository)
                .<LegacyAnnualEntry, AnnualStatementEntry>chunk(chunkSize, transactionManager)
                .reader(annualReader)
                .processor(processor)
                .writer(annualStatementReportWriter)
                // Enables skip/retry behavior at the Spring Batch level for the annual process.
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                // Defines which controlled errors can be skipped without stopping the whole Job.
                .skipPolicy(bankRecordSkipPolicy)
                // Registers batch skips for later audit.
                .listener(bankSkipListener)
                .build();
    }

    @Bean
    public TaskExecutorPartitionHandler annualStatementsPartitionHandler(Step annualStatementsWorkerStep,
                                                                         TaskExecutor batchTaskExecutor,
                                                                         @Value("${batch.partition-grid-size}") int gridSize) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(annualStatementsWorkerStep);
        handler.setTaskExecutor(batchTaskExecutor);
        handler.setGridSize(gridSize);
        return handler;
    }

    @Bean
    // Manager Step for Semana 3 scaling: splits the annual file into independent ranges.
    public Step annualStatementsPartitionStep(JobRepository jobRepository,
                                              AnnualStatementPartitioner annualStatementPartitioner,
                                              TaskExecutorPartitionHandler annualStatementsPartitionHandler) {
        return new StepBuilder("annualStatementsPartitionStep", jobRepository)
                .partitioner("annualStatementsWorkerStep", annualStatementPartitioner)
                .partitionHandler(annualStatementsPartitionHandler)
                .build();
    }

    @Bean
    // The dailyTransactionsJob bean defines a batch job that consists of the dailyTransactionsStep.
    // It orchestrates the execution of the step and manages the job lifecycle.
    // Job flow: dailyTransactionsJob -> dailyTransactionsStep.
    public Job dailyTransactionsJob(JobRepository jobRepository,
                                    Step dailyTransactionsStep,
                                    JobExecutionSummaryListener jobExecutionSummaryListener,
                                    BankJobCompletionDecider bankJobCompletionDecider) {
        return new JobBuilder("dailyTransactionsJob", jobRepository)
                .listener(jobExecutionSummaryListener)
                .start(dailyTransactionsStep)
                .next(bankJobCompletionDecider)
                .on(BankJobCompletionDecider.REVIEW_REQUIRED).end(BankJobCompletionDecider.REVIEW_REQUIRED)
                .from(bankJobCompletionDecider).on(BankJobCompletionDecider.COMPLETED_WITH_SKIPS).end(BankJobCompletionDecider.COMPLETED_WITH_SKIPS)
                .from(bankJobCompletionDecider).on(BankJobCompletionDecider.FAILED).fail()
                .from(bankJobCompletionDecider).on("*").end()
                .build()
                .build();
    }

    @Bean
    // The monthlyInterestJob bean defines a batch job that consists of the monthlyInterestStep.
    // It orchestrates the execution of the step and manages the job lifecycle.
    // Job flow: monthlyInterestJob -> monthlyInterestStep.
    public Job monthlyInterestJob(JobRepository jobRepository,
                                  Step monthlyInterestStep,
                                  JobExecutionSummaryListener jobExecutionSummaryListener,
                                  BankJobCompletionDecider bankJobCompletionDecider) {
        return new JobBuilder("monthlyInterestJob", jobRepository)
                .listener(jobExecutionSummaryListener)
                .start(monthlyInterestStep)
                .next(bankJobCompletionDecider)
                .on(BankJobCompletionDecider.REVIEW_REQUIRED).end(BankJobCompletionDecider.REVIEW_REQUIRED)
                .from(bankJobCompletionDecider).on(BankJobCompletionDecider.COMPLETED_WITH_SKIPS).end(BankJobCompletionDecider.COMPLETED_WITH_SKIPS)
                .from(bankJobCompletionDecider).on(BankJobCompletionDecider.FAILED).fail()
                .from(bankJobCompletionDecider).on("*").end()
                .build()
                .build();
    }

    @Bean
    // The annualStatementsJob bean defines a partitioned batch job for the annual high-volume process.
    // It orchestrates the execution of the step and manages the job lifecycle.
    // Job flow: annualStatementsJob -> annualStatementsPartitionStep -> annualStatementsWorkerStep partitions.
    public Job annualStatementsJob(JobRepository jobRepository,
                                   Step annualStatementsPartitionStep,
                                   JobExecutionSummaryListener jobExecutionSummaryListener,
                                   BankJobCompletionDecider bankJobCompletionDecider) {
        return new JobBuilder("annualStatementsJob", jobRepository)
                .listener(jobExecutionSummaryListener)
                .start(annualStatementsPartitionStep)
                .next(bankJobCompletionDecider)
                .on(BankJobCompletionDecider.REVIEW_REQUIRED).end(BankJobCompletionDecider.REVIEW_REQUIRED)
                .from(bankJobCompletionDecider).on(BankJobCompletionDecider.COMPLETED_WITH_SKIPS).end(BankJobCompletionDecider.COMPLETED_WITH_SKIPS)
                .from(bankJobCompletionDecider).on(BankJobCompletionDecider.FAILED).fail()
                .from(bankJobCompletionDecider).on("*").end()
                .build()
                .build();
    }
}
