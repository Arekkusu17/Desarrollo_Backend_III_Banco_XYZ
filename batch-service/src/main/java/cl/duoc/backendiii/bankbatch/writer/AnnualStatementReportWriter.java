package cl.duoc.backendiii.bankbatch.writer;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Component
public class AnnualStatementReportWriter implements ItemWriter<AnnualStatementEntry>, JobExecutionListener {
    private final JdbcTemplate jdbc;
    private final Path report;

    public AnnualStatementReportWriter(JdbcTemplate jdbc, @Value("${bank.annual.report-output}") String output) {
        this.jdbc = jdbc;
        report = Path.of(output).toAbsolutePath();
    }

    @Override
    public void write(Chunk<? extends AnnualStatementEntry> chunk) {
        jdbc.batchUpdate("""
                INSERT INTO annual_statement_entries(account_id, transaction_date, transaction_type, amount, description, audit_flag)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id, transaction_date, transaction_type, amount, description)
                DO UPDATE SET audit_flag = EXCLUDED.audit_flag
                """, chunk.getItems(), chunk.size(), (ps, item) -> {
                    ps.setLong(1, item.accountId());
                    ps.setObject(2, item.transactionDate());
                    ps.setString(3, item.transactionType());
                    ps.setBigDecimal(4, item.amount());
                    ps.setString(5, item.description());
                    ps.setString(6, item.auditFlag());
                });
    }

    @Override
    public void afterJob(JobExecution execution) {
        if (execution.getStatus() != BatchStatus.COMPLETED) return;
        try {
            exportReport();
        } catch (IOException ex) {
            execution.setStatus(BatchStatus.FAILED);
            execution.setExitStatus(ExitStatus.FAILED.addExitDescription(ex));
        }
    }

    // Export committed rows once, so chunk retries cannot duplicate the CSV.
    public void exportReport() throws IOException {
        Files.createDirectories(report.getParent());
        Path temporary = Files.createTempFile(report.getParent(), "annual-", ".csv");
        try {
            try (CSVPrinter csv = new CSVPrinter(Files.newBufferedWriter(temporary, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setHeader("account_id", "transaction_date", "transaction_type",
                            "amount", "description", "audit_flag").get())) {
                jdbc.query("""
                        SELECT account_id, transaction_date, transaction_type, amount, description, audit_flag
                        FROM annual_statement_entries
                        ORDER BY account_id, transaction_date, transaction_type, amount, description
                        """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                            try {
                                csv.printRecord(rs.getLong(1), rs.getDate(2), rs.getString(3),
                                        rs.getBigDecimal(4), rs.getString(5), rs.getString(6));
                            } catch (IOException ex) {
                                throw new java.io.UncheckedIOException(ex);
                            }
                        });
            }
            try {
                Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (java.io.UncheckedIOException ex) {
            throw ex.getCause();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
