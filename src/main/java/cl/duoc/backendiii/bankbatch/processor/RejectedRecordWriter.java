package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
// This class is responsible for writing rejected records to the database.
public class RejectedRecordWriter {

    private static final int PROCESS_NAME_LIMIT = 80;
    private static final int RECORD_KEY_LIMIT = 120;
    private static final int REASON_LIMIT = 255;

    private final JdbcTemplate jdbcTemplate;

    public RejectedRecordWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reject(RejectedRecord rejectedRecord) {
        jdbcTemplate.update("""
                INSERT INTO rejected_records(process_name, record_key, reason, payload)
                VALUES (?, ?, ?, ?)
                """,
                limit(rejectedRecord.processName(), PROCESS_NAME_LIMIT),
                limit(rejectedRecord.recordKey(), RECORD_KEY_LIMIT),
                limit(rejectedRecord.reason(), REASON_LIMIT),
                rejectedRecord.payload());
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
