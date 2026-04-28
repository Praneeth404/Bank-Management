-- ─────────────────────────────────────────────────────────────────────────────
--  Bank Management System — MySQL Schema
--  Run this ONCE before starting the application:
--
--    mysql -u root -p < schema.sql
--
-- ─────────────────────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS bank_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE bank_db;

-- ── Accounts ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS accounts (
    account_id  INT            NOT NULL,
    name        VARCHAR(100)   NOT NULL,
    balance     DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id)
);

-- ── Transactions ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS transactions (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    account_id    INT            NOT NULL,
    type          ENUM('CREDIT', 'DEBIT', 'TRANSFER_IN', 'TRANSFER_OUT') NOT NULL,
    amount        DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    note          VARCHAR(255),
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts (account_id)
        ON DELETE CASCADE
);

-- ── Useful views (optional but handy for debugging) ───────────────────────────

CREATE OR REPLACE VIEW account_summary AS
    SELECT
        a.account_id,
        a.name,
        a.balance,
        COUNT(t.id)  AS total_transactions,
        a.created_at AS opened_on
    FROM accounts a
    LEFT JOIN transactions t ON t.account_id = a.account_id
    GROUP BY a.account_id, a.name, a.balance, a.created_at;
