package cl.duoc.backendiii.bff.atm.config;

import cl.duoc.backendiii.events.store.JdbcEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
public class AtmEventsConfig {
    @Bean
    JdbcEventStore atmEvents(JdbcTemplate jdbc, ObjectMapper mapper,
            KafkaTemplate<String, Object> kafka, PlatformTransactionManager manager) {
        return new JdbcEventStore("bff-atm", jdbc, mapper, kafka, manager);
    }

    @Bean
    OutboxTask atmOutboxTask(JdbcEventStore events) {
        return new OutboxTask(events);
    }

    public record OutboxTask(JdbcEventStore events) {
        @Scheduled(fixedDelayString = "${events.outbox.delay:2000}")
        public void publish() { events.publish(); }
    }
}
