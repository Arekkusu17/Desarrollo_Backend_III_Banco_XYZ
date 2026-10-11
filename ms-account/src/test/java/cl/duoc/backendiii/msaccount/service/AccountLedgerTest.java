package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.msaccount.model.AccountOperation;
import cl.duoc.backendiii.msaccount.model.AccountRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AccountLedgerTest {
    private AccountLedger ledger;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName(UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .addScript("schema.sql").build();
        jdbc = new JdbcTemplate(dataSource);
        ledger = new AccountLedger(jdbc, new DataSourceTransactionManager(dataSource), new ObjectMapper());
        ledger.open(new AccountRequest(101L, "Alex", "ahorro", 30, new BigDecimal("100")));
        ledger.open(new AccountRequest(102L, "Ana", "corriente", 25, BigDecimal.ZERO));
    }

    @Test
    void transfersAtomicallyAndOnlyOnce() {
        var operation = new AccountOperation(UUID.randomUUID(), "TRANSFER", 101L, 102L,
                new BigDecimal("25.00"), "test");
        ledger.apply(operation);
        ledger.apply(operation);
        assertThat(ledger.details(101).balance()).isEqualByComparingTo("75");
        assertThat(ledger.details(102).balance()).isEqualByComparingTo("25");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_operations", Integer.class)).isEqualTo(1);
    }

    @Test
    void insufficientFundsLeaveBothBalancesAndLedgerUnchanged() {
        var operation = new AccountOperation(UUID.randomUUID(), "TRANSFER", 101L, 102L,
                new BigDecimal("101"), "test");
        assertThatThrownBy(() -> ledger.apply(operation)).isInstanceOf(ResponseStatusException.class);
        assertThat(ledger.details(101).balance()).isEqualByComparingTo("100");
        assertThat(ledger.details(102).balance()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_operations", Integer.class)).isZero();
    }

    @Test
    void missingDestinationRollsBackDebit() {
        assertThatThrownBy(() -> ledger.apply(new AccountOperation(UUID.randomUUID(), "TRANSFER",
                101L, 999L, BigDecimal.TEN, "test"))).isInstanceOf(ResponseStatusException.class);
        assertThat(ledger.details(101).balance()).isEqualByComparingTo("100");
    }

    @Test
    void closesOnlyEmptyAccountsAndRejectsFurtherDeposits() {
        assertThatThrownBy(() -> ledger.close(101)).isInstanceOf(ResponseStatusException.class);
        assertThat(ledger.close(102).status()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> ledger.apply(new AccountOperation(UUID.randomUUID(), "DEPOSIT",
                null, 102L, BigDecimal.TEN, "test"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void maintenanceDoesNotChangeBalance() {
        ledger.maintain(101, new AccountRequest(101L, "Alex Fernandez", "corriente", 31, BigDecimal.ZERO));
        assertThat(ledger.details(101).balance()).isEqualByComparingTo("100");
        assertThat(ledger.details(101).customerName()).isEqualTo("Alex Fernandez");
    }

    @Test
    void rejectsIdempotencyKeyReuseAndFractionalCents() {
        UUID id = UUID.randomUUID();
        ledger.apply(new AccountOperation(id, "DEPOSIT", null, 102L, BigDecimal.TEN, "test"));
        assertThatThrownBy(() -> ledger.apply(new AccountOperation(id, "DEPOSIT", null, 102L,
                BigDecimal.ONE, "test"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ledger.apply(new AccountOperation(UUID.randomUUID(), "PAYMENT", 101L,
                null, new BigDecimal("0.001"), "test"))).isInstanceOf(ResponseStatusException.class);
    }
}
