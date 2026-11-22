# FlowForge ETL

<p align="center">
  <img src="docs/images/flowforge-etl.svg" alt="FlowForge ETL" width="520" />
</p>

<p align="center">
  <strong>A modular ETL application built with Java 21</strong><br />
  Extract · Transform · Load
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Maven-Multi--Module-C71A36?logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white" alt="JUnit 5" />
  <img src="https://img.shields.io/badge/Status-v0.1.0--candidate-blue" alt="Version 0.1.0 candidate" />
</p>

## What is FlowForge ETL?

FlowForge ETL is a modular Java application for processing CSV data through configurable pipeline steps. Its first business workflow, Cash ETL, extracts incoming records, validates them, persists accepted and rejected data in SQL Server, and generates a FRONT output CSV.

The project separates the generic pipeline engine, reusable infrastructure connectors, and the Cash business workflow into independent Maven modules.

## The problem it addresses

File-to-database integrations often involve repetitive manual processing and make failures difficult to track or recover from.

FlowForge brings ingestion, validation, persistence, and output delivery into an observable pipeline. It records pipeline and durable step execution history, tracks output delivery state, prevents duplicate source loads through a source identifier, and supports recovery of pending local deliveries.

## Current Cash ETL workflow

The Cash pipeline is composed from five ordered steps:

1. **Extract** — reads an input CSV containing `accountCode`, `adjustmentId`, `currency`, and `amount`.
2. **Validate** — separates accepted and rejected records and records rejection reasons. Amounts must be numeric and zero or positive; negative amounts are rejected.
3. **Prepare** — prepares the FRONT CSV payload using the configured back-office code, output columns, and output directory.
4. **Persist** — stores accepted records, rejected records, original-row audit data, load-control information, and a pending output delivery in SQL Server.
5. **Publish** — writes an immutable FRONT CSV artifact under `.flowforge-artifacts/<execution-hash>/` and atomically records its path, SHA-256, and execution ID in SQL Server. The database reference is authoritative; artifacts from stale or losing workers may remain unreferenced and require later cleanup.

The workflow also has a recovery CLI that retries pending local output deliveries without reloading the original source data.

**Current delivery boundary:** the Cash workflow publishes to the local filesystem. A reusable SFTP connector is implemented in the connectors module, but it is not yet integrated into Cash delivery.

## Main capabilities

- Configurable pipeline definitions with ordered steps.
- Step executor registry that resolves implementations by step type and connector key.
- Shared execution context with optional and required typed accessors.
- Pipeline lifecycle tracking and per-step execution history.
- Execution and per-step lifecycle persistence through a repository abstraction, including SQL Server integration.
- CSV extraction, validation, transformation, and serialization.
- Cash accepted/rejected record handling and original-row audit.
- SQL Server load control to identify duplicate source batches.
- Local output delivery tracking, integrity verification, and recovery.
- Reusable SFTP file reader and writer with SSH host-key verification.
- Unit tests, filesystem-based integration tests, and SQL Server integration tests.

## Architecture

The project is organized as a Maven multi-module application:

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline domain model, execution engine, step executor registry, execution context, execution history, and repository abstractions. |
| `flowforge-connectors` | Reusable CSV, SQL Server, and SFTP infrastructure connectors, including SQL Server execution-history persistence. |
| `flowforge-cash` | Cash-specific validation, pipeline composition, SQL Server persistence, local output delivery, recovery, and CLI entry points. |

The core module does not depend on the Cash workflow. The Cash module composes the generic engine with the required connectors and business-specific steps.

### Execution model

A `PipelineDefinition` contains ordered `PipelineStep` definitions. `PipelineExecutor` creates a fresh `PipelineExecutionContext` for each run and delegates each step to its registered `StepExecutor`.

Execution results include the overall status, timestamps, failure information, and step history. A repository can persist execution results; the Cash factory configures SQL Server execution persistence when the database connection settings are available.

The execution context stores values as objects because different steps exchange different data types. Callers can use `get(key, type)` for optional values or `getRequired(key, type)` when a value must exist and match the expected type.

