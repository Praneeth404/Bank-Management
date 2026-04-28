import java.sql.SQLException;
import java.util.List;

/**
 * Represents a bank account.
 *
 * All balance-mutating operations update both the in-memory state
 * AND the MySQL database through DatabaseManager.
 *
 * Two constructors:
 *   Account(id, name, initialBalance, db)  → brand-new account (written to DB by Bank)
 *   Account(id, name, balance)             → reconstruction from a DB row (no DB write)
 */
public class Account {

    private final int             accountId;
    private final String          name;
    private double                balance;
    private final DatabaseManager db;          // null only for DB-reconstructed instances

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Used when creating a brand-new account via the CLI.
     * The caller (Bank) is responsible for persisting this account to the DB.
     */
    public Account(int accountId, String name, double initialBalance, DatabaseManager db) {
        if (initialBalance < 0)
            throw new IllegalArgumentException("Initial balance cannot be negative.");
        this.accountId = accountId;
        this.name      = name;
        this.balance   = initialBalance;
        this.db        = db;
    }

    /**
     * Used by DatabaseManager.fetchAccount() to rebuild an Account from a DB row.
     * No DB write happens here — the data is already persisted.
     * db is set to null because fetched accounts are short-lived (used for display only).
     */
    public Account(int accountId, String name, double balance) {
        this.accountId = accountId;
        this.name      = name;
        this.balance   = balance;
        this.db        = null;
    }

    // ── Core Operations ──────────────────────────────────────────────────────

    /**
     * Deposits amount into this account and persists the change.
     */
    public void deposit(double amount) throws SQLException {
        if (amount <= 0)
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");

        balance += amount;
        db.updateBalance(accountId, balance);
        db.insertTransaction(accountId, Transaction.Type.CREDIT, amount, balance, "Deposit");
    }

    /**
     * Withdraws amount from this account and persists the change.
     *
     * @throws InsufficientBalanceException if balance < amount
     */
    public void withdraw(double amount) throws InsufficientBalanceException, SQLException {
        if (amount <= 0)
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        if (amount > balance)
            throw new InsufficientBalanceException(balance, amount);

        balance -= amount;
        db.updateBalance(accountId, balance);
        db.insertTransaction(accountId, Transaction.Type.DEBIT, amount, balance, "Withdrawal");
    }

    /**
     * Computes the post-debit balance for a transfer WITHOUT touching the DB.
     * The actual DB write is done atomically by DatabaseManager.transferAtomic().
     *
     * @throws InsufficientBalanceException if balance < amount
     */
    double computeDebitForTransfer(double amount) throws InsufficientBalanceException {
        if (amount <= 0)
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        if (amount > balance)
            throw new InsufficientBalanceException(balance, amount);
        return balance - amount;
    }

    /** Applies the result of a completed transfer to this in-memory object. */
    void applyDebit(double amount)  { balance -= amount; }
    void applyCredit(double amount) { balance += amount; }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int    getAccountId() { return accountId; }
    public String getName()      { return name; }
    public double getBalance()   { return balance; }

    // ── Display ──────────────────────────────────────────────────────────────

    public void printDetails() {
        System.out.println("┌─────────────────────────────────────────┐");
        System.out.printf( "│  Account ID  : %-26d│%n", accountId);
        System.out.printf( "│  Name        : %-26s│%n", name);
        System.out.printf( "│  Balance     : ₹%-25.2f│%n", balance);
        System.out.println("└─────────────────────────────────────────┘");
    }

    public void printTransactionHistory(List<Transaction> transactions) {
        System.out.println("\n  Transaction History — Account #" + accountId + " (" + name + ")");
        System.out.println("  " + "─".repeat(90));
        if (transactions.isEmpty()) {
            System.out.println("  No transactions found.");
        } else {
            transactions.forEach(System.out::println);
        }
        System.out.println("  " + "─".repeat(90));
    }
}
