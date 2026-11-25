CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(255),
    reference_id VARCHAR(64) NOT NULL,
    timestamp TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_transactions_account_ts ON transactions (account_id, timestamp);
CREATE INDEX idx_transactions_type ON transactions (type);
CREATE INDEX idx_transactions_reference ON transactions (reference_id);
