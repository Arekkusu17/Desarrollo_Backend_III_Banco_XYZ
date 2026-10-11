package cl.duoc.backendiii.bff.atm.repository;

import cl.duoc.backendiii.bff.atm.model.WithdrawalTransaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;

@Repository
public class WithdrawalTransactionRepository {
    private final JdbcTemplate jdbc;

    public WithdrawalTransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WithdrawalTransaction save(WithdrawalTransaction transaction) {
        jdbc.update("""
                INSERT INTO atm_withdrawals(transaction_id, event_id, account_id, amount, created_at,
                                            status, message, authorization_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, transaction.getTransactionId(), transaction.getWithdrawalRequestedEventId(),
                transaction.getAccountId(), transaction.getAmount(), Timestamp.from(transaction.getCreatedAt()),
                transaction.getStatus().name(), transaction.getMessage(), transaction.getAuthorizationCode());
        jdbc.update("UPDATE atm_withdrawals SET status=?, message=?, authorization_code=? WHERE transaction_id=?",
                transaction.getStatus().name(), transaction.getMessage(), transaction.getAuthorizationCode(),
                transaction.getTransactionId());
        return transaction;
    }

    public Optional<WithdrawalTransaction> findById(UUID transactionId) {
        return jdbc.query("SELECT * FROM atm_withdrawals WHERE transaction_id=? FOR UPDATE",
                (rs, row) -> map(rs), transactionId).stream().findFirst();
    }

    public List<WithdrawalTransaction> findAll() {
        return jdbc.query("SELECT * FROM atm_withdrawals ORDER BY created_at DESC", (rs, row) -> map(rs));
    }

    private WithdrawalTransaction map(java.sql.ResultSet rs) throws java.sql.SQLException {
        var transaction = new WithdrawalTransaction((UUID) rs.getObject("transaction_id"),
                (UUID) rs.getObject("event_id"), rs.getLong("account_id"), rs.getBigDecimal("amount"),
                rs.getTimestamp("created_at").toInstant(),
                cl.duoc.backendiii.bff.atm.model.WithdrawalStatus.valueOf(rs.getString("status")),
                rs.getString("message"));
        transaction.restoreAuthorizationCode(rs.getString("authorization_code"));
        return transaction;
    }
}
