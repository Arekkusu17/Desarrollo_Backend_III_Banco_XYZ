package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.msaccount.model.AccountBalanceResponse;
import cl.duoc.backendiii.msaccount.model.AccountMovementResponse;
import cl.duoc.backendiii.msaccount.model.AccountStatusResponse;
import cl.duoc.backendiii.msaccount.model.AccountSummaryResponse;
import cl.duoc.backendiii.msaccount.model.DailyTransactionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AccountSummaryResponse> findAccounts() {
        return jdbcTemplate.query("""
                SELECT account_id, customer_name, account_type, age, initial_balance,
                       monthly_rate, interest_amount, final_balance
                FROM monthly_interest_results
                ORDER BY account_id
                """, (rs, rowNum) -> new AccountSummaryResponse(
                rs.getLong("account_id"),
                rs.getString("customer_name"),
                rs.getString("account_type"),
                rs.getInt("age"),
                rs.getBigDecimal("initial_balance"),
                rs.getBigDecimal("monthly_rate"),
                rs.getBigDecimal("interest_amount"),
                rs.getBigDecimal("final_balance")
        ));
    }

    public Optional<AccountSummaryResponse> findAccount(long accountId) {
        List<AccountSummaryResponse> accounts = jdbcTemplate.query("""
                SELECT account_id, customer_name, account_type, age, initial_balance,
                       monthly_rate, interest_amount, final_balance
                FROM monthly_interest_results
                WHERE account_id = ?
                """, (rs, rowNum) -> new AccountSummaryResponse(
                rs.getLong("account_id"),
                rs.getString("customer_name"),
                rs.getString("account_type"),
                rs.getInt("age"),
                rs.getBigDecimal("initial_balance"),
                rs.getBigDecimal("monthly_rate"),
                rs.getBigDecimal("interest_amount"),
                rs.getBigDecimal("final_balance")
        ), accountId);
        return accounts.stream().findFirst();
    }

    public Optional<AccountBalanceResponse> findBalance(long accountId) {
        List<AccountBalanceResponse> balances = jdbcTemplate.query("""
                SELECT account_id, customer_name, account_type, final_balance
                FROM monthly_interest_results
                WHERE account_id = ?
                """, (rs, rowNum) -> new AccountBalanceResponse(
                rs.getLong("account_id"),
                rs.getString("customer_name"),
                rs.getString("account_type"),
                rs.getBigDecimal("final_balance")
        ), accountId);
        return balances.stream().findFirst();
    }

    public List<AccountMovementResponse> findMovements(long accountId, int limit) {
        return jdbcTemplate.query("""
                SELECT account_id, transaction_date, transaction_type, amount, description, audit_flag
                FROM annual_statement_entries
                WHERE account_id = ?
                ORDER BY transaction_date DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new AccountMovementResponse(
                rs.getLong("account_id"),
                rs.getDate("transaction_date").toLocalDate(),
                rs.getString("transaction_type"),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getString("audit_flag")
        ), accountId, limit);
    }

    public List<DailyTransactionResponse> findAnomalousTransactions(int limit) {
        return jdbcTemplate.query("""
                SELECT transaction_id, transaction_date, amount, transaction_type, anomaly, anomaly_reason
                FROM daily_transaction_summary
                WHERE anomaly = true
                ORDER BY transaction_date DESC, transaction_id DESC
                LIMIT ?
                """, (rs, rowNum) -> new DailyTransactionResponse(
                rs.getLong("transaction_id"),
                rs.getDate("transaction_date").toLocalDate(),
                rs.getBigDecimal("amount"),
                rs.getString("transaction_type"),
                rs.getBoolean("anomaly"),
                rs.getString("anomaly_reason")
        ), limit);
    }

    public AccountStatusResponse status() {
        return new AccountStatusResponse(
                count("monthly_interest_results"),
                count("annual_statement_entries"),
                count("daily_transaction_summary")
        );
    }

    private long count(String tableName) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return result == null ? 0 : result;
    }
}
