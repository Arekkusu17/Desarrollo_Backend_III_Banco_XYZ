CREATE TABLE IF NOT EXISTS customer_profiles (
    customer_id BIGINT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    segment VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
