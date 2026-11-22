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
- Transactional persistence of asynchronous pipeline requests and their pending outbox delivery state in SQL Server.
- A bounded outbox dispatcher, RabbitMQ publisher/consumer, manual-ack request consumer, lease-protected request handler with periodic lease renewal, a concrete Cash request-to-pipeline resolver, a managed Cash worker, and a REST API for submitting and querying executions.
- Unit tests, filesystem-based integration tests, and SQL Server integration tests.

## Architecture

The project is organized as a Maven multi-module application:

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline domain model, execution engine, step executor registry, execution context, execution history, and repository abstractions. |
| `flowforge-connectors` | Reusable CSV, SQL Server, and SFTP infrastructure connectors, including SQL Server execution-history persistence. |
| `flowforge-cash` | Cash-specific validation, pipeline composition, SQL Server persistence, local output delivery, recovery, CLI entry points, asynchronous request resolution, and the Cash worker entry point. |
| `flowforge-messaging` | Transport-neutral execution request contract, JSON serialization, RabbitMQ publisher/consumer adapters, lease-protected request handler, bounded outbox dispatcher, and transactional outbox port. |
| `flowforge-api` | HTTP endpoints for accepting pipeline requests and querying request, execution, and step status. |

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

## Run the asynchronous Cash worker

The worker entry point is `com.flowforge.etl.cash.worker.CashPipelineWorker`. It declares a durable queue, consumes requests with manual acknowledgements, composes the SQL Server repositories with the Cash pipeline resolver, and renews execution leases while a request is running.

Configure the following process environment variables before starting it:

- `FLOWFORGE_DB_JDBC_URL`, `FLOWFORGE_DB_USER`, `FLOWFORGE_DB_PASSWORD` — required SQL Server connection settings.
- `FLOWFORGE_RABBITMQ_URI` — required RabbitMQ connection URI.
- `FLOWFORGE_RABBITMQ_QUEUE` — queue name; defaults to `flowforge.pipeline.requests`.
- `FLOWFORGE_WORKER_ID` — optional worker owner identifier; a unique process identifier is generated when omitted.
- `FLOWFORGE_WORKER_PREFETCH` — optional positive prefetch count; defaults to `1`.
- `FLOWFORGE_WORKER_LEASE_SECONDS` — optional lease duration in seconds; defaults to `90`.
- `FLOWFORGE_WORKER_LEASE_RENEWAL_SECONDS` — optional renewal interval in seconds; defaults to `20` and must be shorter than the lease.
- `FLOWFORGE_WORKER_MAX_RETRIES` — optional non-negative retry count; defaults to `3` (does not include the initial attempt).\n- `FLOWFORGE_WORKER_RETRY_DELAY_MILLIS` — optional fixed delay before retry; defaults to `30000` ms.\n- `FLOWFORGE_WORKER_RETRY_CONFIRM_TIMEOUT_MILLIS` — optional publisher-confirm timeout; defaults to `5000` ms.\n- `FLOWFORGE_RABBITMQ_RETRY_QUEUE` — optional durable retry queue; defaults to `<main-queue>.retry`.\n- `FLOWFORGE_RABBITMQ_DEAD_LETTER_QUEUE` — optional durable terminal queue; defaults to `<main-queue>.dead`.\n- `FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE` — optional dead-letter exchange argument for the main queue.

The worker uses RabbitMQ's default exchange: the publisher must send to exchange `""` with the configured queue name as its routing key. The worker does not provision RabbitMQ or SQL Server. The database schema listed above must already be installed.

Each request carries an input-file path, not the file itself. The path must resolve to a readable regular file on the worker host, and the worker must have access to the output directory as well. For multiple worker machines, use a shared filesystem or another explicit file-transfer mechanism; local paths on the producer host are not automatically visible to a remote worker.

The worker declares a durable retry queue with a fixed message TTL and dead-letters expired retry messages back to the main queue. On a transient handler failure, it republishes the original message with an incremented retry header, waits for publisher confirmation, and only then acknowledges the original delivery. After the configured retry budget is exhausted, or when a payload is malformed or rejected as non-retryable, it republishes the message to the durable dead-letter queue and acknowledges only after broker confirmation. If republishing fails or times out, the original delivery is negatively acknowledged for broker redelivery. Interrupted handlers are requeued without consuming the retry budget. This is a bounded fixed-delay retry policy, not exponential backoff.\n\nThe worker cancels its consumer on shutdown, waits for in-flight request handlers, stops lease renewal, and then closes its broker resources. This remains an initial application lifecycle, not a production deployment recipe. Dead-letter inspection and replay are operational responsibilities; retrying a dead-lettered execution manually still requires checking its SQL execution state and side effects. Do not treat this worker as production-ready without validating fencing at every side-effect boundary.


## Run the REST API

