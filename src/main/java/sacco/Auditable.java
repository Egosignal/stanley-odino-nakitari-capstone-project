package sacco;

/** A domain object that can append an audit record. */
public interface Auditable {
    void logTransaction(String message);
}
