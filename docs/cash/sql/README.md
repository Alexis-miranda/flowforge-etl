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
6. `07-etl-execution-schema.sql` (creates the execution audit and independent worker-lease table)
7. `08-cash-output-artifact-metadata.sql`
8. `../../sql/05-pipeline-step-execution-schema.sql` (durable step audit; requires `dbo.ETLExecution`)
9. `../../sql/06-pipeline-request-outbox-schema.sql` (asynchronous execution request outbox)

The optional `02-cash-reconciliation-schema.sql` creates reconciliation tables and foreign keys to
`CashStaging`; the first Cash ETL workflow does not use them.

The separate `../../sql/07-etl-execution-lease-independent-schema.sql` file is a standalone,
repeatable lease-schema script for installations that need it independently. It is already
covered by `07-etl-execution-schema.sql` in the standard Cash setup above, so it does not need
to be run again.

The older `../../sql/04-pipeline-execution-audit-schema.sql` defines the same
`dbo.ETLExecution` table for standalone setup. The standard Cash setup uses
`07-etl-execution-schema.sql` instead; do not treat both files as separate tables or
run them as competing schema versions.

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

This enables the SQL Server integration tests. The RabbitMQ/SQL Server
asynchronous execution integration tests also require
`FLOWFORGE_TEST_RABBITMQ_URI`; they are skipped when that variable is absent.
GitHub Actions starts disposable RabbitMQ and SQL Server service containers and
runs these tests against both real services. Tests for external SFTP
infrastructure remain optional and are skipped unless a test SFTP server is
configured.

The Cash end-to-end integration test inserts uniquely identified rows and
removes its test data in a `finally` block. Run the suite against a dedicated
development/test database, never against production data.

GitHub Actions uses a disposable SQL Server service container, initializes
these same schema scripts, and runs the full Maven verification.

For the documented table inventory, declared foreign keys, ER diagram, and
read-only SQL examples, see [Database schema and relationships](../../database-schema.md).
