package cl.duoc.backendiii.bff.atm.config;

import cl.duoc.backendiii.events.TopicNames;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfiguration {
    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic withdrawalRequested() {
        return topic(TopicNames.WITHDRAWAL_REQUESTED);
    }

    @Bean
    NewTopic fundsReserved() {
        return topic(TopicNames.FUNDS_RESERVED);
    }

    @Bean
    NewTopic fundsRejected() {
        return topic(TopicNames.FUNDS_REJECTED);
    }

    @Bean
    NewTopic riskApproved() {
        return topic(TopicNames.RISK_APPROVED);
    }

    @Bean
    NewTopic riskRejected() {
        return topic(TopicNames.RISK_REJECTED);
    }

    @Bean
    NewTopic withdrawalConfirmed() {
        return topic(TopicNames.WITHDRAWAL_CONFIRMED);
    }

    @Bean
    NewTopic withdrawalCancelled() {
        return topic(TopicNames.WITHDRAWAL_CANCELLED);
    }

    @Bean
    NewTopic fundsReleaseRequested() {
        return topic(TopicNames.FUNDS_RELEASE_REQUESTED);
    }

    @Bean
    NewTopic fundsReleased() {
        return topic(TopicNames.FUNDS_RELEASED);
    }
}
