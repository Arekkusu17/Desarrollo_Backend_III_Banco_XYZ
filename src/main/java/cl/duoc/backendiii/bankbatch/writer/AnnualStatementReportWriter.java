package cl.duoc.backendiii.bankbatch.writer;

import cl.duoc.backendiii.bankbatch.domain.AnnualStatementEntry;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
// This writer is responsible for writing AnnualStatementEntry objects to both a CSV report and a database table.
public class AnnualStatementReportWriter implements ItemWriter<AnnualStatementEntry> {

    private static final Path REPORT_PATH = Path.of("output", "annual_statement_report.csv");

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    // The annual Step runs with a TaskExecutor, so multiple worker threads can call this writer at the same time.
    // PostgreSQL writes are handled safely by JdbcTemplate and the database transaction, but appending to a plain CSV
    // file is not atomic at the chunk level. This lock serializes only the file initialization and append operations,
    // preventing interleaved lines or a header being written while another thread is appending data.
    private final Object reportLock = new Object();

    public AnnualStatementReportWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    // The write method takes a chunk of AnnualStatementEntry items, writes them to the database, and appends them to a CSV report.
    public void write(Chunk<? extends AnnualStatementEntry> chunk) throws IOException {
        initializeReport();
        jdbcTemplate.batchUpdate("""
                INSERT INTO annual_statement_entries(account_id, transaction_date, transaction_type, amount, description, audit_flag)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id, transaction_date, transaction_type, amount, description)
                DO UPDATE SET audit_flag = EXCLUDED.audit_flag
                """,
                chunk.getItems(),
                chunk.size(),
                (ps, item) -> {
                    ps.setLong(1, item.accountId());
                    ps.setObject(2, item.transactionDate());
                    ps.setString(3, item.transactionType());
                    ps.setBigDecimal(4, item.amount());
                    ps.setString(5, item.description());
                    ps.setString(6, item.auditFlag());
                });

        appendReportLines(chunk);
    }

    private void appendReportLines(Chunk<? extends AnnualStatementEntry> chunk) throws IOException {
        // Build the chunk text before entering the synchronized block so the lock is held only during file IO.
        // This keeps the critical section short while still writing each chunk as one contiguous CSV append.
        StringBuilder lines = new StringBuilder();
        for (AnnualStatementEntry item : chunk) {
            lines.append(item.accountId()).append(',')
                    .append(item.transactionDate()).append(',')
                    .append(item.transactionType()).append(',')
                    .append(item.amount().setScale(2, RoundingMode.HALF_UP)).append(',')
                    .append(escape(item.description())).append(',')
                    .append(item.auditFlag()).append(System.lineSeparator());
        }
        synchronized (reportLock) {
            Files.writeString(REPORT_PATH, lines.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    // This method initializes the CSV report file by creating the necessary directories and writing the header line if it hasn't been initialized yet.
    private void initializeReport() throws IOException {
        // The lock works together with initialized.compareAndSet: only one thread creates/truncates the report,
        // and every other thread waits until the header is ready before appending its chunk.
        synchronized (reportLock) {
            if (initialized.compareAndSet(false, true)) {
                Files.createDirectories(REPORT_PATH.getParent());
                Files.writeString(REPORT_PATH,
                        "account_id,transaction_date,transaction_type,amount,description,audit_flag" + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
