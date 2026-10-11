package cl.duoc.backendiii.bankxyz.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/gateway/web/**").hasAuthority("SCOPE_web.read")
                        .pathMatchers("/gateway/mobile/**").hasAuthority("SCOPE_mobile.read")
                        .pathMatchers("/gateway/atm/**").hasAnyAuthority("SCOPE_atm.read", "SCOPE_atm.write")
                        .pathMatchers("/gateway/cuentas", "/gateway/cuentas/**").hasAnyAuthority("SCOPE_account.read", "SCOPE_account.write")
                        .pathMatchers("/gateway/payments/**").hasAnyAuthority("SCOPE_payment.read", "SCOPE_payment.write")
                        .pathMatchers("/gateway/customers/**").hasAnyAuthority("SCOPE_customer.read", "SCOPE_customer.write")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
