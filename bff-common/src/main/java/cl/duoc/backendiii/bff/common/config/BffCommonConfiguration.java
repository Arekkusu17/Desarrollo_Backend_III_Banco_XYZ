package cl.duoc.backendiii.bff.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ChannelAuthProperties.class)
public class BffCommonConfiguration {

    @Bean("loadBalancedRestClientBuilder")
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean("standardRestClientBuilder")
    RestClient.Builder standardRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient coreBankingRestClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder,
                                     @Qualifier("standardRestClientBuilder") RestClient.Builder standardRestClientBuilder,
                                     @Value("${core.banking.url}") String coreBankingUrl,
                                     OAuth2ServiceTokenProvider tokenProvider) {
        RestClient.Builder builder = usesServiceDiscovery(coreBankingUrl)
                ? loadBalancedRestClientBuilder
                : standardRestClientBuilder;

        return builder
                .requestFactory(coreBankingRequestFactory())
                .baseUrl(coreBankingUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.getToken());
                    return execution.execute(request, body);
                })
                .build();
    }

    private SimpleClientHttpRequestFactory coreBankingRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return requestFactory;
    }

    private boolean usesServiceDiscovery(String coreBankingUrl) {
        return !coreBankingUrl.contains("localhost") && !coreBankingUrl.contains("127.0.0.1");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ChannelAuthProperties channelAuthProperties) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers(channelAuthProperties.pathPattern())
                        .hasAuthority("SCOPE_" + channelAuthProperties.scope())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Token OAuth2 requerido o invalido\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\"Token valido sin scope suficiente para este canal\"}");
                        }));

        return http.build();
    }

    @Bean
    OAuth2ServiceTokenProvider oauth2ServiceTokenProvider(
            @Value("${app.oauth.token-url}") String tokenUrl,
            @Value("${app.oauth.client-id}") String clientId,
            @Value("${app.oauth.client-secret}") String clientSecret,
            @Value("${app.oauth.core-scope:core.read}") String scope) {
        return new OAuth2ServiceTokenProvider(tokenUrl, clientId, clientSecret, scope);
    }

    public static class OAuth2ServiceTokenProvider {
        private final RestClient tokenClient = RestClient.create();
        private final String tokenUrl;
        private final String clientId;
        private final String clientSecret;
        private final String scope;

        OAuth2ServiceTokenProvider(String tokenUrl, String clientId, String clientSecret, String scope) {
            this.tokenUrl = tokenUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scope = scope;
        }

        String getToken() {
            TokenResponse token = tokenClient.post()
                    .uri(tokenUrl)
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials&scope=" + scope)
                    .retrieve()
                    .body(TokenResponse.class);

            if (token == null || token.accessToken() == null) {
                throw new IllegalStateException("No fue posible obtener token OAuth2 para core-banking");
            }
            return token.accessToken();
        }
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken) {
    }
}
