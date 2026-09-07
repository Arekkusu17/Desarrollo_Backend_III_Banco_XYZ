package cl.duoc.backendiii.bankbatch.service;

import cl.duoc.backendiii.bankbatch.model.AccountBalanceResponse;
import cl.duoc.backendiii.bankbatch.model.AccountMovementResponse;
import cl.duoc.backendiii.bankbatch.model.AccountSummaryResponse;
import cl.duoc.backendiii.bankbatch.model.CoreAccountOverviewResponse;
import cl.duoc.backendiii.bankbatch.model.CoreStatusResponse;
import cl.duoc.backendiii.bankbatch.model.DailyTransactionResponse;
import cl.duoc.backendiii.bankbatch.model.RejectedRecordResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BankCoreService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final BankCoreRepository repository;

    public BankCoreService(BankCoreRepository repository) {
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

    public Optional<CoreAccountOverviewResponse> overview(long accountId) {
        return repository.findAccount(accountId)
                .map(account -> new CoreAccountOverviewResponse(
                        account,
                        repository.findMovements(accountId, 10),
                        repository.findDailyTransactions(true, 10)
                ));
    }

    public List<DailyTransactionResponse> dailyTransactions(boolean onlyAnomalies, Integer limit) {
        return repository.findDailyTransactions(onlyAnomalies, normalizeLimit(limit));
    }

    public List<RejectedRecordResponse> rejectedRecords(Integer limit) {
        return repository.findRejectedRecords(normalizeLimit(limit));
    }

    public CoreStatusResponse status() {
        return repository.status();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}

