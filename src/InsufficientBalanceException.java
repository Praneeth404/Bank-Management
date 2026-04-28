/**
 * Custom exception thrown when a withdrawal or transfer
 * is attempted with insufficient account balance.
 */
public class InsufficientBalanceException extends Exception {

    private final double currentBalance;
    private final double requestedAmount;

    public InsufficientBalanceException(double currentBalance, double requestedAmount) {
        super(String.format(
            "Insufficient balance. Requested: ₹%.2f | Available: ₹%.2f",
            requestedAmount, currentBalance
        ));
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public double getRequestedAmount() {
        return requestedAmount;
    }
}
