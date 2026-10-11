CREATE TABLE IF NOT EXISTS atm_withdrawals (
    transaction_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    account_id BIGINT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    message VARCHAR(255),
    authorization_code VARCHAR(120)
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