The `flowforge-api` module entry point is `com.flowforge.etl.api.FlowForgeApiApplication`. It exposes a small HTTP interface using the JDK HTTP server. It accepts Cash execution requests through the SQL Server transactional outbox; a scheduled dispatcher publishes pending requests to the configured RabbitMQ queue. The API does not execute ETL steps itself.

Start the application with the module runtime classpath and configure:

- `FLOWFORGE_DB_JDBC_URL`, `FLOWFORGE_DB_USER`, `FLOWFORGE_DB_PASSWORD` — required SQL Server settings.
- `FLOWFORGE_RABBITMQ_URI` — required RabbitMQ connection URI.
- `FLOWFORGE_RABBITMQ_QUEUE` — queue shared with the Cash worker; defaults to `flowforge.pipeline.requests`.
- `FLOWFORGE_API_HOST` — bind address; defaults to `127.0.0.1`.
- `FLOWFORGE_API_PORT` — HTTP port; defaults to `8080`.
- `FLOWFORGE_API_DISPATCH_INTERVAL_MILLIS` — outbox polling interval; defaults to `1000`.
- `FLOWFORGE_API_DISPATCH_BATCH_SIZE` — maximum messages per dispatch pass; defaults to `50`.
- `FLOWFORGE_API_PUBLISH_CONFIRM_TIMEOUT_MILLIS` — RabbitMQ publisher-confirm timeout; defaults to `5000`.

The main queue is declared durable by the API. If `FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE` is configured, use the same value in the API and worker so their queue declarations match.

### HTTP endpoints

`POST /api/executions` accepts JSON:

```json
{
  "pipelineType": "cash",
  "parameters": {
    "input": "/shared/input.csv",
    "backOfficeCode": "BNYM",
    "outputDirectory": "/shared/output",
    "columns": "accountCode,currency,amount",
    "sourceFile": "batch-2026-11-22.csv"
  }
}
```

It returns `202 Accepted` with an `executionId` and `QUEUED` status. The required Cash parameters are `input`, `backOfficeCode`, and `outputDirectory`; file accessibility is validated by the worker because the API and worker may run on different hosts.

- `GET /api/executions/{executionId}` — request delivery and execution status. It reports `QUEUED` while the outbox is pending, `DISPATCHED` after RabbitMQ confirms publication but before the worker creates an execution snapshot, and the persisted execution status thereafter.
- `GET /api/executions/{executionId}/steps` — persisted step states; returns an empty list while execution has not started.

This is an initial development API, not a public production endpoint: it has no authentication, authorization, TLS termination, rate limiting, or CORS policy. It binds to loopback by default. Do not expose it to an untrusted network without adding those controls. The outbox dispatcher is a single-process polling loop; multiple API instances require an atomic outbox-claim/lease mechanism to avoid concurrent duplicate publication. A request that reaches the broker but is later dead-lettered before the worker creates an execution snapshot may continue to appear as `DISPATCHED`; durable consumer/DLQ outcome tracking is a separate future capability.

## Project status and boundaries

**Current milestone: functional Cash ETL workflow.** The generic pipeline engine, CSV processing, Cash validation, SQL Server persistence, audit history, execution history, local output publication, delivery tracking, and recovery are implemented and covered by automated tests.

The following capabilities are **not currently implemented or integrated**:

- Production-ready end-to-end asynchronous execution. The repository now includes a Cash worker entry point that composes the resolver, SQL Server repositories, RabbitMQ consumer, and lease renewal. Operational retry backoff, dead-letter handling, deployment, observability, and end-to-end validation against real broker/database environments remain outstanding. Long-running step implementations must check the execution-context guard before irreversible work; fencing-token validation remains required at side-effect boundaries.
- Kafka messaging.
- A production-hardened REST service with authentication, authorization, TLS, rate limiting, and multi-instance outbox claiming.
- SFTP publication from the Cash workflow.
- Production deployment topology, broker provisioning, retry backoff, and dead-letter operations.

This is a personal development project, not a production-certified system. Before production use, the official input/output specification, business mappings, operational monitoring, database permissions, backup strategy, deployment security, and recovery procedures must be reviewed and validated.

## Roadmap

- [ ] Confirm the official output-file specification and production mappings.
- [ ] Add further transformation types where required by real workflows.
- [ ] Add operational tooling and runbooks to inspect and safely replay dead-lettered requests.
- [ ] Add cooperative cancellation checks inside long-running steps and verify fencing at every side-effect boundary.
- [ ] Add end-to-end tests against disposable RabbitMQ and SQL Server services.
- [ ] Harden the REST API for deployment, including authentication, authorization, TLS, observability, and multi-instance outbox claiming.
- [ ] Integrate SFTP delivery into the Cash workflow.

---

<p align="center">
  <sub>Built with Java ☕</sub>
</p>
