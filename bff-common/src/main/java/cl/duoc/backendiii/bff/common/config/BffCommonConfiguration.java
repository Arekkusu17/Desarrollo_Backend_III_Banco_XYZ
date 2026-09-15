package cl.duoc.backendiii.bff.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ChannelAuthProperties.class)
public class BffCommonConfiguration {

    @Bean
    RestClient coreBankingRestClient(@Value("${core.banking.url}") String coreBankingUrl) {
        return RestClient.builder()
                .baseUrl(coreBankingUrl)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService bffUsers(PasswordEncoder encoder,
                                @Value("${security.users.web.password}") String webPassword,
                                @Value("${security.users.mobile.password}") String mobilePassword,
                                @Value("${security.users.atm.password}") String atmPassword) {
        UserDetails web = User.builder()
                .username("webuser")
                .password(encoder.encode(webPassword))
                .roles("WEB")
                .build();

        UserDetails mobile = User.builder()
                .username("mobileuser")
                .password(encoder.encode(mobilePassword))
                .roles("MOBILE")
                .build();

        UserDetails atm = User.builder()
                .username("atmuser")
                .password(encoder.encode(atmPassword))
                .roles("ATM")
                .build();

        return new org.springframework.security.provisioning.InMemoryUserDetailsManager(web, mobile, atm);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ChannelAuthProperties channelAuthProperties) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(channelAuthProperties.pathPattern()).hasRole(channelAuthProperties.role())
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Credenciales requeridas o invalidas\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\"Usuario autenticado sin permisos para este canal\"}");
                        }));

        return http.build();
    }
}
