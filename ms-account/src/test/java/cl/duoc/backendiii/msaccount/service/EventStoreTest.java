package cl.duoc.backendiii.msaccount.service;

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

class EventStoreTest {
    @Test
    void failedConsumerRollsBackInboxAndOutgoingEventSoItCanRetry() {
        var ds = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName(UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .addScript("schema.sql").build();
        var jdbc = new JdbcTemplate(ds);
        var store = new JdbcEventStore("test", jdbc, new ObjectMapper().findAndRegisterModules(),
                null, new DataSourceTransactionManager(ds));
        var event = new WithdrawalRequestedEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                101, BigDecimal.TEN, "ATM");
        assertThatThrownBy(() -> store.consume(event, () -> {
            store.enqueue(TopicNames.WITHDRAWAL_REQUESTED, event);
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isZero();
        store.consume(event, () -> store.enqueue(TopicNames.WITHDRAWAL_REQUESTED, event));
        store.consume(event, () -> { throw new AssertionError("duplicate consumer"); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(1);
        ds.shutdown();
    }
}
