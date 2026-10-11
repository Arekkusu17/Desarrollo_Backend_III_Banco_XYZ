package cl.duoc.backendiii.bankbatch.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "BATCH_RECOVERY_PG_URL", matches = "jdbc:postgresql:.*")
class BatchFailureRecoveryIT {
    private final Path output = Path.of("target/recovery-validation").toAbsolutePath();
    private final List<Process> children = new ArrayList<>();
    private Map<String, List<Map<String, Object>>> reference;
    private String annualReference;

    @BeforeAll
    void referenceRun() throws Exception {
        Files.createDirectories(output);
        String schema = schema("reference");
        JdbcTemplate jdbc = prepare(schema);
        Process process = launch(schema, true, "reference");
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(Files.readString(output.resolve("reference.log"))).isZero();
        reference = snapshot(jdbc);
        annualReference = Files.readString(output.resolve(schema + "-annual.csv"));
    }

    @AfterAll
    void cleanup() throws Exception {
        for (Process child : children) {
            if (child.isAlive()) {
                child.destroy();
                if (!child.waitFor(10, TimeUnit.SECONDS)) child.destroyForcibly().waitFor();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dailyTransactionsJob", "monthlyInterestJob", "annualStatementsJob"})
    void abruptTerminationRestartsFromCommittedPartitionCheckpoints(String job) throws Exception {
        String schema = schema("crash");
        JdbcTemplate jdbc = prepare(schema);
        String table = switch (job) {
            case "dailyTransactionsJob" -> "daily_transaction_summary";
            case "monthlyInterestJob" -> "monthly_interest_results";
            default -> "annual_statement_entries";
        };
        slowWrites(jdbc, table);
        Process original = launch(schema, true, schema + "-before");
        await(() -> hasCheckpoint(jdbc, job), Duration.ofSeconds(120));
        original.destroyForcibly().waitFor();
        assertThat(jdbc.queryForObject("SELECT status FROM batch_job_execution e JOIN batch_job_instance i USING(job_instance_id) WHERE i.job_name=? ORDER BY job_execution_id DESC LIMIT 1",
                String.class, job)).isEqualTo("STARTED");
        Process restarted = launch(schema, true, schema + "-after");
        assertThat(restarted.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(restarted.exitValue()).as(Files.readString(output.resolve(schema + "-after.log"))).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(s.read_count) FROM batch_step_execution s JOIN batch_job_execution e USING(job_execution_id) JOIN batch_job_instance i USING(job_instance_id) WHERE i.job_name=? AND s.step_name LIKE '%WorkerStep:%'",
                Integer.class, job)).isEqualTo(1000);
        assertRecoveredResults(jdbc, schema);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BATCH_RECOVERY_PG_CONTAINER", matches = "banco-eft-recovery-postgres")
    void temporaryDatabaseOutageRecoversAutomatically() throws Exception {
        String schema = schema("outage");
        JdbcTemplate jdbc = prepare(schema);
        slowWrites(jdbc, "daily_transaction_summary");
        Process batch = launch(schema, false, schema + "-outage");
        await(() -> hasCheckpoint(jdbc, "dailyTransactionsJob"), Duration.ofSeconds(120));
        try {
            docker("stop", "--time", "1", "banco-eft-recovery-postgres");
            Thread.sleep(1500);
        } finally {
            docker("start", "banco-eft-recovery-postgres");
        }
        await(() -> jdbc.queryForObject("SELECT COUNT(DISTINCT i.job_name) FROM batch_job_execution e JOIN batch_job_instance i USING(job_instance_id) WHERE e.status='COMPLETED'", Integer.class) == 3,
                Duration.ofSeconds(150));
        assertThat(batch.isAlive()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_job_execution WHERE status IN ('FAILED','UNKNOWN')", Integer.class)).isPositive();
        assertThat(jdbc.queryForList("SELECT COUNT(*) AS attempts FROM batch_job_execution GROUP BY job_instance_id", Integer.class))
                .allMatch(attempts -> attempts <= 3);
        assertRecoveredResults(jdbc, schema);
        batch.destroy();
        assertThat(batch.waitFor(15, TimeUnit.SECONDS)).isTrue();
    }

    private void assertRecoveredResults(JdbcTemplate jdbc, String schema) throws Exception {
        assertThat(snapshot(jdbc)).isEqualTo(reference);
        assertThat(Files.readString(output.resolve(schema + "-annual.csv"))).isEqualTo(annualReference);
    }

    private boolean hasCheckpoint(JdbcTemplate jdbc, String job) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM batch_step_execution s JOIN batch_job_execution e USING(job_execution_id) JOIN batch_job_instance i USING(job_instance_id) WHERE i.job_name=? AND e.status='STARTED' AND s.step_name LIKE '%WorkerStep:%' AND s.read_count>0", Integer.class, job) > 0;
    }

    private String schema(String prefix) { return "recovery_" + prefix + "_" + UUID.randomUUID().toString().replace("-", ""); }

    private JdbcTemplate prepare(String schema) {
        String url = System.getenv("BATCH_RECOVERY_PG_URL");
        assertThat(url).doesNotContain("banco_xyz");
        var root = new JdbcTemplate(new DriverManagerDataSource(url, "banco", "banco"));
        root.execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + "?currentSchema=" + schema + "&socketTimeout=2&connectTimeout=2", "banco", "banco");
        var populator = new ResourceDatabasePopulator(new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql"),
                new ClassPathResource("schema.sql"));
        populator.execute(ds);
        return new JdbcTemplate(ds);
    }

    private void slowWrites(JdbcTemplate jdbc, String table) {
        jdbc.execute("CREATE FUNCTION slow_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(0.08); RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER slow_write BEFORE INSERT OR UPDATE ON " + table + " FOR EACH ROW EXECUTE FUNCTION slow_write()");
    }

    private Process launch(String schema, boolean exit, String log) throws Exception {
        String url = System.getenv("BATCH_RECOVERY_PG_URL") + "?currentSchema=" + schema + "&socketTimeout=2&connectTimeout=2";
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx128m", "-jar",
                Path.of("target/batch-service-0.0.1-SNAPSHOT.jar").toAbsolutePath().toString(),
                "--spring.main.web-application-type=none", "--spring.cloud.config.enabled=false",
                "--eureka.client.enabled=false", "--spring.cloud.discovery.enabled=false",
                "--spring.datasource.url=" + url, "--spring.datasource.username=banco", "--spring.datasource.password=banco",
                "--spring.datasource.hikari.maximum-pool-size=10", "--spring.datasource.hikari.minimum-idle=1",
                "--spring.datasource.hikari.connection-timeout=3000", "--batch.run-key=" + schema,
                "--legacy.data.week=eft", "--batch.thread-pool-size=3", "--batch.partition-grid-size=3",
                "--batch.recovery.poll-ms=1000", "--batch.exit-after-run=" + exit,
                "--bank.annual.report-output=" + output.resolve(schema + "-annual.csv"))
                .redirectErrorStream(true).redirectOutput(output.resolve(log + ".log").toFile()).start();
        children.add(process);
        return process;
    }

    private Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        result.put("daily", jdbc.queryForList("SELECT * FROM daily_transaction_summary ORDER BY transaction_id"));
        result.put("interest", jdbc.queryForList("SELECT * FROM monthly_interest_results ORDER BY account_id"));
        result.put("annual", jdbc.queryForList("SELECT account_id,transaction_date,transaction_type,amount,description,audit_flag FROM annual_statement_entries ORDER BY account_id,transaction_date,transaction_type,amount,description"));
        result.put("rejected", jdbc.queryForList("SELECT process_name,record_key,reason,payload,fingerprint FROM rejected_records ORDER BY process_name,record_key,reason,payload"));
        return result;
    }

    private void await(BooleanSupplier condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try { if (condition.getAsBoolean()) return; }
            catch (org.springframework.dao.DataAccessException ignored) { }
            Thread.sleep(100);
        }
        fail("La condicion no se cumplio antes de " + timeout);
    }

    private void docker(String... args) throws Exception {
        var command = new ArrayList<String>(); command.add("docker"); command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(new String(process.getInputStream().readAllBytes())).isZero();
    }
}
