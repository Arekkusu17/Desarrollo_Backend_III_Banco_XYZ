package cl.duoc.backendiii.msaccount.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cuentas/operaciones").hasAuthority("SCOPE_account.execute")
                        .requestMatchers(HttpMethod.POST, "/api/cuentas/**").hasAuthority("SCOPE_account.write")
                        .requestMatchers(HttpMethod.PUT, "/api/cuentas/**").hasAuthority("SCOPE_account.write")
                        .anyRequest().hasAuthority("SCOPE_account.read"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
