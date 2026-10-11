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
    private final AccountLedger ledger;

    public AccountRepository(JdbcTemplate jdbcTemplate, AccountLedger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    public List<AccountSummaryResponse> findAccounts() {
        ledger.importLegacyAccounts();
        return jdbcTemplate.query(summarySelect() + " ORDER BY a.account_id", (rs, rowNum) -> new AccountSummaryResponse(
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
        ledger.importLegacyAccounts();
        List<AccountSummaryResponse> accounts = jdbcTemplate.query(summarySelect() + " WHERE a.account_id = ?",
                (rs, rowNum) -> new AccountSummaryResponse(
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
        ledger.importLegacyAccounts();
        List<AccountBalanceResponse> balances = jdbcTemplate.query("""
                SELECT account_id, customer_name, account_type, balance AS final_balance
                FROM bank_accounts
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
        ledger.importLegacyAccounts();
        return new AccountStatusResponse(
                count("bank_accounts"),
                count("annual_statement_entries"),
                count("daily_transaction_summary")
        );
    }

    private long count(String tableName) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return result == null ? 0 : result;
    }

    private String summarySelect() {
        if (ledger.hasLegacyData()) {
            return """
                    SELECT a.account_id, a.customer_name, a.account_type, a.age,
                           COALESCE(m.initial_balance, a.balance) AS initial_balance,
                           COALESCE(m.monthly_rate, 0) AS monthly_rate,
                           COALESCE(m.interest_amount, 0) AS interest_amount, a.balance AS final_balance
                    FROM bank_accounts a LEFT JOIN monthly_interest_results m ON a.account_id=m.account_id
                    """;
        }
        return """
                SELECT a.account_id, a.customer_name, a.account_type, a.age, a.balance AS initial_balance,
                       0 AS monthly_rate, 0 AS interest_amount, a.balance AS final_balance FROM bank_accounts a
                """;
    }
}
