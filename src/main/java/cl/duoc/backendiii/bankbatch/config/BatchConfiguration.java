package cl.duoc.backendiii.bankbatch.config;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import cl.duoc.backendiii.bankbatch.domain.DailyTransactionSummary;
import cl.duoc.backendiii.bankbatch.domain.LegacyAnnualEntry;
import cl.duoc.backendiii.bankbatch.domain.LegacyInterestAccount;
import cl.duoc.backendiii.bankbatch.domain.LegacyTransaction;
import cl.duoc.backendiii.bankbatch.domain.MonthlyInterestResult;
import cl.duoc.backendiii.bankbatch.processor.AnnualStatementProcessor;
import cl.duoc.backendiii.bankbatch.processor.DailyTransactionProcessor;
import cl.duoc.backendiii.bankbatch.processor.MonthlyInterestProcessor;
import cl.duoc.backendiii.bankbatch.writer.AnnualStatementReportWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

// This class contains the configuration for the batch jobs, steps, readers, processors, and writers. 
// It defines beans for reading legacy data from CSV files, processing it, and writing the results to a database. 
// The configuration is annotated with @Configuration to indicate that it is a source of bean definitions for the application context.
@Configuration
public class BatchConfiguration {

    @Bean
    // The @StepScope annotation indicates that the bean is scoped to the lifecycle of a step execution.
    @StepScope
    public FlatFileItemReader<LegacyTransaction> transactionReader(@Value("${legacy.data.week}") String dataWeek) {
        return new FlatFileItemReaderBuilder<LegacyTransaction>()
                .name("transactionReader")
                .resource(new ClassPathResource("input/" + dataWeek + "/transacciones.csv"))
                .linesToSkip(1) // skip the header line
                .delimited() // indicates that the file is delimited (e.g., CSV)
                .delimiter(",") // specifies the delimiter used in the file
                .names("id", "fecha", "monto", "tipo") // specifies the names of the fields in the file
                .fieldSetMapper(
                        // maps the fields in the file to a LegacyTransaction object
                        fieldSet -> new LegacyTransaction(
                                fieldSet.readString("id"),
                                fieldSet.readString("fecha"),
                                fieldSet.readString("monto"),
                                fieldSet.readString("tipo")))
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<LegacyInterestAccount> interestReader(@Value("${legacy.data.week}") String dataWeek) {
        return new FlatFileItemReaderBuilder<LegacyInterestAccount>()
                .name("interestReader")
                .resource(new ClassPathResource("input/" + dataWeek + "/intereses.csv"))
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuenta_id", "nombre", "saldo", "edad", "tipo") // specifies the names of the fields in the file
                .fieldSetMapper(
                        // maps the fields in the file to a LegacyInterestAccount object
                        fieldSet -> new LegacyInterestAccount(
                                fieldSet.readString("cuenta_id"),
                                fieldSet.readString("nombre"),
                                fieldSet.readString("saldo"),
                                fieldSet.readString("edad"),
                                fieldSet.readString("tipo")))
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<LegacyAnnualEntry> annualReader(@Value("${legacy.data.week}") String dataWeek) {
        return new FlatFileItemReaderBuilder<LegacyAnnualEntry>()
                .name("annualReader")
                .resource(new ClassPathResource("input/" + dataWeek + "/cuentas_anuales.csv"))
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuenta_id", "fecha", "transaccion", "monto", "descripcion") // specifies the names of the fields in the file
                .fieldSetMapper(
                        // maps the fields in the file to a LegacyAnnualEntry object
                        fieldSet -> new LegacyAnnualEntry(
                                fieldSet.readString("cuenta_id"),
                                fieldSet.readString("fecha"),
                                fieldSet.readString("transaccion"),
                                fieldSet.readString("monto"),
                                fieldSet.readString("descripcion")))
                .build();
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
    public Step dailyTransactionsStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<LegacyTransaction> transactionReader,
                                      DailyTransactionProcessor processor,
                                      JdbcBatchItemWriter<DailyTransactionSummary> transactionWriter,
                                      @Value("${batch.chunk-size}") int chunkSize) {
        return new StepBuilder("dailyTransactionsStep", jobRepository)
                .<LegacyTransaction, DailyTransactionSummary>chunk(chunkSize, transactionManager)
                .reader(transactionReader)
                .processor(processor)
                .writer(transactionWriter)
                .build();
    }

    @Bean
    // The monthlyInterestStep bean defines a step in the batch job that processes monthly interest calculations.
    // It reads legacy interest account data from a CSV file, processes it using the MonthlyInterestProcessor, 
    // and writes the results to the database using the interestWriter.
    // Flow: intereses.csv -> interestReader -> MonthlyInterestProcessor
    //       -> interestWriter -> monthly_interest_results.
    public Step monthlyInterestStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    FlatFileItemReader<LegacyInterestAccount> interestReader,
                                    MonthlyInterestProcessor processor,
                                    JdbcBatchItemWriter<MonthlyInterestResult> interestWriter,
                                    @Value("${batch.chunk-size}") int chunkSize) {
        return new StepBuilder("monthlyInterestStep", jobRepository)
                .<LegacyInterestAccount, MonthlyInterestResult>chunk(chunkSize, transactionManager)
                .reader(interestReader)
                .processor(processor)
                .writer(interestWriter)
                .build();
    }

    @Bean
    // The annualStatementsStep bean defines a step in the batch job that processes annual statements.
    // It reads legacy annual entry data from a CSV file, processes it using the AnnualStatementProcessor, 
    // and writes the results to a report using the AnnualStatementReportWriter.
    // Flow: cuentas_anuales.csv -> annualReader -> AnnualStatementProcessor
    //       -> AnnualStatementReportWriter -> annual_statement_entries + annual_statement_report.csv.
    public Step annualStatementsStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager,
                                     FlatFileItemReader<LegacyAnnualEntry> annualReader,
                                     AnnualStatementProcessor processor,
                                     AnnualStatementReportWriter annualStatementReportWriter,
                                     @Value("${batch.chunk-size}") int chunkSize) {
        return new StepBuilder("annualStatementsStep", jobRepository)
                .<LegacyAnnualEntry, AnnualStatementEntry>chunk(chunkSize, transactionManager)
                .reader(annualReader)
                .processor(processor)
                .writer(annualStatementReportWriter)
                .build();
    }

