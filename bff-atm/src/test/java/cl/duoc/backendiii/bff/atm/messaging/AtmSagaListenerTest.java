package cl.duoc.backendiii.bff.atm.messaging;

import cl.duoc.backendiii.bff.atm.model.*;
import cl.duoc.backendiii.bff.atm.repository.WithdrawalTransactionRepository;
import cl.duoc.backendiii.events.*;
import cl.duoc.backendiii.events.store.JdbcEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AtmSagaListenerTest {
    @Test
    void rejectedWithdrawalAndCompensationSurviveRestartWithoutDuplicateEvents() {
        var ds = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName(UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .addScript("schema.sql").build();
        var jdbc = new JdbcTemplate(ds);
        var manager = new DataSourceTransactionManager(ds);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var repository = new WithdrawalTransactionRepository(jdbc);
        UUID id = UUID.randomUUID();
        repository.save(new WithdrawalTransaction(id, UUID.randomUUID(), 101, BigDecimal.TEN,
                Instant.now(), WithdrawalStatus.PENDING, "test"));
        var event = new RiskRejectedEvent(UUID.randomUUID(), id, Instant.now(), 101, BigDecimal.TEN, "test");
        new AtmSagaListener(repository, new JdbcEventStore("bff-atm", jdbc, mapper, null, manager))
                .onRiskRejected(event);
        var restarted = new WithdrawalTransactionRepository(jdbc);
        new AtmSagaListener(restarted, new JdbcEventStore("bff-atm", jdbc, mapper, null, manager))
                .onRiskRejected(event);
        assertThat(restarted.findById(id).orElseThrow().getStatus()).isEqualTo(WithdrawalStatus.RISK_REJECTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(2);
        ds.shutdown();
    }
}
