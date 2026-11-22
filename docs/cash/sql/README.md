# SQL Server setup

The first FlowForge Cash workflow expects a SQL Server database named
`FlowForgeETL_Dev`.

## 1. Create the database

Create an empty database named `FlowForgeETL_Dev` in your SQL Server instance.
The schema scripts below do not create the database itself.

## 2. Execute the required scripts

Run these scripts against `FlowForgeETL_Dev`, in this order:

1. `01-cash-staging-schema.sql`
2. `03-cash-rejected-schema.sql`
3. `04-cash-master-audit-schema.sql`
4. `05-cash-load-control-schema.sql`
5. `06-cash-output-delivery-schema.sql`
6. `07-etl-execution-schema.sql`
7. `08-cash-output-artifact-metadata.sql`
8. `../../sql/05-pipeline-step-execution-schema.sql` (durable step audit)
9. `../../sql/06-pipeline-request-outbox-schema.sql` (asynchronous execution request outbox)

The optional `02-cash-reconciliation-schema.sql` creates reconciliation
tables, but the first Cash ETL workflow does not use them.

The scripts are intended to be repeatable for an existing installation where
the relevant tables already exist. Review any existing table definitions
before applying schema changes to a database containing business data.

## 3. Configure the application

Set these environment variables for the process that runs Maven or the Cash CLI:

- `FLOWFORGE_DB_JDBC_URL`
- `FLOWFORGE_DB_USER`
- `FLOWFORGE_DB_PASSWORD`

Example JDBC URL for a local development SQL Server:

```text
jdbc:sqlserver://localhost:1433;databaseName=FlowForgeETL_Dev;encrypt=true;trustServerCertificate=true
```

The `trustServerCertificate=true` option is shown only as a local-development
example. For shared or production environments, configure a trusted TLS
certificate and do not bypass certificate validation.

Use a dedicated SQL Server login with only the permissions required by the
workflow. The application needs to read and write the Cash staging, rejected,
master audit, load-control, output-delivery, and pipeline execution tables.

Never commit credentials or put them in source files.

## 4. Run the full test suite against SQL Server

After the schema is installed and the three environment variables are set,
run from the repository root:

```bash
mvn --batch-mode clean verify
```

This enables the SQL Server integration tests. Tests for external SFTP
infrastructure remain optional and are skipped unless a test SFTP server is
configured.

The Cash end-to-end integration test inserts uniquely identified rows and
removes its test data in a `finally` block. Run the suite against a dedicated
development/test database, never against production data.

GitHub Actions uses a disposable SQL Server service container, initializes
these same schema scripts, and runs the full Maven verification.
