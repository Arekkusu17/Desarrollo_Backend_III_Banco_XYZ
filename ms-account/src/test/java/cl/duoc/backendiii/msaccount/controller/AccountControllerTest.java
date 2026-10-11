package cl.duoc.backendiii.msaccount.controller;

import cl.duoc.backendiii.msaccount.model.AccountBalanceResponse;
import cl.duoc.backendiii.msaccount.model.AccountStatusResponse;
import cl.duoc.backendiii.msaccount.service.AccountService;
import cl.duoc.backendiii.msaccount.service.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AccountControllerTest {

    private final FakeAccountService service = new FakeAccountService();
    private final AccountController controller = new AccountController(service);

    @Test
    void returnsBalanceWhenAccountExists() {
        service.balance = new AccountBalanceResponse(
                101L,
                "Alex Fernandez",
                "ahorro",
                BigDecimal.valueOf(100500));

        var response = controller.balance(101L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().availableBalance()).isEqualByComparingTo("100500");
    }

    @Test
    void returnsNotFoundWhenAccountDoesNotExist() {
        var response = controller.balance(404L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static class FakeAccountService extends AccountService {
        private AccountBalanceResponse balance;

        FakeAccountService() {
            super(new NoopAccountRepository());
        }

        @Override
        public Optional<AccountBalanceResponse> balance(long accountId) {
            return Optional.ofNullable(balance);
        }
    }

    private static class NoopAccountRepository extends AccountRepository {
        NoopAccountRepository() {
            super(null, null);
        }

        @Override
        public AccountStatusResponse status() {
            return new AccountStatusResponse(0, 0, 0);
        }
    }
}
