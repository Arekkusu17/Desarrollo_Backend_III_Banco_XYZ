package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.msaccount.model.AccountBalanceResponse;
import cl.duoc.backendiii.msaccount.model.AccountMovementResponse;
import cl.duoc.backendiii.msaccount.model.AccountOverviewResponse;
import cl.duoc.backendiii.msaccount.model.AccountStatusResponse;
import cl.duoc.backendiii.msaccount.model.AccountSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public List<AccountSummaryResponse> accounts() {
        return repository.findAccounts();
    }

    public Optional<AccountSummaryResponse> account(long accountId) {
        return repository.findAccount(accountId);
    }

    public Optional<AccountBalanceResponse> balance(long accountId) {
        return repository.findBalance(accountId);
    }

    public List<AccountMovementResponse> movements(long accountId, Integer limit) {
        return repository.findMovements(accountId, normalizeLimit(limit));
    }

    public Optional<AccountOverviewResponse> overview(long accountId) {
        return repository.findAccount(accountId)
                .map(account -> new AccountOverviewResponse(
                        account,
                        repository.findMovements(accountId, 10),
                        repository.findAnomalousTransactions(10)
                ));
    }

    public AccountStatusResponse status() {
        return repository.status();
    }

    int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
