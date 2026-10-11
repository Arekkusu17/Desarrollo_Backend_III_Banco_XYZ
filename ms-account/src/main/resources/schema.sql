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

CREATE TABLE IF NOT EXISTS event_inbox (
    service_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    PRIMARY KEY (service_name, event_id)
);

CREATE TABLE IF NOT EXISTS event_outbox (
    service_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    topic VARCHAR(100) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (service_name, event_id)
);
