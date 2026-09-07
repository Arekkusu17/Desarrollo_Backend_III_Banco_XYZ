package cl.duoc.backendiii.bff.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient coreBankingRestClient(@Value("${core.banking.url}") String coreBankingUrl) {
        return RestClient.builder()
                .baseUrl(coreBankingUrl)
                .build();
    }
}

