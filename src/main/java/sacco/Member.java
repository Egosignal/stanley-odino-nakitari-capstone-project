package sacco;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** A SACCO member and their accounts indexed by account type. */
public final class Member {
    private final String memberId;
    private final String name;
    private final String nationalId;
    private final Map<String, Account> accounts = new HashMap<>();

    public Member(String memberId, String name, String nationalId) {
        this.memberId = requireText(memberId, "Member ID");
        this.name = requireText(name, "Name");
        this.nationalId = requireText(nationalId, "National ID");
    }

    public String getMemberId() { return memberId; }
    public String getName() { return name; }
    public String getNationalId() { return nationalId; }
    public Map<String, Account> getAccounts() { return Collections.unmodifiableMap(accounts); }
    public Account getAccount(String type) { return accounts.get(type); }

    public void addAccount(Account account) {
        if (account == null) throw new IllegalArgumentException("Account is required");
        if (!memberId.equals(account.getMemberId())) throw new IllegalArgumentException("Account belongs to a different member");
        if (accounts.containsKey(account.getAccountType())) throw new IllegalArgumentException("Member already has a " + account.getAccountType() + " account");
        accounts.put(account.getAccountType(), account);
    }

    /** Remove an account when saving its opening fails. */
    void removeAccount(String type) {
        accounts.remove(type);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
