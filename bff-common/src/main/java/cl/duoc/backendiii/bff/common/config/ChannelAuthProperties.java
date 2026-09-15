package cl.duoc.backendiii.bff.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "channel")
public record ChannelAuthProperties(
        String name,
        String pathPattern,
        String role
) {
}