    @Bean
    // The dailyTransactionsJob bean defines a batch job that consists of the dailyTransactionsStep.
    // It orchestrates the execution of the step and manages the job lifecycle.
    // Job flow: dailyTransactionsJob -> dailyTransactionsStep.
    public Job dailyTransactionsJob(JobRepository jobRepository, Step dailyTransactionsStep) {
        return new JobBuilder("dailyTransactionsJob", jobRepository)
                .start(dailyTransactionsStep)
                .build();
    }

    @Bean
    // The monthlyInterestJob bean defines a batch job that consists of the monthlyInterestStep.
    // It orchestrates the execution of the step and manages the job lifecycle.
    // Job flow: monthlyInterestJob -> monthlyInterestStep.
    public Job monthlyInterestJob(JobRepository jobRepository, Step monthlyInterestStep) {
        return new JobBuilder("monthlyInterestJob", jobRepository)
                .start(monthlyInterestStep)
                .build();
    }

    @Bean
    // The annualStatementsJob bean defines a batch job that consists of the annualStatementsStep.
    // It orchestrates the execution of the step and manages the job lifecycle.
    // Job flow: annualStatementsJob -> annualStatementsStep.
    public Job annualStatementsJob(JobRepository jobRepository, Step annualStatementsStep) {
        return new JobBuilder("annualStatementsJob", jobRepository)
                .start(annualStatementsStep)
                .build();
    }
}
