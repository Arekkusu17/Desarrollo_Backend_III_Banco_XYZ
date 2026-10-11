package cl.duoc.backendiii.bankbatch.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class AnnualStatementReportWriterTest {
    @TempDir Path directory;

    @Test
    void repeatedExportHasNoDuplicateRowsAndEscapesDescriptions() throws Exception {
        var ds = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE annual_statement_entries(account_id BIGINT, transaction_date DATE,
                transaction_type VARCHAR(20), amount DECIMAL(18,2), description VARCHAR(255), audit_flag VARCHAR(80))
                """);
        jdbc.update("INSERT INTO annual_statement_entries VALUES (101, DATE '2024-01-01', 'deposito', 10, ?, 'OK')",
                "Pago, con \"comillas\"");
        Path output = directory.resolve("annual.csv");
        var writer = new AnnualStatementReportWriter(jdbc, output.toString());
        writer.exportReport();
        String first = Files.readString(output);
        writer.exportReport();
        assertThat(Files.readString(output)).isEqualTo(first);
        try (var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().get()
                .parse(Files.newBufferedReader(output))) {
            var rows = parser.getRecords();
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().get("description")).isEqualTo("Pago, con \"comillas\"");
        }
        ds.shutdown();
    }
}
