package cl.duoc.backendiii.mspayment.controller;

import cl.duoc.backendiii.mspayment.config.SecurityConfig;
import cl.duoc.backendiii.mspayment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PaymentController.class, properties = "spring.cloud.config.enabled=false")
@Import(SecurityConfig.class)
class PaymentSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentService service;
    @MockitoBean JwtDecoder decoder;

    @Test
    void separatesReadAndWriteScopes() throws Exception {
        mvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/deposits")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.read")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/deposits")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payment.write")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }
}
