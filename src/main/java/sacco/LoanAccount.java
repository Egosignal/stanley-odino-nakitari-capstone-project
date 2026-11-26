package sacco;

import java.nio.file.Path;

/** Loan account with the 12% interest rate specified in the capstone brief. */
public final class LoanAccount extends Account {
    public LoanAccount(String memberId, String accountNumber, double openingBalance, Path transactionLog) {
        super(memberId, accountNumber, openingBalance, transactionLog);
    }

    @Override public String getAccountType() { return "Loan"; }

    @Override
    public double calculateInterest() {
        double interest = getBalance() * 0.12;
        if (!Double.isFinite(interest)) throw new IllegalStateException("Interest exceeds the supported number range");
        return interest;
    }
}
