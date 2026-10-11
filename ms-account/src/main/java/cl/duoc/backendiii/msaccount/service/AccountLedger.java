package cl.duoc.backendiii.msaccount.service;

import cl.duoc.backendiii.msaccount.model.AccountOperation;
import cl.duoc.backendiii.msaccount.model.AccountRequest;
import cl.duoc.backendiii.msaccount.model.ManagedAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AccountLedger {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public AccountLedger(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.mapper = mapper;
    }

    public void importLegacyAccounts() {
        if (hasLegacyData()) {
            jdbc.update("""
                    INSERT INTO bank_accounts(account_id, customer_name, account_type, age, balance)
                    SELECT account_id, customer_name, account_type, age, final_balance
                    FROM monthly_interest_results
                    ON CONFLICT DO NOTHING
                    """);
        }
    }

    public boolean hasLegacyData() {
        Boolean exists = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var tables = connection.getMetaData().getTables(null, null, "monthly_interest_results", null)) {
                return tables.next();
            }
        });
        return Boolean.TRUE.equals(exists);
    }

    public ManagedAccount open(AccountRequest request) {
        validate(request);
        money(request.initialBalance(), true);
        importLegacyAccounts();
        return transactions.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO bank_accounts(account_id, customer_name, account_type, age, balance)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, request.accountId(), request.customerName().trim(), request.accountType().trim(),
                    request.age(), request.initialBalance());
            if (inserted == 0) {
                throw error(HttpStatus.CONFLICT, "La cuenta ya existe");
            }
            return find(request.accountId(), false);
        });
    }

    public ManagedAccount maintain(long id, AccountRequest request) {
        validate(request);
        if (request.accountId() != id) {
            throw error(HttpStatus.BAD_REQUEST, "El identificador de la cuenta no coincide");
        }
        importLegacyAccounts();
        return transactions.execute(status -> {
            ManagedAccount account = find(id, true);
            requireActive(account);
            jdbc.update("UPDATE bank_accounts SET customer_name=?, account_type=?, age=? WHERE account_id=?",
                    request.customerName().trim(), request.accountType().trim(), request.age(), id);
            return find(id, false);
        });
    }

    public ManagedAccount close(long id) {
        importLegacyAccounts();
        return transactions.execute(status -> {
            ManagedAccount account = find(id, true);
            Integer pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM account_reservations WHERE account_id=? AND status='RESERVED'",
                    Integer.class, id);
            if (account.balance().signum() != 0 || pending != null && pending > 0) {
                throw error(HttpStatus.CONFLICT, "La cuenta debe tener saldo cero y no tener reservas");
            }
            jdbc.update("UPDATE bank_accounts SET status='CLOSED' WHERE account_id=?", id);
            return find(id, false);
        });
    }

    public ManagedAccount details(long id) {
        importLegacyAccounts();
        return find(id, false);
    }

    public BigDecimal reserve(java.util.UUID transactionId, long accountId, BigDecimal amount) {
        money(amount, false);
        ManagedAccount account = find(accountId, true);
        requireActive(account);
        var existing = jdbc.query("SELECT amount FROM account_reservations WHERE transaction_id=?",
                (rs, row) -> rs.getBigDecimal(1), transactionId);
        if (!existing.isEmpty()) return account.balance();
        if (account.balance().compareTo(amount) < 0) throw error(HttpStatus.CONFLICT, "Saldo insuficiente");
        jdbc.update("UPDATE bank_accounts SET balance=balance-? WHERE account_id=?", amount, accountId);
        jdbc.update("INSERT INTO account_reservations(transaction_id, account_id, amount, status) VALUES (?, ?, ?, 'RESERVED')",
                transactionId, accountId, amount);
        return account.balance().subtract(amount);
    }

    public void release(java.util.UUID transactionId, long accountId, BigDecimal amount) {
        find(accountId, true);
        int released = jdbc.update("""
                UPDATE account_reservations SET status='RELEASED'
                WHERE transaction_id=? AND account_id=? AND amount=? AND status='RESERVED'
                """, transactionId, accountId, amount);
        if (released > 0) jdbc.update("UPDATE bank_accounts SET balance=balance+? WHERE account_id=?", amount, accountId);
    }

    public void confirm(java.util.UUID transactionId) {
        jdbc.update("UPDATE account_reservations SET status='CONFIRMED' WHERE transaction_id=? AND status='RESERVED'",
                transactionId);
    }

    public void apply(AccountOperation operation) {
        if (operation == null || operation.operationId() == null || operation.type() == null) {
            throw error(HttpStatus.BAD_REQUEST, "Operacion e identificador requeridos");
        }
        money(operation.amount(), false);
        if (!List.of("DEPOSIT", "PAYMENT", "TRANSFER").contains(operation.type())) {
            throw error(HttpStatus.BAD_REQUEST, "Tipo de operacion invalido");
        }
        Long source = operation.sourceAccountId();
        Long target = operation.targetAccountId();
        if ((!operation.type().equals("DEPOSIT") && (source == null || source <= 0))
                || (!operation.type().equals("PAYMENT") && (target == null || target <= 0))
                || operation.type().equals("TRANSFER") && Objects.equals(source, target)) {
            throw error(HttpStatus.BAD_REQUEST, "Cuentas de origen y destino invalidas");
        }
        importLegacyAccounts();
        String payload = serialize(operation);
        transactions.executeWithoutResult(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO account_operations(operation_id, request_payload) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, operation.operationId(), payload);
            if (inserted == 0) {
                String previous = jdbc.queryForObject(
                        "SELECT request_payload FROM account_operations WHERE operation_id=?",
                        String.class, operation.operationId());
                if (!payload.equals(previous)) {
                    throw error(HttpStatus.CONFLICT, "Identificador reutilizado con otra operacion");
                }
                return;
            }
            // A consistent lock order prevents opposite transfers from deadlocking.
            List<Long> ids = new ArrayList<>();
            if (!operation.type().equals("DEPOSIT")) ids.add(source);
            if (!operation.type().equals("PAYMENT")) ids.add(target);
            ids.stream().sorted().forEach(id -> requireActive(find(id, true)));
            if (!operation.type().equals("DEPOSIT")) {
                int debited = jdbc.update("""
                        UPDATE bank_accounts SET balance=balance-?
                        WHERE account_id=? AND balance>=? AND status='ACTIVE'
                        """, operation.amount(), source, operation.amount());
                if (debited == 0) throw error(HttpStatus.CONFLICT, "Saldo insuficiente");
            }
            if (!operation.type().equals("PAYMENT")) {
                jdbc.update("UPDATE bank_accounts SET balance=balance+? WHERE account_id=?",
                        operation.amount(), target);
            }
        });
    }

    private ManagedAccount find(long id, boolean lock) {
        var accounts = jdbc.query("SELECT * FROM bank_accounts WHERE account_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new ManagedAccount(rs.getLong("account_id"), rs.getString("customer_name"),
                        rs.getString("account_type"), rs.getInt("age"), rs.getBigDecimal("balance"),
                        rs.getString("status")), id);
        if (accounts.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Cuenta no existe");
        return accounts.getFirst();
    }

    private void requireActive(ManagedAccount account) {
        if (!account.status().equals("ACTIVE")) throw error(HttpStatus.CONFLICT, "Cuenta cerrada");
    }

    private void validate(AccountRequest request) {
        if (request == null || request.accountId() == null || request.accountId() <= 0
                || request.customerName() == null || request.customerName().isBlank()
                || request.customerName().trim().length() > 120
                || request.accountType() == null
                || !List.of("ahorro", "corriente").contains(request.accountType())
                || request.age() == null || request.age() < 18 || request.age() > 120) {
            throw error(HttpStatus.BAD_REQUEST, "Datos de cuenta invalidos");
        }
    }

    private void money(BigDecimal amount, boolean allowZero) {
        if (amount == null || amount.signum() < 0 || !allowZero && amount.signum() == 0
                || amount.stripTrailingZeros().scale() > 2 || amount.precision() - amount.scale() > 16) {
            throw error(HttpStatus.BAD_REQUEST, "Monto invalido; usar hasta dos decimales");
        }
    }

    private String serialize(AccountOperation operation) {
        try {
            return mapper.writeValueAsString(operation);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
