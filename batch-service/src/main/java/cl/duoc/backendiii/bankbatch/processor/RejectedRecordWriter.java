package cl.duoc.backendiii.bankbatch.processor;

import cl.duoc.backendiii.bankbatch.domain.RejectedRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
// This class is responsible for writing rejected records to the database.
public class RejectedRecordWriter {

    private final JdbcTemplate jdbcTemplate;
    private final int processNameLimit;
    private final int recordKeyLimit;
    private final int reasonLimit;

    public RejectedRecordWriter(JdbcTemplate jdbcTemplate,
                                @Value("${bank.rejected.process-name-limit}") int processNameLimit,
                                @Value("${bank.rejected.record-key-limit}") int recordKeyLimit,
                                @Value("${bank.rejected.reason-limit}") int reasonLimit) {
        this.jdbcTemplate = jdbcTemplate;
        this.processNameLimit = processNameLimit;
        this.recordKeyLimit = recordKeyLimit;
        this.reasonLimit = reasonLimit;
    }

    public void reject(RejectedRecord rejectedRecord) {
        jdbcTemplate.update("""
                INSERT INTO rejected_records(process_name, record_key, reason, payload, fingerprint)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """,
                limit(rejectedRecord.processName(), processNameLimit),
                limit(rejectedRecord.recordKey(), recordKeyLimit),
                limit(rejectedRecord.reason(), reasonLimit),
                rejectedRecord.payload(),
                fingerprint(rejectedRecord));
    }

    private String fingerprint(RejectedRecord record) {
        try {
            String value = record.processName() + "\u001f" + record.recordKey() + "\u001f" + record.reason()
                    + "\u001f" + record.payload();
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
