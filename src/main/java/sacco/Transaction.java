package sacco;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Immutable record of an account movement or member/account audit event. */
public final class Transaction {
    private final LocalDateTime timestamp;
    private final String type;
    private final String memberId;
    private final String accountNumber;
    private final double amount;
    private final double balanceAfter;

    public Transaction(LocalDateTime timestamp, String type, String memberId,
                       String accountNumber, double amount, double balanceAfter) {
        if (timestamp == null) throw new IllegalArgumentException("Timestamp is required");
        if (type == null || type.trim().isEmpty()) throw new IllegalArgumentException("Transaction type is required");
        if (memberId == null || memberId.trim().isEmpty()) throw new IllegalArgumentException("Member ID is required");
        if (!Double.isFinite(amount) || !Double.isFinite(balanceAfter)) {
            throw new IllegalArgumentException("Transaction values must be finite");
        }
        this.timestamp = timestamp;
        this.type = type;
        this.memberId = memberId;
        this.accountNumber = accountNumber == null ? "" : accountNumber;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getMemberId() { return memberId; }
    public String getAccountNumber() { return accountNumber; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }

    /** Tab-separated fields are safe because IDs are validated and other values are controlled. */
    public String toLogLine() {
        return timestamp + "\t" + type + "\t" + memberId + "\t" + accountNumber
                + "\t" + Double.toString(amount) + "\t" + Double.toString(balanceAfter);
    }

    public static Transaction fromLogLine(String line) {
        String[] fields = line.split("\\t", -1);
        if (fields.length != 6) throw new IllegalArgumentException("Expected six tab-separated fields");
        try {
            return new Transaction(LocalDateTime.parse(fields[0]), fields[1], fields[2], fields[3],
                    Double.parseDouble(fields[4]), Double.parseDouble(fields[5]));
        } catch (DateTimeParseException | NumberFormatException error) {
            throw new IllegalArgumentException("Invalid transaction record", error);
        }
    }
}
