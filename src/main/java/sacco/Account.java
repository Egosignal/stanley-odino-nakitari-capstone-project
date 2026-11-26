package sacco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared account state and validated deposit/withdraw behavior. */
public abstract class Account implements Auditable {
    private final String memberId;
    private final String accountNumber;
    private final Path transactionLog;
    private double balance;
    private final ArrayList<Transaction> transactionHistory = new ArrayList<>();

    protected Account(String memberId, String accountNumber, double openingBalance, Path transactionLog) {
        if (memberId == null || memberId.trim().isEmpty()) throw new IllegalArgumentException("Member ID is required");
        if (accountNumber == null || accountNumber.trim().isEmpty()) throw new IllegalArgumentException("Account number is required");
        if (!Double.isFinite(openingBalance) || openingBalance < 0) throw new IllegalArgumentException("Opening balance must be finite and non-negative");
        this.memberId = memberId;
        this.accountNumber = accountNumber;
        this.balance = openingBalance;
        this.transactionLog = transactionLog == null ? Paths.get("transactions.txt") : transactionLog;
    }

    public String getMemberId() { return memberId; }
    public String getAccountNumber() { return accountNumber; }
    public double getBalance() { return balance; }
    public abstract String getAccountType();
    public abstract double calculateInterest();

    public List<Transaction> getTransactionHistory() {
        return Collections.unmodifiableList(new ArrayList<>(transactionHistory));
    }

    public void deposit(double amount) {
        requirePositiveFinite(amount, "Deposit");
        double updatedBalance = balance + amount;
        if (!Double.isFinite(updatedBalance)) throw new IllegalArgumentException("Deposit would exceed the supported balance range");
        recordMovement("DEPOSIT", amount, updatedBalance);
    }

    public void withdraw(double amount) throws InsufficientFundsException {
        requirePositiveFinite(amount, "Withdrawal");
        if (amount > balance) {
            throw new InsufficientFundsException("Insufficient funds. Available balance: KSh " + balance);
        }
        double updatedBalance = balance - amount;
        recordMovement("WITHDRAWAL", amount, updatedBalance);
    }

    private void recordMovement(String type, double amount, double updatedBalance) {
        Transaction transaction = new Transaction(LocalDateTime.now(), type, memberId,
                accountNumber, amount, updatedBalance);
        logTransaction(transaction.toLogLine());
        balance = updatedBalance;
        transactionHistory.add(transaction);
    }

    private static void requirePositiveFinite(double amount, String operation) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException(operation + " amount must be a finite number greater than zero");
        }
    }

    @Override
    public void logTransaction(String message) {
        try {
            Files.write(transactionLog, (message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not append transaction log", error);
        }
    }

    void restoreTransaction(Transaction transaction) {
        if (!memberId.equals(transaction.getMemberId()) || !accountNumber.equals(transaction.getAccountNumber())) {
            throw new IllegalArgumentException("Transaction belongs to a different account");
        }
        if (!"DEPOSIT".equals(transaction.getType()) && !"WITHDRAWAL".equals(transaction.getType())) return;
        transactionHistory.add(transaction);
    }
}