## Technology stack

**Java 21** · **Maven** · **SQL Server** · **JUnit 5** · **Mockito** · **Apache Commons CSV** · **Apache MINA SSHD (SFTP)**

## Build and test

**Prerequisites:** JDK 21 and Apache Maven.

Run the complete build and test suite from the repository root:

```bash
mvn --batch-mode clean verify
```

GitHub Actions runs Maven verification on pushes to `main` and `feature/**` branches, and on pull requests targeting `main`. The workflow provisions a disposable SQL Server service, initializes the required schema, and runs the full verification command.

For local runs:

- Unit tests and filesystem-based integration tests do not require external services.
- SQL Server integration tests run when all three database environment variables are configured.
- SFTP integration tests require a separately configured test SFTP server; otherwise, they are skipped.

Configure these environment variables in the process environment:

- `FLOWFORGE_DB_JDBC_URL`
- `FLOWFORGE_DB_USER`
- `FLOWFORGE_DB_PASSWORD`

A `.env.example` file is provided as a reference. Java does not load `.env` files automatically; export the variables or use an environment-loading mechanism before starting the application. Never commit a real `.env` file or credentials.

## SQL Server setup

The Cash workflow expects a SQL Server database named `FlowForgeETL_Dev`. Create the database, then execute the required scripts in `docs/cash/sql` in this order:

1. `01-cash-staging-schema.sql`
2. `03-cash-rejected-schema.sql`
3. `04-cash-master-audit-schema.sql`
4. `05-cash-load-control-schema.sql`
5. `06-cash-output-delivery-schema.sql`
6. `07-etl-execution-schema.sql`
7. `08-cash-output-artifact-metadata.sql`

The generic step lifecycle audit table is created by `docs/sql/05-pipeline-step-execution-schema.sql` after `ETLExecution` exists.

The optional reconciliation tables in `02-cash-reconciliation-schema.sql` are not required by the first Cash workflow.

See the [SQL setup instructions](docs/cash/sql/README.md) for database configuration and test guidance. Use a dedicated development/test database when running integration tests.

## Run the Cash CLI

The CLI entry point is `com.flowforge.etl.cash.cli.CashPipelineCli`. It accepts:

```text
<input.csv> <backOfficeCode> <outputDir> [columns] [sourceFile]
```

Example:

```bash
java com.flowforge.etl.cash.cli.CashPipelineCli \
  cash-input.csv BNYM ./output accountCode,currency,amount cash-delivery-2026-11-21.csv
```

The command-line class must be launched with the project's runtime classpath (or through your chosen Maven/IDE run configuration). Configure the SQL Server environment variables before running the Cash workflow.

Provide a unique `sourceFile` identifier for each distinct input batch. Reusing an identifier already registered by the load-control mechanism is treated as a duplicate load.

The recovery entry point is `com.flowforge.etl.cash.cli.CashOutputRecoveryCli`. It retries pending local output deliveries from SQL Server.

## Project status and boundaries

**Current milestone: functional Cash ETL workflow.** The generic pipeline engine, CSV processing, Cash validation, SQL Server persistence, audit history, execution history, local output publication, delivery tracking, and recovery are implemented and covered by automated tests.

The following capabilities are **not currently implemented or integrated**:

- RabbitMQ or Kafka messaging.
- A REST API or Spring Boot service interface.
- SFTP publication from the Cash workflow.
- Distributed workers or remote execution.

This is a personal development project, not a production-certified system. Before production use, the official input/output specification, business mappings, operational monitoring, database permissions, backup strategy, deployment security, and recovery procedures must be reviewed and validated.

## Roadmap

- [ ] Confirm the official output-file specification and production mappings.
- [ ] Add further transformation types where required by real workflows.
- [ ] Introduce asynchronous execution through a separate messaging module (RabbitMQ is the first candidate).
- [ ] Evaluate a REST interface and service deployment.
- [ ] Integrate SFTP delivery into the Cash workflow.

---

<p align="center">
  <sub>Built with Java ☕</sub>
</p>
