# Stanley Odino Nakitari — M-Kopo SACCO Capstone Project

**Student:** Stanley Odino Nakitari  
**Purpose:** An independent Java console application built for the supplied M-Kopo SACCO capstone brief.

The program registers members, opens savings and loan accounts, records deposits and withdrawals, calculates account interest, and saves member/account data to `members.csv`. It appends audit and transaction records to `transactions.txt`.

## Requirements represented

- Private fields with validation in domain classes
- An abstract `Account` class with savings and loan implementations
- The `Auditable` interface and checked `InsufficientFundsException`
- `HashMap` member/account storage, a `HashSet` for national-ID uniqueness, and an `ArrayList<Transaction>` per account
- A `do-while` console menu, input validation, and try-with-resources file access
- CSV loading at startup, CSV saving on changes and exit, and append-only audit logging

## Run

Requires Java 8 or newer. From the repository root on Windows PowerShell:

```powershell
javac -d out (Get-ChildItem -Recurse src\main\java\*.java).FullName
java -cp out sacco.SaccoManager
```

On macOS or Linux:

```sh
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out sacco.SaccoManager
```

The program reads `members.csv` if it exists. It creates or updates that file as member/account data changes and on exit. `transactions.txt` is append-only. Back up both files before editing them manually.

The CSV reader supports quoted fields and commas in names. National IDs are used only for duplicate checks and stored in `members.csv`; they are not written to the transaction log.

## Project files

- `src/main/java/sacco/`: domain model, console workflow, CSV support, and persistence
- `members.csv`: header-only starting file
- `transactions.txt`: append-only audit log, created by the application when needed

This is a student learning project. It is not connected to a real SACCO or payment service.
