package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
// This class is responsible for writing rejected records to the database.
public class RejectedRecordWriter {

    private final JdbcTemplate jdbcTemplate;

    public RejectedRecordWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reject(RejectedRecord rejectedRecord) {
        jdbcTemplate.update("""
                INSERT INTO rejected_records(process_name, record_key, reason, payload)
                VALUES (?, ?, ?, ?)
                """,
                rejectedRecord.processName(),
                rejectedRecord.recordKey(),
                rejectedRecord.reason(),
                rejectedRecord.payload());
    }
}
