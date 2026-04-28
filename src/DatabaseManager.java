import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all JDBC communication with the MySQL database.
 *
 * Responsibilities:
 * - Open / close connections
 * - CRUD for accounts table
 * - Insert / fetch for transactions table
 * - Atomic transfer using manual transaction control
 * (conn.setAutoCommit(false))
 *
 * Every public method opens its own connection and closes it in a
 * finally-block (via try-with-resources), so callers never need to
 * worry about leaking connections.
 */
public class DatabaseManager {

    // ── Connection details ────────────────────────────────────────────────────

    private static final String URL = "jdbc:mysql://localhost:3306/bank_db"
            + "?useSSL=false&serverTimezone=UTC"
            + "&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = System.getenv("DB_PASSWORD");
    // ── Connection factory ────────────────────────────────────────────────────

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // ── Schema bootstrap ──────────────────────────────────────────────────────

    /**
     * Creates the accounts and transactions tables if they don't already exist.
     * Called once at application startup.
     */
    public void initializeSchema() throws SQLException {
        String createAccounts = """
                CREATE TABLE IF NOT EXISTS accounts (
                    account_id  INT          PRIMARY KEY,
                    name        VARCHAR(100) NOT NULL,
                    balance     DECIMAL(15,2) NOT NULL DEFAULT 0.00
                )
                """;

        String createTransactions = """
                CREATE TABLE IF NOT EXISTS transactions (
                    id            INT AUTO_INCREMENT PRIMARY KEY,
                    account_id    INT          NOT NULL,
                    type          ENUM('CREDIT','DEBIT','TRANSFER_IN','TRANSFER_OUT') NOT NULL,
                    amount        DECIMAL(15,2) NOT NULL,
                    balance_after DECIMAL(15,2) NOT NULL,
                    note          VARCHAR(255),
                    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
                )
                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createAccounts);
            stmt.execute(createTransactions);
        }
    }

    // ── Account operations ────────────────────────────────────────────────────

    /**
     * Inserts a brand-new account row.
     * Also inserts an opening-deposit transaction if initialBalance > 0.
     */
    public void insertAccount(int accountId, String name, double initialBalance)
            throws SQLException {
        String sql = "INSERT INTO accounts (account_id, name, balance) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, name);
            ps.setDouble(3, initialBalance);
            ps.executeUpdate();
        }

        if (initialBalance > 0) {
            insertTransaction(accountId, Transaction.Type.CREDIT,
                    initialBalance, initialBalance, "Account opening deposit");
        }
    }

    /**
     * Fetches a single account by ID.
     * Returns null if not found.
     */
    public Account fetchAccount(int accountId) throws SQLException {
        String sql = "SELECT account_id, name, balance FROM accounts WHERE account_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getInt("account_id"),
                            rs.getString("name"),
                            rs.getDouble("balance"));
                }
            }
        }
        return null;
    }

    /**
     * Returns the highest existing account_id in the table, or 1000 if empty.
     * Used by Bank to seed nextAccountId correctly across restarts.
     */
    public int fetchMaxAccountId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(account_id), 1000) AS max_id FROM accounts";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt("max_id");
        }
    }

    /**
     * Updates the balance column for a single account.
     */
    public void updateBalance(int accountId, double newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    // ── Transfer (atomic) ─────────────────────────────────────────────────────

    /**
     * Executes a transfer atomically inside a single DB transaction.
     *
     * Both balance updates and both transaction-log inserts happen together.
     * If anything fails mid-way, the whole thing rolls back — no money lost.
     *
     * This solves the "atomicity problem" mentioned at the end of session 1.
     */
    public void transferAtomic(int fromId, double fromNewBalance,
            int toId, double toNewBalance,
            double amount) throws SQLException {
        String updateSql = "UPDATE accounts SET balance = ? WHERE account_id = ?";
        String txnSql = """
                INSERT INTO transactions (account_id, type, amount, balance_after, note)
                VALUES (?, ?, ?, ?, ?)
                """;

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // ← begin manual transaction

            // 1. Debit sender
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setDouble(1, fromNewBalance);
                ps.setInt(2, fromId);
                ps.executeUpdate();
            }

            // 2. Credit receiver
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setDouble(1, toNewBalance);
                ps.setInt(2, toId);
                ps.executeUpdate();
            }

            // 3. Log TRANSFER_OUT for sender
            try (PreparedStatement ps = conn.prepareStatement(txnSql)) {
                ps.setInt(1, fromId);
                ps.setString(2, "TRANSFER_OUT");
                ps.setDouble(3, amount);
                ps.setDouble(4, fromNewBalance);
                ps.setString(5, "Transfer OUT → Account #" + toId);
                ps.executeUpdate();
            }

            // 4. Log TRANSFER_IN for receiver
            try (PreparedStatement ps = conn.prepareStatement(txnSql)) {
                ps.setInt(1, toId);
                ps.setString(2, "TRANSFER_IN");
                ps.setDouble(3, amount);
                ps.setDouble(4, toNewBalance);
                ps.setString(5, "Transfer IN ← Account #" + fromId);
                ps.executeUpdate();
            }

            conn.commit(); // ← all four steps succeeded → commit

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            throw e; // re-throw so Bank can show the error
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
        }
    }

    // ── Transaction log operations ────────────────────────────────────────────

    /**
     * Inserts a single transaction row.
     */
    public void insertTransaction(int accountId, Transaction.Type type,
            double amount, double balanceAfter,
            String note) throws SQLException {
        String sql = """
                INSERT INTO transactions (account_id, type, amount, balance_after, note)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, type.name());
            ps.setDouble(3, amount);
            ps.setDouble(4, balanceAfter);
            ps.setString(5, note);
            ps.executeUpdate();
        }
    }

    /**
     * Fetches all transactions for an account, ordered oldest → newest.
     */
    public List<Transaction> fetchTransactions(int accountId) throws SQLException {
        String sql = """
                SELECT type, amount, balance_after, note, created_at
                FROM   transactions
                WHERE  account_id = ?
                ORDER  BY id ASC
                """;
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction.Type type = Transaction.Type.valueOf(rs.getString("type"));
                    double amount = rs.getDouble("amount");
                    double balanceAfter = rs.getDouble("balance_after");
                    String note = rs.getString("note");
                    LocalDateTime date = rs.getTimestamp("created_at").toLocalDateTime();
                    list.add(new Transaction(type, amount, balanceAfter, note, date));
                }
            }
        }
        return list;
    }
}
