import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single financial transaction linked to an account.
 *
 * Two constructors:
 *   - Transaction(type, amount, balanceAfter, note)
 *       → used when creating a new transaction in memory; sets date = now()
 *   - Transaction(type, amount, balanceAfter, note, date)
 *       → used when reconstructing a transaction fetched from the DB
 */
public class Transaction {

    public enum Type {
        CREDIT, DEBIT, TRANSFER_IN, TRANSFER_OUT
    }

    private final Type          type;
    private final double        amount;
    private final double        balanceAfter;
    private final LocalDateTime date;
    private final String        note;

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Used for new in-memory transactions — timestamp = now. */
    public Transaction(Type type, double amount, double balanceAfter, String note) {
        this(type, amount, balanceAfter, note, LocalDateTime.now());
    }

    /** Used when rebuilding a Transaction from a DB ResultSet row. */
    public Transaction(Type type, double amount, double balanceAfter,
                       String note, LocalDateTime date) {
        this.type         = type;
        this.amount       = amount;
        this.balanceAfter = balanceAfter;
        this.note         = note;
        this.date         = date;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Type          getType()         { return type; }
    public double        getAmount()       { return amount; }
    public double        getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getDate()         { return date; }
    public String        getNote()         { return note; }

    // ── Display ───────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        String sign = (type == Type.CREDIT || type == Type.TRANSFER_IN) ? "+" : "-";
        return String.format(
            "  [%s]  %-14s  %s₹%-10.2f  Balance: ₹%.2f  | %s",
            date.format(FORMATTER),
            type,
            sign,
            amount,
            balanceAfter,
            note
        );
    }
}
