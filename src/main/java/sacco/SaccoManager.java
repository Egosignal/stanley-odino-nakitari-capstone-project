package sacco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/** Console menu and business workflow for the M-Kopo SACCO learning project. */
public final class SaccoManager {
    private final HashMap<String, Member> members = new HashMap<>();
    private final HashSet<String> nationalIds = new HashSet<>();
    private final SaccoStore store;
    private final Scanner input;

    private SaccoManager(SaccoStore store, Scanner input) {
        this.store = store;
        this.input = input;
    }

    public static void main(String[] args) {
        SaccoStore store = new SaccoStore();
        SaccoManager application = new SaccoManager(store, new Scanner(System.in));
        try {
            application.members.putAll(store.loadMembers());
            int restored = store.loadTransactionHistory(application.members);
            for (Member member : application.members.values()) {
                application.nationalIds.add(normalizeNationalId(member.getNationalId()));
            }
            System.out.println("Loaded " + application.members.size() + " member(s) and " + restored + " transaction(s).");
        } catch (IOException error) {
            System.out.println("Could not safely load saved data: " + error.getMessage());
            System.out.println("Fix members.csv or transactions.txt before starting the application again.");
            return;
        }
        application.runMenu();
    }

    private void runMenu() {
        int choice;
        do {
            System.out.println("\n=== M-KOPO SACCO MANAGEMENT SYSTEM ===");
            System.out.println("1. Register New Member");
            System.out.println("2. Open an Account");
            System.out.println("3. Deposit Money");
            System.out.println("4. Withdraw Money");
            System.out.println("5. View Member Details");
            System.out.println("6. Exit and Save");
            choice = readInt("Enter choice: ", 1, 6);

            switch (choice) {
                case 1: registerMember(); break;
                case 2: openAccount(); break;
                case 3: depositMoney(); break;
                case 4: withdrawMoney(); break;
                case 5: viewMemberDetails(); break;
                case 6: saveOnExit(); break;
                default: throw new IllegalStateException("Menu choice validation failed");
            }
        } while (choice != 6);
    }

    private void registerMember() {
        String memberId = readRequired("Member ID (letters, numbers, hyphen, underscore): ");
        if (!memberId.matches("[A-Za-z0-9_-]+")) {
            System.out.println("Member ID may use letters, numbers, hyphens, and underscores only.");
            return;
        }
        if (members.containsKey(memberId)) {
            System.out.println("That member ID is already registered.");
            return;
        }
        String name = readRequired("Full name: ");
        String nationalId = readRequired("National ID: ");
        if (nationalIds.contains(normalizeNationalId(nationalId))) {
            System.out.println("A member with that National ID is already registered.");
            return;
        }

        Member member = new Member(memberId, name, nationalId);
        members.put(memberId, member);
        nationalIds.add(normalizeNationalId(nationalId));
        try {
            store.saveMembers(members);
        } catch (IOException error) {
            members.remove(memberId);
            nationalIds.remove(normalizeNationalId(nationalId));
            System.out.println("Member was not added because the CSV file could not be saved: " + error.getMessage());
            return;
        }
        try {
            store.appendAuditEvent("MEMBER_REGISTERED", memberId, "");
            System.out.println("Member registered and saved.");
        } catch (IOException error) {
            System.out.println("Member was saved, but the audit entry could not be appended: " + error.getMessage());
        }
    }

    private void openAccount() {
        Member member = findMember(readRequired("Member ID: "));
        if (member == null) return;
        System.out.println("1. Savings Account (5% interest)");
        System.out.println("2. Loan Account (12% interest)");
        int typeChoice = readInt("Choose account type: ", 1, 2);
        String type = typeChoice == 1 ? "Savings" : "Loan";
        if (member.getAccount(type) != null) {
            System.out.println("This member already has a " + type + " account.");
            return;
        }

        String accountNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Account account = typeChoice == 1
                ? new SavingsAccount(member.getMemberId(), accountNumber, 0, store.getTransactionFile())
                : new LoanAccount(member.getMemberId(), accountNumber, 0, store.getTransactionFile());
        member.addAccount(account);
        try {
            store.saveMembers(members);
        } catch (IOException error) {
            member.removeAccount(type);
            System.out.println("Account was not opened because the CSV file could not be saved: " + error.getMessage());
            return;
        }
        try {
            store.appendAuditEvent("ACCOUNT_OPENED", member.getMemberId(), accountNumber);
            System.out.println(type + " account opened: " + accountNumber);
        } catch (IOException error) {
            System.out.println(type + " account was saved, but its audit entry could not be appended: " + error.getMessage());
        }
    }

