package cl.duoc.backendiii.mscustomer.service;

import cl.duoc.backendiii.mscustomer.model.CustomerProfileResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomerProfileResponse save(CustomerProfileResponse profile) {
        jdbcTemplate.update("""
                INSERT INTO customer_profiles(customer_id, full_name, email, phone, segment, status, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (customer_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    email = EXCLUDED.email,
                    phone = EXCLUDED.phone,
                    segment = EXCLUDED.segment,
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                """,
                profile.customerId(),
                profile.fullName(),
                profile.email(),
                profile.phone(),
                profile.segment(),
                profile.status(),
                Timestamp.valueOf(profile.updatedAt()));
        return profile;
    }

    public Optional<CustomerProfileResponse> findById(long customerId) {
        List<CustomerProfileResponse> profiles = jdbcTemplate.query("""
                SELECT customer_id, full_name, email, phone, segment, status, updated_at
                FROM customer_profiles
                WHERE customer_id = ?
                """, (rs, rowNum) -> mapProfile(
                rs.getLong("customer_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("segment"),
                rs.getString("status"),
                rs.getTimestamp("updated_at").toLocalDateTime()
        ), customerId);
        return profiles.stream().findFirst();
    }

    public List<CustomerProfileResponse> findRecent(int limit) {
        return jdbcTemplate.query("""
                SELECT customer_id, full_name, email, phone, segment, status, updated_at
                FROM customer_profiles
                ORDER BY updated_at DESC, customer_id
                LIMIT ?
                """, (rs, rowNum) -> mapProfile(
                rs.getLong("customer_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("segment"),
                rs.getString("status"),
                rs.getTimestamp("updated_at").toLocalDateTime()
        ), limit);
    }

    private CustomerProfileResponse mapProfile(long customerId,
                                               String fullName,
                                               String email,
                                               String phone,
                                               String segment,
                                               String status,
                                               LocalDateTime updatedAt) {
        return new CustomerProfileResponse(customerId, fullName, email, phone, segment, status, updatedAt);
    }
}
