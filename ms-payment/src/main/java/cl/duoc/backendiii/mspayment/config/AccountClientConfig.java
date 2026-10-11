package cl.duoc.backendiii.mspayment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

@Configuration
public class AccountClientConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder accountClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient accountRestClient(RestClient.Builder accountClientBuilder,
            @Value("${account.service.url:http://ms-account}") String url,
            @Value("${app.oauth.token-url:http://auth-server:9000/oauth2/token}") String tokenUrl,
            @Value("${app.oauth.client-secret:payment-secret}") String secret) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        RestClient tokenClient = RestClient.builder().requestFactory(factory).build();
        return accountClientBuilder.requestFactory(factory).baseUrl(url)
                .requestInterceptor((request, body, execution) -> {
                    Token token = tokenClient.post().uri(tokenUrl)
                            .headers(headers -> headers.setBasicAuth("ms-payment", secret))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body("grant_type=client_credentials&scope=account.execute")
                            .retrieve().body(Token.class);
                    if (token == null || token.accessToken() == null) {
                        throw new IllegalStateException("No se pudo obtener token para cuentas");
                    }
                    request.getHeaders().setBearerAuth(token.accessToken());
                    return execution.execute(request, body);
                }).build();
    }

    record Token(@com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken) {
    }
}