    private void depositMoney() {
        Account account = findAccount();
        if (account == null) return;
        double amount = readPositiveAmount("Amount to deposit (KSh): ");
        try {
            account.deposit(amount);
            saveAfterAccountChange(account);
            System.out.printf("Deposit recorded. New balance: KSh %.2f%n", account.getBalance());
        } catch (UncheckedIOException error) {
            System.out.println("Deposit was not applied because the transaction log could not be written: " + error.getMessage());
        }
    }

    private void withdrawMoney() {
        Account account = findAccount();
        if (account == null) return;
        double amount = readPositiveAmount("Amount to withdraw (KSh): ");
        try {
            account.withdraw(amount);
            saveAfterAccountChange(account);
            System.out.printf("Withdrawal recorded. New balance: KSh %.2f%n", account.getBalance());
        } catch (InsufficientFundsException error) {
            System.out.println("Transaction declined: " + error.getMessage());
        } catch (UncheckedIOException error) {
            System.out.println("Withdrawal was not applied because the transaction log could not be written: " + error.getMessage());
        }
    }

    private void saveAfterAccountChange(Account account) {
        try {
            store.saveMembers(members);
        } catch (IOException error) {
            System.out.println("The transaction is logged, but the updated balance could not be saved to members.csv: " + error.getMessage());
        }
    }

    private Account findAccount() {
        Member member = findMember(readRequired("Member ID: "));
        if (member == null) return null;
        String type = readRequired("Account type (Savings/Loan): ");
        if (!"Savings".equalsIgnoreCase(type) && !"Loan".equalsIgnoreCase(type)) {
            System.out.println("Enter Savings or Loan.");
            return null;
        }
        Account account = member.getAccount("Savings".equalsIgnoreCase(type) ? "Savings" : "Loan");
        if (account == null) System.out.println("That account type is not open for this member.");
        return account;
    }

    private void viewMemberDetails() {
        Member member = findMember(readRequired("Member ID: "));
        if (member == null) return;
        System.out.println("\n--- MEMBER DETAILS ---");
        System.out.println("Name: " + member.getName());
        System.out.println("Member ID: " + member.getMemberId());
        System.out.println("National ID: " + member.getNationalId());
        if (member.getAccounts().isEmpty()) {
            System.out.println("No accounts opened yet.");
            return;
        }
        for (Account account : member.getAccounts().values()) {
            System.out.printf("%s account %s | Balance: KSh %.2f | Estimated interest: KSh %.2f%n",
                    account.getAccountType(), account.getAccountNumber(), account.getBalance(), account.calculateInterest());
            if (account.getTransactionHistory().isEmpty()) {
                System.out.println("  No transactions recorded.");
            } else {
                for (Transaction transaction : account.getTransactionHistory()) {
                    System.out.printf("  %s %s KSh %.2f; balance KSh %.2f%n",
                            transaction.getTimestamp(), transaction.getType(), transaction.getAmount(), transaction.getBalanceAfter());
                }
            }
        }
    }

    private Member findMember(String memberId) {
        Member member = members.get(memberId);
        if (member == null) System.out.println("Member not found.");
        return member;
    }

    private void saveOnExit() {
        try {
            store.saveMembers(members);
            System.out.println("Data saved. Goodbye.");
        } catch (IOException error) {
            System.out.println("Could not save members.csv: " + error.getMessage());
        }
    }

    private int readInt(String prompt, int minimum, int maximum) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= minimum && parsed <= maximum) return parsed;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a whole number from " + minimum + " to " + maximum + ".");
        }
    }

    private double readPositiveAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            try {
                double parsed = Double.parseDouble(value);
                if (Double.isFinite(parsed) && parsed > 0) return parsed;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a finite amount greater than zero.");
        }
    }

    private String readRequired(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This field is required.");
        }
    }

    static String normalizeNationalId(String nationalId) {
        return nationalId.trim().toUpperCase(Locale.ROOT);
    }
}
