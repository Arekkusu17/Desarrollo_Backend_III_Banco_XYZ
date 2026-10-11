package cl.duoc.backendiii.msaccount.controller;

import cl.duoc.backendiii.msaccount.config.SecurityConfig;
import cl.duoc.backendiii.msaccount.service.AccountLedger;
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

@WebMvcTest(value = AccountManagementController.class, properties = "spring.cloud.config.enabled=false")
@Import(SecurityConfig.class)
class AccountSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccountLedger ledger;
    @MockitoBean JwtDecoder decoder;

    @Test
    void rejectsAnonymousAndReadOnlyWrites() throws Exception {
        mvc.perform(post("/api/cuentas").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cuentas").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_account.read")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cuentas").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_account.write")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyPaymentServiceCanExecuteBalanceOperations() throws Exception {
        mvc.perform(post("/api/cuentas/operaciones")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_account.write")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cuentas/operaciones")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_account.execute")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNoContent());
    }
}
