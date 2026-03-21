CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY,
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    transaction_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    security_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO user_preferences (user_id, email_notifications, transaction_notifications, security_alerts)
SELECT u.id, TRUE, TRUE, TRUE
FROM users u
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type CHECK (type IN ('CREDIT', 'DEBIT', 'LOGIN', 'SECURITY', 'WITHDRAWAL', 'SYSTEM'))
);

CREATE INDEX idx_notifications_user_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_is_read ON notifications(user_id, is_read);
