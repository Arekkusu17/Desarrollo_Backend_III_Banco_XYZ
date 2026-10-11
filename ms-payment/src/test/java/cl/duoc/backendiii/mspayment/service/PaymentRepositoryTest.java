package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PaymentRepositoryTest {
    @Test
    void savesPendingPaymentAndEnqueuesOneEventWhenCompleted() {
        var ds = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName(UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .addScript("schema.sql").build();
        var jdbc = new JdbcTemplate(ds);
        var repository = new PaymentRepository(jdbc);
        UUID id = UUID.randomUUID();
        var pending = new PaymentOperationResponse(id, PaymentType.DEPOSIT, null, 101L,
                BigDecimal.TEN, PaymentStatus.PENDING, "test", LocalDateTime.now());
        repository.save(pending);
        repository.save(pending);
        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status -> repository.complete(id));
        tx.executeWithoutResult(status -> repository.complete(id));
        assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(PaymentStatus.ACCEPTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_outbox", Integer.class)).isEqualTo(1);
        ds.shutdown();
    }
}
