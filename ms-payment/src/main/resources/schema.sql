CREATE TABLE IF NOT EXISTS payment_operations (
    operation_id UUID PRIMARY KEY,
    operation_type VARCHAR(20) NOT NULL,
    source_account_id BIGINT,
    target_account_id BIGINT,
    amount NUMERIC(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
