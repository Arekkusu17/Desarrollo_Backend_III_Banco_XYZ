CREATE TABLE IF NOT EXISTS daily_transaction_summary (
    transaction_id BIGINT PRIMARY KEY,
    transaction_date DATE NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    anomaly BOOLEAN NOT NULL,
    anomaly_reason VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS monthly_interest_results (
    account_id BIGINT PRIMARY KEY,
    customer_name VARCHAR(120) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    age INTEGER NOT NULL,
    initial_balance NUMERIC(18, 2) NOT NULL,
    monthly_rate NUMERIC(8, 4) NOT NULL,
    interest_amount NUMERIC(18, 2) NOT NULL,
    final_balance NUMERIC(18, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS annual_statement_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_date DATE NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    audit_flag VARCHAR(80) NOT NULL,
    CONSTRAINT annual_statement_unique UNIQUE (account_id, transaction_date, transaction_type, amount, description)
);

CREATE TABLE IF NOT EXISTS rejected_records (
    id BIGSERIAL PRIMARY KEY,
    process_name VARCHAR(80) NOT NULL,
    record_key VARCHAR(120) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
