import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

/**
 * Core banking engine.
 *
 * The HashMap still acts as an in-memory cache so we don't hit the DB
 * on every keystroke, but every mutation is immediately persisted to MySQL
 * through DatabaseManager.
 */
public class Bank {

    private final HashMap<Integer, Account> accounts; // in-memory cache
    private final DatabaseManager db;
    private int nextAccountId;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Bank(DatabaseManager db) throws SQLException {
        this.db = db;
        this.accounts = new HashMap<>();
        // Resume from whatever the DB already contains across restarts
        this.nextAccountId = db.fetchMaxAccountId() + 1;
    }

    // ── Account Management ────────────────────────────────────────────────────

    public void createAccount(Scanner scanner) {
        System.out.println("\n  ── Create New Account ──");

        System.out.print("  Enter account holder name : ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("  Name cannot be empty.");
            return;
        }

        double initialBalance = readPositiveDouble(scanner, "  Enter initial deposit (0 for none): ₹");
        if (initialBalance < 0)
            return;

        try {
            Account account = new Account(nextAccountId, name, initialBalance, db);
            db.insertAccount(nextAccountId, name, initialBalance); // persist to DB
            accounts.put(nextAccountId, account); // cache in memory

            System.out.printf("%n  Account created successfully!%n");
            account.printDetails();
            nextAccountId++;
        } catch (SQLException e) {
            System.out.println("  Database error while creating account: " + e.getMessage());
        }
    }

    // ── Deposits ──────────────────────────────────────────────────────────────

    public void deposit(int accountId, double amount) {
        Account account = loadAccount(accountId);
        if (account == null)
            return;

        try {
            account.deposit(amount); // updates DB + in-memory balance
            System.out.printf(
                    "  Deposited ₹%.2f into Account #%d (%s). New balance: ₹%.2f%n",
                    amount, accountId, account.getName(), account.getBalance());
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("  Database error: " + e.getMessage());
        }
    }

    // ── Withdrawals ───────────────────────────────────────────────────────────

    public void withdraw(int accountId, double amount) {
        Account account = loadAccount(accountId);
        if (account == null)
            return;

        try {
            account.withdraw(amount); // updates DB + in-memory balance
            System.out.printf(
                    "  Withdrew ₹%.2f from Account #%d (%s). New balance: ₹%.2f%n",
                    amount, accountId, account.getName(), account.getBalance());
        } catch (InsufficientBalanceException e) {
            System.out.println("  " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("  Database error: " + e.getMessage());
        }
    }

    // ── Transfers ─────────────────────────────────────────────────────────────

    public void transfer(int fromId, int toId, double amount) {
        if (fromId == toId) {
            System.out.println("  Cannot transfer to the same account.");
            return;
        }

        Account from = loadAccount(fromId);
        if (from == null)
            return;
        Account to = loadAccount(toId);
        if (to == null)
            return;

        try {
            // Validate balance and compute new values — no DB write yet
            double fromNewBalance = from.computeDebitForTransfer(amount);
            double toNewBalance = to.getBalance() + amount;

            // Single atomic DB transaction — all 4 SQL statements or none
            db.transferAtomic(fromId, fromNewBalance, toId, toNewBalance, amount);

            // Only update in-memory state after DB confirms success
            from.applyDebit(amount);
            to.applyCredit(amount);

            System.out.printf(
                    "  ✔  Transferred ₹%.2f from Account #%d (%s) → Account #%d (%s)%n",
                    amount, fromId, from.getName(), toId, to.getName());
            System.out.printf("     Sender balance  : ₹%.2f%n", from.getBalance());
            System.out.printf("     Receiver balance: ₹%.2f%n", to.getBalance());

        } catch (InsufficientBalanceException e) {
            System.out.println("  Transfer failed — " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("  Database error during transfer: " + e.getMessage());
        }
    }

    // ── View Operations ───────────────────────────────────────────────────────

    public void viewAccount(int accountId) {
        Account account = loadAccount(accountId);
        if (account == null)
            return;
        System.out.println();
        account.printDetails();
    }

    public void viewTransactionHistory(int accountId) {
        Account account = loadAccount(accountId);
        if (account == null)
            return;

        try {
            List<Transaction> transactions = db.fetchTransactions(accountId);
            account.printTransactionHistory(transactions);
        } catch (SQLException e) {
            System.out.println("  ✖  Database error fetching history: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns an Account from the in-memory cache if present,
     * otherwise fetches it from the DB and caches it.
     * Prints an error and returns null if the ID doesn't exist anywhere.
     */
    private Account loadAccount(int accountId) {
        if (accounts.containsKey(accountId)) {
            return accounts.get(accountId);
        }
        try {
            Account account = db.fetchAccount(accountId);
            if (account == null) {
                System.out.println("  ✖  No account found with ID: " + accountId);
                return null;
            }
            // Re-wrap with db reference so mutations can persist
            Account liveAccount = new Account(
                    account.getAccountId(), account.getName(), account.getBalance(), db);
            accounts.put(accountId, liveAccount);
            return liveAccount;
        } catch (SQLException e) {
            System.out.println("  ✖  Database error: " + e.getMessage());
            return null;
        }
    }

    // ── Static input helpers ──────────────────────────────────────────────────

    public static int readAccountId(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            int id = Integer.parseInt(scanner.nextLine().trim());
            if (id <= 0) {
                System.out.println("  ✖  Account ID must be a positive number.");
                return -1;
            }
            return id;
        } catch (NumberFormatException e) {
            System.out.println("  ✖  Invalid input. Please enter a numeric account ID.");
            return -1;
        }
    }

    public static double readPositiveDouble(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            double value = Double.parseDouble(scanner.nextLine().trim());
            if (value < 0) {
                System.out.println("  ✖  Amount cannot be negative.");
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("  ✖  Invalid amount. Please enter a valid number.");
            return -1;
        }
    }
}
