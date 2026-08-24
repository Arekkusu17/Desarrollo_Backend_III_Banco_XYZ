package cl.duoc.backendiii.bankbatch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class JobExecutionSummaryListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionSummaryListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final String dataWeek;
    private final int chunkSize;
    private final int threadPoolSize;
    private final int skipLimit;

    public JobExecutionSummaryListener(JdbcTemplate jdbcTemplate,
                                       @Value("${legacy.data.week}") String dataWeek,
                                       @Value("${batch.chunk-size}") int chunkSize,
                                       @Value("${batch.thread-pool-size}") int threadPoolSize,
                                       @Value("${batch.skip-limit}") int skipLimit) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataWeek = dataWeek;
        this.chunkSize = chunkSize;
        this.threadPoolSize = threadPoolSize;
        this.skipLimit = skipLimit;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("========== Resumen de ejecucion: {} ==========", jobExecution.getJobInstance().getJobName());
        log.info("Estado final: {}", jobExecution.getStatus());
        log.info("Configuracion batch activa: dataWeek={} | chunkSize={} | threadPoolSize={} | skipLimit={}",
                dataWeek,
                chunkSize,
                threadPoolSize,
                skipLimit);

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            log.info("Step {} | leidos={} | escritos={} | filtrados={} | commits={}",
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getFilterCount(),
                    stepExecution.getCommitCount());
        }

        log.info("Salida persistida: {}", outputSummary(jobExecution.getJobInstance().getJobName()));
        log.info("Rechazos de esta ejecucion: {}", rejectedCount(jobExecution));
    }

    private String outputSummary(String jobName) {
        return switch (jobName) {
            case "dailyTransactionsJob" -> "daily_transaction_summary="
                    + countRows("daily_transaction_summary") + " registros";
            case "monthlyInterestJob" -> "monthly_interest_results="
                    + countRows("monthly_interest_results") + " registros";
            case "annualStatementsJob" -> "annual_statement_entries="
                    + countRows("annual_statement_entries")
                    + " registros; output/annual_statement_report.csv generado";
            default -> "sin tabla asociada";
        };
    }

    private Integer rejectedCount(JobExecution jobExecution) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM rejected_records
                        WHERE process_name = ?
                          AND created_at BETWEEN ? AND ?
                        """,
                Integer.class,
                jobExecution.getJobInstance().getJobName(),
                Timestamp.valueOf(jobExecution.getStartTime()),
                Timestamp.valueOf(jobExecution.getEndTime()));
    }

    private Integer countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
