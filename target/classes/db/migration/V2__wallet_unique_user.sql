ALTER TABLE wallets
    ADD CONSTRAINT uk_wallets_user_id UNIQUE (user_id);
