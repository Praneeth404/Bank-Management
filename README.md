# Bank Management System

A menu-driven command-line banking application built in **Java 25** with **MySQL** persistence. The project covers core OOP principles, JDBC, custom exceptions, atomic transfers, and an in-memory caching layer — all without any external frameworks.

---

## Features

- Create accounts with an optional opening balance
- Deposit and withdraw money, with insufficient balance protection
- Transfer money between accounts atomically — all-or-nothing at the database level
- View account details fetched live from MySQL
- View full transaction history with timestamps and running balance after each entry
- In-memory account cache to avoid redundant database queries within a session
- Input validation throughout — negative amounts, empty names, and invalid IDs are all handled

---

## Project Structure

```
BankManagementSystem/
├── src/
│   ├── Main.java                         ← Entry point, CLI menu loop
│   ├── Bank.java                         ← Core engine, in-memory cache
│   ├── Account.java                      ← Account entity, balance logic
│   ├── Transaction.java                  ← Transaction record (type, amount, date)
│   ├── DatabaseManager.java              ← All JDBC / SQL operations
│   └── InsufficientBalanceException.java ← Custom checked exception
├── lib/
│   └── mysql-connector-j-9.7.0.jar       ← MySQL JDBC driver
├── out/                                  ← Compiled .class files (auto-generated)
├── schema.sql                            ← Run once to set up the database
├── run.sh                                ← Compile and run script
└── README.md
```

---

## Database Setup

```bash
mysql -u root -p < schema.sql
```

This creates `bank_db` with an `accounts` table, a `transactions` table, and an `account_summary` view that can be useful for inspecting data directly in MySQL.

---

## Running the Application

```bash
chmod +x run.sh
./run.sh
```

The script checks that `javac` and the connector JAR are present, compiles all source files, and starts the application. Pass `build` as an argument to compile without running:

```bash
./run.sh build
```

To compile and run manually without the script:

```bash
mkdir -p out
javac -cp lib/mysql-connector-j-9.7.0.jar -d out src/*.java
java -Dfile.encoding=UTF-8 -cp "out:lib/mysql-connector-j-9.7.0.jar" Main
```

On Windows with Git Bash, replace `:` with `;` as the classpath separator in the manual command. The `run.sh` script handles this automatically.

---

## Sample Session

```
  ╔══════════════════════════════════════════╗
  ║      JAVA BANK MANAGEMENT SYSTEM         ║
  ║         Built with Java 25 + OOP         ║
  ╚══════════════════════════════════════════╝

  Connecting to database...
  Database ready.

┌─────────────────────────────┐
│          MAIN MENU          │
├─────────────────────────────┤
│  1. Create Account          │
│  2. Deposit Money           │
│  3. Withdraw Money          │
│  4. Transfer Money          │
│  5. View Account Details    │
│  6. View Transaction History│
│  7. Exit                    │
└─────────────────────────────┘
  Choose an option (1-7): 1

  -- Create New Account --
  Enter account holder name : Praneeth
  Enter initial deposit (0 for none): 5000

  Account created successfully!
  ┌─────────────────────────────────────────┐
  │  Account ID  : 1001                     │
  │  Name        : Praneeth                 │
  │  Balance     : 5000.00                  │
  └─────────────────────────────────────────┘

  Choose an option (1-7): 6
  Enter Account ID: 1001

  Transaction History - Account #1001 (Praneeth)
  ──────────────────────────────────────────────────────────────────────────────────────────
  [28-04-2025 14:22:01]  CREDIT          +5000.00    Balance: 5000.00  | Account opening deposit
  [28-04-2025 14:23:10]  DEBIT           -1200.00    Balance: 3800.00  | Withdrawal
  [28-04-2025 14:24:05]  TRANSFER_OUT    -500.00     Balance: 3300.00  | Transfer OUT -> Account #1002
  ──────────────────────────────────────────────────────────────────────────────────────────
```

---

## Architecture

```
Main  ->  Bank  ->  Account
                       |
               DatabaseManager  ->  MySQL (bank_db)
```

`Bank` is the orchestrator. It holds an in-memory `HashMap` cache of `Account` objects and delegates all persistence to `DatabaseManager`. `Account` contains the balance logic and holds a reference to `DatabaseManager` so it can persist changes directly after each operation. `Main` handles only the CLI loop and user input — it has no knowledge of SQL or balance rules.

---

## How Atomic Transfers Work

A naive transfer would call withdraw on the sender and deposit on the receiver as two separate database writes. If anything fails between them, money disappears from one account without arriving in the other.

This is solved using JDBC manual transaction control in `DatabaseManager.transferAtomic()`. All four statements — two balance updates and two transaction log inserts — are wrapped in a single database transaction:

```java
conn.setAutoCommit(false);
// 1. UPDATE sender balance
// 2. UPDATE receiver balance
// 3. INSERT TRANSFER_OUT log entry
// 4. INSERT TRANSFER_IN log entry
conn.commit();
// On any failure, the catch block calls conn.rollback()
```

Either all four succeed together, or none of them do.

---

## Database Schema

```sql
CREATE TABLE accounts (
    account_id  INT            PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL,
    balance     DECIMAL(15,2)  NOT NULL DEFAULT 0.00,
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    account_id    INT            NOT NULL,
    type          ENUM('CREDIT','DEBIT','TRANSFER_IN','TRANSFER_OUT') NOT NULL,
    amount        DECIMAL(15,2)  NOT NULL,
    balance_after DECIMAL(15,2)  NOT NULL,
    note          VARCHAR(255),
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);
```

---

## Concepts Covered

| Concept                  | Where                                                                    |
| ------------------------ | ------------------------------------------------------------------------ |
| Encapsulation            | `Account` — private fields accessed through public methods               |
| Custom checked exception | `InsufficientBalanceException extends Exception`                         |
| Collections              | `HashMap<Integer, Account>` as an in-memory cache in `Bank`              |
| JDBC                     | `DatabaseManager` — `PreparedStatement`, `ResultSet`, `Connection`       |
| Atomic DB transactions   | `transferAtomic()` with `setAutoCommit(false)`, `commit()`, `rollback()` |
| Separation of concerns   | All SQL is isolated in `DatabaseManager`                                 |
| Enum                     | `Transaction.Type` — `CREDIT`, `DEBIT`, `TRANSFER_IN`, `TRANSFER_OUT`    |
| Switch expressions       | Arrow syntax in `Main`                                                   |
| Cache pattern            | `Bank.loadAccount()` checks the `HashMap` before querying the database   |
