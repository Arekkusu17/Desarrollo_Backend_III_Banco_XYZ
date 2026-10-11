CREATE TABLE IF NOT EXISTS bank_accounts (
    account_id BIGINT PRIMARY KEY,
    customer_name VARCHAR(120) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    age INTEGER NOT NULL,
    balance NUMERIC(18, 2) NOT NULL CHECK (balance >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS account_operations (
    operation_id UUID PRIMARY KEY,
    request_payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_reservations (
    transaction_id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES bank_accounts(account_id),
    amount NUMERIC(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL
);
