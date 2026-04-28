import java.sql.SQLException;
import java.util.Scanner;

/**
 * Entry point for the Bank Management System.
 * Drives the menu-based CLI loop.
 */
public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        printBanner();

        // ── Connect to DB and bootstrap schema ────────────────────────────────
        DatabaseManager db = new DatabaseManager();
        try {
            System.out.println("    Connecting to database...");
            db.initializeSchema();
            System.out.println("    Database ready.\n");
        } catch (SQLException e) {
            System.out.println("     Could not connect to MySQL: " + e.getMessage());
            System.out.println("     Make sure MySQL is running and credentials in");
            System.out.println("     DatabaseManager.java are correct, then try again.");
            scanner.close();
            return;
        }

        Bank bank;
        try {
            bank = new Bank(db);
        } catch (SQLException e) {
            System.out.println("  ✖  Failed to initialise bank: " + e.getMessage());
            scanner.close();
            return;
        }

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readMenuChoice(scanner);

            switch (choice) {
                case 1 -> bank.createAccount(scanner);

                case 2 -> {
                    int id = Bank.readAccountId(scanner, "\n  Enter Account ID: ");
                    if (id == -1)
                        break;
                    double amount = Bank.readPositiveDouble(scanner, "  Enter deposit amount: \u20B9");
                    if (amount <= 0)
                        break;
                    bank.deposit(id, amount);
                }

                case 3 -> {
                    int id = Bank.readAccountId(scanner, "\n  Enter Account ID: ");
                    if (id == -1)
                        break;
                    double amount = Bank.readPositiveDouble(scanner, "  Enter withdrawal amount: \u20B9");
                    if (amount <= 0)
                        break;
                    bank.withdraw(id, amount);
                }

                case 4 -> {
                    System.out.println("\n  ── Transfer Money ──");
                    int fromId = Bank.readAccountId(scanner, "  Enter sender Account ID  : ");
                    if (fromId == -1)
                        break;
                    int toId = Bank.readAccountId(scanner, "  Enter receiver Account ID: ");
                    if (toId == -1)
                        break;
                    double amount = Bank.readPositiveDouble(scanner, "  Enter transfer amount: \u20B9");
                    if (amount <= 0)
                        break;
                    bank.transfer(fromId, toId, amount);
                }

                case 5 -> {
                    int id = Bank.readAccountId(scanner, "\n  Enter Account ID: ");
                    if (id == -1)
                        break;
                    bank.viewAccount(id);
                }

                case 6 -> {
                    int id = Bank.readAccountId(scanner, "\n  Enter Account ID: ");
                    if (id == -1)
                        break;
                    bank.viewTransactionHistory(id);
                }

                case 7 -> {
                    System.out.println("\n  Thank you for banking with us. Goodbye!\n");
                    running = false;
                }

                default -> System.out.println("\n   Invalid choice. Please select 1-7.");
            }
        }

        scanner.close();
    }

    // ── UI Helpers ───────────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔════════════════════════════════════════╗");
        System.out.println("  ║      JAVA BANK MANAGEMENT SYSTEM       ║");
        System.out.println("  ╚════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printMenu() {
        System.out.println("\n");
        System.out.println("  ┌─────────────────────────────┐");
        System.out.println("  │          MAIN MENU          │");
        System.out.println("  ├─────────────────────────────┤");
        System.out.println("  │  1. Create Account          │");
        System.out.println("  │  2. Deposit Money           │");
        System.out.println("  │  3. Withdraw Money          │");
        System.out.println("  │  4. Transfer Money          │");
        System.out.println("  │  5. View Account Details    │");
        System.out.println("  │  6. View Transaction History│");
        System.out.println("  │  7. Exit                    │");
        System.out.println("  └─────────────────────────────┘");
        System.out.print("  Choose an option (1-7): ");
    }

    private static int readMenuChoice(Scanner scanner) {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
