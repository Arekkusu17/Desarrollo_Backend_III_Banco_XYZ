package cl.duoc.backendiii.bff.atm.repository;

import cl.duoc.backendiii.bff.atm.model.WithdrawalTransaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WithdrawalTransactionRepository {
    private final ConcurrentHashMap<UUID, WithdrawalTransaction> transactions = new ConcurrentHashMap<>();

    public WithdrawalTransaction save(WithdrawalTransaction transaction) {
        transactions.put(transaction.getTransactionId(), transaction);
        return transaction;
    }

    public Optional<WithdrawalTransaction> findById(UUID transactionId) {
        return Optional.ofNullable(transactions.get(transactionId));
    }

    public List<WithdrawalTransaction> findAll() {
        return transactions.values().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
    }
}
