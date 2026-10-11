package cl.duoc.backendiii.mspayment.service;

import cl.duoc.backendiii.mspayment.model.PaymentOperationResponse;
import cl.duoc.backendiii.mspayment.model.PaymentStatus;
import cl.duoc.backendiii.mspayment.model.PaymentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentOperationResponse save(PaymentOperationResponse operation) {
        jdbcTemplate.update("""
                INSERT INTO payment_operations(operation_id, operation_type, source_account_id,
                                               target_account_id, amount, status, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                operation.operationId(),
                operation.type().name(),
                operation.sourceAccountId(),
                operation.targetAccountId(),
                operation.amount(),
                operation.status().name(),
                operation.description(),
                Timestamp.valueOf(operation.createdAt()));
        return operation;
    }

    public Optional<PaymentOperationResponse> findById(UUID operationId) {
        List<PaymentOperationResponse> operations = jdbcTemplate.query("""
                SELECT operation_id, operation_type, source_account_id, target_account_id,
                       amount, status, description, created_at
                FROM payment_operations
                WHERE operation_id = ?
                """, (rs, rowNum) -> mapOperation(
                (UUID) rs.getObject("operation_id"),
                rs.getString("operation_type"),
                rs.getObject("source_account_id", Long.class),
                rs.getObject("target_account_id", Long.class),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), operationId);
        return operations.stream().findFirst();
    }

    public List<PaymentOperationResponse> findRecent(int limit) {
        return jdbcTemplate.query("""
                SELECT operation_id, operation_type, source_account_id, target_account_id,
                       amount, status, description, created_at
                FROM payment_operations
                ORDER BY created_at DESC
                LIMIT ?
                """, (rs, rowNum) -> mapOperation(
                (UUID) rs.getObject("operation_id"),
                rs.getString("operation_type"),
                rs.getObject("source_account_id", Long.class),
                rs.getObject("target_account_id", Long.class),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), limit);
    }

    private PaymentOperationResponse mapOperation(UUID operationId,
                                                  String type,
                                                  Long sourceAccountId,
                                                  Long targetAccountId,
                                                  java.math.BigDecimal amount,
                                                  String status,
                                                  String description,
                                                  LocalDateTime createdAt) {
        return new PaymentOperationResponse(
                operationId,
                PaymentType.valueOf(type),
                sourceAccountId,
                targetAccountId,
                amount,
                PaymentStatus.valueOf(status),
                description,
                createdAt);
    }
}
