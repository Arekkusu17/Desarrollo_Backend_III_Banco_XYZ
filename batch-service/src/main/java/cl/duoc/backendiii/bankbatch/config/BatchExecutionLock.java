package cl.duoc.backendiii.bankbatch.config;

import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
public class BatchExecutionLock implements ChunkListener {
    private static final ConcurrentHashMap<String, Semaphore> localLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Lease> active = new ConcurrentHashMap<>();
    private final DataSource dataSource;

    public BatchExecutionLock(DataSource dataSource) { this.dataSource = dataSource; }

    public Lease acquire(String job, JobParameters parameters) throws Exception {
        Connection connection = dataSource.getConnection();
        try {
            String identity = connection.getSchema() + "|" + job + "|" + parameters;
            long key = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8))).getLong();
            boolean postgres = "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
            Semaphore local = postgres ? null : localLocks.computeIfAbsent(
                    connection.getMetaData().getURL() + identity, ignored -> new Semaphore(1));
            boolean locked;
            if (postgres) {
                try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                    statement.setLong(1, key);
                    try (var result = statement.executeQuery()) { result.next(); locked = result.getBoolean(1); }
                }
            } else {
                locked = local.tryAcquire();
            }
            if (!locked) { connection.close(); return null; }
            Lease lease = new Lease(job, connection, key, postgres, local);
            active.put(job, lease);
            return lease;
        } catch (Exception ex) {
            try { connection.close(); } catch (SQLException closeError) { ex.addSuppressed(closeError); }
            throw ex;
        }
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        String job = context.getStepContext().getStepExecution().getJobExecution().getJobInstance().getJobName();
        Lease lease = active.get(job);
        if (lease == null || !lease.valid()) throw new IllegalStateException("Bloqueo de ejecucion batch perdido");
    }

    public final class Lease implements AutoCloseable {
        private final String job;
        private final Connection connection;
        private final long key;
        private final boolean postgres;
        private final Semaphore local;

        private Lease(String job, Connection connection, long key, boolean postgres, Semaphore local) {
            this.job = job; this.connection = connection; this.key = key; this.postgres = postgres; this.local = local;
        }

        private synchronized boolean valid() {
            try { return !connection.isClosed() && connection.isValid(1); }
            catch (SQLException ex) { return false; }
        }

        @Override
        public void close() {
            active.remove(job, this);
            try {
                if (postgres && valid()) {
                    try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                        statement.setLong(1, key); statement.execute();
                    }
                }
            } catch (SQLException ex) {
                org.slf4j.LoggerFactory.getLogger(BatchExecutionLock.class).warn("No se pudo liberar bloqueo {}", job, ex);
                try { connection.abort(Runnable::run); } catch (SQLException abortError) { ex.addSuppressed(abortError); }
            } finally {
                if (!postgres) local.release();
                try { connection.close(); } catch (SQLException ignored) { }
            }
        }
    }
}
