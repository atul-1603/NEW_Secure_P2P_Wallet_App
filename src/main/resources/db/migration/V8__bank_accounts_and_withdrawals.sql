CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_holder_name VARCHAR(120) NOT NULL,
    account_number VARCHAR(512) NOT NULL,
    ifsc_code VARCHAR(11) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_bank_accounts_ifsc CHECK (ifsc_code ~ '^[A-Z]{4}0[A-Z0-9]{6}$')
);

CREATE TABLE withdrawal_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    bank_account_id UUID NOT NULL,
    reference_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT fk_withdrawal_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_withdrawal_requests_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_withdrawal_requests_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_withdrawal_requests_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_bank_accounts_user_id ON bank_accounts(user_id);
CREATE INDEX idx_withdrawal_requests_user_id ON withdrawal_requests(user_id);
CREATE INDEX idx_withdrawal_requests_status ON withdrawal_requests(status);
CREATE INDEX idx_withdrawal_requests_created_at ON withdrawal_requests(created_at);
