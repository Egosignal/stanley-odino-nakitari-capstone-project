package sacco;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads/writes members.csv and appends transaction records to transactions.txt. */
public final class SaccoStore {
    private static final String HEADER = "memberId,name,nationalId,accountType,accountNumber,balance";
    private final Path membersFile;
    private final Path transactionFile;

    public SaccoStore() {
        this(Paths.get("members.csv"), Paths.get("transactions.txt"));
    }

    SaccoStore(Path membersFile, Path transactionFile) {
        this.membersFile = membersFile;
        this.transactionFile = transactionFile;
    }

    public Path getTransactionFile() { return transactionFile; }

    /** Load the whole file before the menu starts; invalid rows stop startup to protect saved data. */
    public HashMap<String, Member> loadMembers() throws IOException {
        HashMap<String, Member> members = new HashMap<>();
        Map<String, String> nationalIdOwners = new HashMap<>();
        if (!Files.exists(membersFile)) return members;

        try (BufferedReader reader = Files.newBufferedReader(membersFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.replace("\uFEFF", "").equalsIgnoreCase(HEADER)) continue;
                if (line.trim().isEmpty()) continue;
                try {
                    List<String> fields = CsvSupport.parse(line);
                    if (fields.size() != 6) throw new IllegalArgumentException("Expected six CSV columns");
                    String memberId = fields.get(0).trim();
                    String name = fields.get(1).trim();
                    String nationalId = fields.get(2).trim();
                    String accountType = fields.get(3).trim();
                    String accountNumber = fields.get(4).trim();
                    String balanceText = fields.get(5).trim();

                    Member member = members.get(memberId);
                    if (member == null) {
                        if (memberId.isEmpty() || name.isEmpty() || nationalId.isEmpty()) {
                            throw new IllegalArgumentException("Member fields cannot be empty");
                        }
                        String normalizedNationalId = SaccoManager.normalizeNationalId(nationalId);
                        String owner = nationalIdOwners.get(normalizedNationalId);
                        if (owner != null && !owner.equals(memberId)) {
                            throw new IllegalArgumentException("National ID is assigned to more than one member");
                        }
                        nationalIdOwners.put(normalizedNationalId, memberId);
                        member = new Member(memberId, name, nationalId);
                        members.put(memberId, member);
                    } else if (!member.getName().equals(name) || !member.getNationalId().equals(nationalId)) {
                        throw new IllegalArgumentException("Repeated member rows contain conflicting details");
                    }

                    if (accountType.isEmpty()) {
                        if (!accountNumber.isEmpty() || !balanceText.isEmpty()) {
                            throw new IllegalArgumentException("An account row needs both account number and balance");
                        }
                        continue;
                    }
                    if (accountNumber.isEmpty() || balanceText.isEmpty()) {
                        throw new IllegalArgumentException("Account number and balance are required");
                    }
                    double balance = Double.parseDouble(balanceText);
                    Account account;
                    if ("Savings".equals(accountType)) {
                        account = new SavingsAccount(memberId, accountNumber, balance, transactionFile);
                    } else if ("Loan".equals(accountType)) {
                        account = new LoanAccount(memberId, accountNumber, balance, transactionFile);
                    } else {
                        throw new IllegalArgumentException("Unknown account type: " + accountType);
                    }
                    member.addAccount(account);
                } catch (RuntimeException error) {
                    throw new IOException("Invalid members.csv row " + lineNumber + ": " + error.getMessage(), error);
                }
            }
        }
        return members;
    }

    /** Restore transaction history without changing balances already saved in members.csv. */
    public int loadTransactionHistory(Map<String, Member> members) throws IOException {
        if (!Files.exists(transactionFile)) return 0;
        int restored = 0;
        try (BufferedReader reader = Files.newBufferedReader(transactionFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                Transaction transaction;
                try {
                    transaction = Transaction.fromLogLine(line);
                } catch (IllegalArgumentException error) {
                    System.out.println("Skipping malformed transaction log row " + lineNumber + ".");
                    continue;
                }
                if (!"DEPOSIT".equals(transaction.getType()) && !"WITHDRAWAL".equals(transaction.getType())) continue;
                Member member = members.get(transaction.getMemberId());
                Account account = member == null ? null : findAccountByNumber(member, transaction.getAccountNumber());
                if (account == null) {
                    System.out.println("Skipping history for an account not present in members.csv (row " + lineNumber + ").");
                    continue;
                }
                account.restoreTransaction(transaction);
                restored++;
            }
        }
        return restored;
    }

    public void saveMembers(Map<String, Member> members) throws IOException {
        Path temporary = membersFile.resolveSibling(membersFile.getFileName().toString() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(HEADER);
                writer.newLine();
                for (Member member : new java.util.TreeMap<>(members).values()) {
                    if (member.getAccounts().isEmpty()) {
                        writer.write(CsvSupport.row(member.getMemberId(), member.getName(), member.getNationalId(), "", "", ""));
                        writer.newLine();
                    } else {
                        for (Account account : new java.util.TreeMap<>(member.getAccounts()).values()) {
                            writer.write(CsvSupport.row(member.getMemberId(), member.getName(), member.getNationalId(),
                                    account.getAccountType(), account.getAccountNumber(), Double.toString(account.getBalance())));
                            writer.newLine();
                        }
                    }
                }
            }
            try {
                Files.move(temporary, membersFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, membersFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    public void appendAuditEvent(String type, String memberId, String accountNumber) throws IOException {
        Transaction event = new Transaction(LocalDateTime.now(), type, memberId,
                accountNumber == null ? "" : accountNumber, 0.0, 0.0);
        Files.write(transactionFile, (event.toLogLine() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    private static Account findAccountByNumber(Member member, String accountNumber) {
        for (Account account : member.getAccounts().values()) {
            if (account.getAccountNumber().equals(accountNumber)) return account;
        }
        return null;
    }
}
