package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.events.*;
import cl.duoc.backendiii.events.store.JdbcEventStore;
import cl.duoc.backendiii.msaccount.model.AccountRequest;
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

class WithdrawalFundsListenerTest {
    @Test
    void durableReservationSurvivesNewServiceInstanceAndCompensatesOnce() {
        var ds = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName(UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .addScript("schema.sql").build();
        var jdbc = new JdbcTemplate(ds);
        var manager = new DataSourceTransactionManager(ds);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var ledger = new AccountLedger(jdbc, manager, mapper);
        ledger.open(new AccountRequest(101L, "Alex", "ahorro", 30, new BigDecimal("100")));
        var store = new JdbcEventStore("ms-account", jdbc, mapper, null, manager);
        var listener = new WithdrawalFundsListener(ledger, store);
        UUID tx = UUID.randomUUID();
        var requested = new WithdrawalRequestedEvent(UUID.randomUUID(), tx, Instant.now(), 101,
                new BigDecimal("25"), "ATM");
        listener.requested(requested);
        listener.requested(requested);
        assertThat(ledger.details(101).balance()).isEqualByComparingTo("75");
        var restartedLedger = new AccountLedger(jdbc, manager, mapper);
        var restarted = new WithdrawalFundsListener(restartedLedger,
                new JdbcEventStore("ms-account", jdbc, mapper, null, manager));
        var release = new FundsReleaseRequestedEvent(UUID.randomUUID(), tx, Instant.now(), 101,
                new BigDecimal("25"), "test");
        restarted.released(release);
        restarted.released(release);
        assertThat(restartedLedger.details(101).balance()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM account_reservations WHERE transaction_id=?",
                String.class, tx)).isEqualTo("RELEASED");
        ds.shutdown();
    }
}
