package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.msaccount.model.AccountMovementResponse;
import cl.duoc.backendiii.msaccount.model.AccountStatusResponse;
import cl.duoc.backendiii.msaccount.model.AccountSummaryResponse;
import cl.duoc.backendiii.msaccount.model.DailyTransactionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceTest {

    private final FakeAccountRepository repository = new FakeAccountRepository();
    private final AccountService service = new AccountService(repository);

    @Test
    void clampsMovementLimitToSupportedRange() {
        service.movements(101L, 500);

        assertThat(repository.lastMovementLimit).isEqualTo(100);
    }

    @Test
    void usesDefaultMovementLimitWhenNotProvided() {
        service.movements(101L, null);

        assertThat(repository.lastMovementLimit).isEqualTo(20);
    }

    @Test
    void buildsOverviewFromAccountMovementsAndAnomalies() {
        AccountSummaryResponse account = new AccountSummaryResponse(
                101L,
                "Alex Fernandez",
                "ahorro",
                30,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(0.005),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(100500));
        repository.account = account;

        var overview = service.overview(101L);

        assertThat(overview).isPresent();
        assertThat(overview.orElseThrow().account()).isEqualTo(account);
    }

    private static class FakeAccountRepository extends AccountRepository {
        private AccountSummaryResponse account;
        private int lastMovementLimit;

        FakeAccountRepository() {
            super(null);
        }

        @Override
        public List<AccountSummaryResponse> findAccounts() {
            return account == null ? List.of() : List.of(account);
        }

        @Override
        public Optional<AccountSummaryResponse> findAccount(long accountId) {
            return Optional.ofNullable(account);
        }

        @Override
        public List<AccountMovementResponse> findMovements(long accountId, int limit) {
            lastMovementLimit = limit;
            return List.of();
        }

        @Override
        public List<DailyTransactionResponse> findAnomalousTransactions(int limit) {
            return List.of();
        }

        @Override
        public AccountStatusResponse status() {
            return new AccountStatusResponse(0, 0, 0);
        }
    }
}
