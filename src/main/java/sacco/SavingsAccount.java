package sacco;

import java.nio.file.Path;

/** Savings account with the 5% interest rate specified in the capstone brief. */
public final class SavingsAccount extends Account {
    public SavingsAccount(String memberId, String accountNumber, double openingBalance, Path transactionLog) {
        super(memberId, accountNumber, openingBalance, transactionLog);
    }

    @Override public String getAccountType() { return "Savings"; }

    @Override
    public double calculateInterest() {
        double interest = getBalance() * 0.05;
        if (!Double.isFinite(interest)) throw new IllegalStateException("Interest exceeds the supported number range");
        return interest;
    }
}
