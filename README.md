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
  <img src="https://img.shields.io/badge/React-18-149ECA?logo=react&logoColor=white" alt="React 18" />
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Status-v0.1.0--candidate-blue" alt="Version 0.1.0 candidate" />
</p>

## Overview

FlowForge ETL is a modular Java application for processing CSV data through ordered pipeline steps. Its first business workflow, Cash ETL, reads incoming records, validates them, persists accepted and rejected data in SQL Server, and produces a FRONT output CSV.

The backend separates the generic pipeline engine, reusable infrastructure connectors, business-specific processing, messaging, and the HTTP API. A React dashboard prototype is included as a separate frontend application.

> **Project status:** active development / demo candidate. The backend has a working Cash workflow and asynchronous execution components. The dashboard is currently a frontend prototype and is **not connected to the REST API**. This repository is not a production-certified service.

## Current Cash ETL workflow

The Cash pipeline is composed of five ordered steps:

1. **Extract** — reads a CSV with `accountCode`, `adjustmentId`, `currency`, and `amount`.
2. **Validate** — separates accepted and rejected records and records rejection reasons. Amounts must be numeric and zero or positive; negative amounts are rejected.
3. **Prepare** — prepares the FRONT CSV payload using the configured back-office code, output columns, and output directory.
4. **Persist** — stores accepted records, rejected records, original-row audit data, load-control information, and a pending output delivery in SQL Server.
5. **Publish** — writes an immutable FRONT CSV artifact under `.flowforge-artifacts/<execution-hash>/` and records its path, SHA-256, and execution ID in SQL Server.

A recovery CLI retries pending local output deliveries without reloading the original source data. The database delivery record is authoritative. Artifacts left unreferenced by stale or losing workers may need operational cleanup.

**Delivery boundary:** Cash currently publishes to the local filesystem. A reusable SFTP reader/writer exists in the connectors module, including SSH host-key verification, but SFTP publication is not integrated into the Cash workflow.

## Capabilities

- Configurable pipeline definitions with ordered steps and a step-executor registry.
- Typed execution context and pipeline/per-step lifecycle tracking.
- Repository abstractions and SQL Server persistence for execution history.
- CSV extraction, validation, transformation, and serialization.
- Cash accepted/rejected record handling and original-row audit.
- SQL Server load control to identify duplicate source batches.
- Local output delivery tracking, integrity metadata, and recovery.
- Transactional persistence of asynchronous execution requests through an SQL Server outbox.
- RabbitMQ publisher/consumer adapters, bounded outbox dispatch, manual acknowledgements, retry and dead-letter queues.
- Lease-protected worker request handling with periodic lease renewal.
- REST API for submitting executions and querying recent executions, execution status, and step history.
- React dashboard prototype with English, Spanish, and French translations and light/dark themes.
- Unit, filesystem integration, API, and SQL Server integration tests.

## Architecture

The repository is a Maven multi-module application:

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline domain model, execution engine, step-executor registry, execution context, execution history, and repository abstractions. |
| `flowforge-connectors` | CSV, SQL Server, and SFTP infrastructure connectors, including SQL Server execution-history persistence. |
| `flowforge-cash` | Cash validation and pipeline composition, SQL Server persistence, local output delivery, recovery CLI, request resolver, and worker entry point. |
| `flowforge-messaging` | Execution request contract, JSON serialization, RabbitMQ adapters, lease-protected request handler, bounded outbox dispatcher, and outbox port. |
| `flowforge-api` | HTTP endpoints for accepting pipeline requests and querying request, execution, and step status. |

The core module does not depend on the Cash workflow. The Cash module composes the generic engine with business-specific steps and connectors.

### Asynchronous execution flow

1. The API validates a request and stores it with a pending outbox message in SQL Server.
2. The outbox dispatcher publishes pending messages to RabbitMQ and records successful dispatch.
3. The Cash worker consumes requests, acquires an execution lease, and invokes the Cash pipeline.
4. The worker records execution and step state in SQL Server. Retry and dead-letter handling are bounded and use broker publisher confirms.

This is an initial single-process-oriented implementation. Multi-instance outbox claiming, complete operational DLQ tooling, and production deployment concerns remain open.

## Technology stack

**Backend:** Java 21, Maven, SQL Server, RabbitMQ, JUnit 5, Mockito, Apache Commons CSV, Apache MINA SSHD.

**Frontend:** React 18, TypeScript, Vite, lucide-react.

## Build and test

### Prerequisites

- JDK 21
- Apache Maven
- Node.js 20 or newer and npm (for the dashboard)
- SQL Server and RabbitMQ for the full asynchronous integration test suite

Run the backend verification from the repository root:

```bash
mvn --batch-mode clean verify
```

The GitHub Actions workflow provisions disposable SQL Server and RabbitMQ services, initializes the database schema, runs Maven verification, and builds the dashboard.

For local testing:

- Unit tests and filesystem-based integration tests do not require external services.
- SQL Server integration tests run when the database environment variables are configured.
- RabbitMQ/SQL Server asynchronous integration tests also require `FLOWFORGE_TEST_RABBITMQ_URI`; they are skipped when it is absent.
- SFTP integration tests require a separately configured test SFTP server; otherwise, they are skipped.

Configure database settings in the process environment:

- `FLOWFORGE_DB_JDBC_URL`
- `FLOWFORGE_DB_USER`
- `FLOWFORGE_DB_PASSWORD`

The `.env.example` file shows the expected variable names and a local-development JDBC URL. Java does not load `.env` files automatically; export the variables or use an environment-loading mechanism before starting an application. Never commit a real `.env` file or credentials.

## SQL Server setup

The Cash workflow expects a database named `FlowForgeETL_Dev`. Create the database, then run the schema scripts against it in the order documented in [docs/cash/sql/README.md](docs/cash/sql/README.md).

The required scripts include the Cash staging, rejected, master-audit, load-control, output-delivery, execution, lease, artifact-metadata, step-history, and request-outbox schemas. The optional `02-cash-reconciliation-schema.sql` is not required by the first Cash workflow.

Use a dedicated development/test database when running integration tests. Review existing table definitions before applying schema changes to a database containing business data.

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

Launch it with the project's runtime classpath (or an IDE/Maven run configuration) and configure the SQL Server environment variables first. Provide a unique `sourceFile` identifier for each distinct input batch; reusing an identifier already registered by load control is treated as a duplicate load.

The recovery entry point is `com.flowforge.etl.cash.cli.CashOutputRecoveryCli`. It retries pending local output deliveries recorded in SQL Server.

## Run the asynchronous Cash worker

The worker entry point is `com.flowforge.etl.cash.worker.CashPipelineWorker`. It declares a durable queue, consumes requests with manual acknowledgements, composes the SQL Server repositories with the Cash pipeline resolver, and renews execution leases while a request is running.

Required environment variables:

- `FLOWFORGE_DB_JDBC_URL`, `FLOWFORGE_DB_USER`, `FLOWFORGE_DB_PASSWORD` — SQL Server connection.
- `FLOWFORGE_RABBITMQ_URI` — RabbitMQ connection URI.

Optional worker/broker settings:

| Variable | Default |
| --- | --- |
| `FLOWFORGE_RABBITMQ_QUEUE` | `flowforge.pipeline.requests` |
| `FLOWFORGE_WORKER_ID` | Generated unique process identifier |
| `FLOWFORGE_WORKER_PREFETCH` | `1` |
| `FLOWFORGE_WORKER_LEASE_SECONDS` | `90` |
| `FLOWFORGE_WORKER_LEASE_RENEWAL_SECONDS` | `20` |
| `FLOWFORGE_WORKER_MAX_RETRIES` | `3` (does not include the initial attempt) |
| `FLOWFORGE_WORKER_RETRY_DELAY_MILLIS` | `30000` |
| `FLOWFORGE_WORKER_RETRY_CONFIRM_TIMEOUT_MILLIS` | `5000` |
| `FLOWFORGE_RABBITMQ_RETRY_QUEUE` | `<main-queue>.retry` |
| `FLOWFORGE_RABBITMQ_DEAD_LETTER_QUEUE` | `<main-queue>.dead` |
| `FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE` | Unset |

The worker uses RabbitMQ's default exchange: messages are routed with the configured queue name as the routing key. The API and worker must use the same main queue and compatible queue declaration settings. They do not provision SQL Server or RabbitMQ; install the schema and provision the broker separately.

Each request carries an input-file path, not the file itself. The path must resolve to a readable regular file on the worker host, and the worker must have access to the output directory. Producer and worker must therefore share a filesystem or use an explicit file-transfer mechanism. A local path on the producer host is not automatically available to a remote worker.

The worker uses a fixed-delay bounded retry policy, not exponential backoff. Dead-letter inspection and replay are operational responsibilities. Before manually replaying a dead-lettered request, inspect its SQL execution state and side effects. Do not treat this worker as production-ready without validating fencing at every side-effect boundary.

## Run the REST API

The API entry point is `com.flowforge.etl.api.FlowForgeApiApplication`. It uses the JDK HTTP server and accepts Cash execution requests through the SQL Server transactional outbox. A scheduled dispatcher publishes pending requests to RabbitMQ; the API does not execute ETL steps itself.

Required environment variables:

- `FLOWFORGE_DB_JDBC_URL`, `FLOWFORGE_DB_USER`, `FLOWFORGE_DB_PASSWORD`
- `FLOWFORGE_RABBITMQ_URI`

Optional settings:

| Variable | Default |
| --- | --- |
| `FLOWFORGE_RABBITMQ_QUEUE` | `flowforge.pipeline.requests` |
| `FLOWFORGE_API_HOST` | `127.0.0.1` |
| `FLOWFORGE_API_PORT` | `8080` |
| `FLOWFORGE_API_DISPATCH_INTERVAL_MILLIS` | `1000` |
| `FLOWFORGE_API_DISPATCH_BATCH_SIZE` | `50` |
| `FLOWFORGE_API_PUBLISH_CONFIRM_TIMEOUT_MILLIS` | `5000` |

If `FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE` is configured, use the same value in the API and worker so their queue declarations match.

### HTTP endpoints

| Method and path | Purpose |
| --- | --- |
| `POST /api/executions` | Submit a Cash execution request; returns `202 Accepted` with an execution ID and `QUEUED` status. |
| `GET /api/executions?limit=25` | List recent executions; limit defaults to 25 and must be between 1 and 100. |
| `GET /api/executions/{executionId}` | Read request-delivery and execution status. |
| `GET /api/executions/{executionId}/steps` | Read persisted step states; returns an empty list before execution starts. |

Example request body:

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

The required Cash parameters are `input`, `backOfficeCode`, and `outputDirectory`. File accessibility is validated by the worker because the API and worker may run on different hosts.

Status semantics:

- `QUEUED`: the request is waiting in the SQL Server outbox.
- `DISPATCHED`: RabbitMQ confirmed publication, but the worker has not yet created an execution snapshot.
- After the worker starts, the persisted execution status is returned.

**API security boundary:** this is a development API, not a public endpoint. It has no authentication, authorization, TLS termination, rate limiting, or CORS policy and binds to loopback by default. Do not expose it to an untrusted network. The outbox dispatcher is a single-process polling loop; multiple API instances need an atomic outbox-claim/lease mechanism. A request dead-lettered before the worker creates an execution snapshot may remain reported as `DISPATCHED`; durable consumer/DLQ outcome tracking is future work.

## Dashboard prototype

The React + TypeScript dashboard lives in `frontend/`. It currently provides:

- Overview cards, activity chart, pipeline-health cards, and illustrative recent executions.
- Search and status filters for the sample execution table.
- An execution-detail drawer with sample step information.
- A Cash execution form that creates a local sample row only.
- English, Spanish, and French translations.
- Light and dark themes, with preferences persisted in browser local storage.

Run locally:

```bash
cd frontend
npm install
npm run dev
```

On Windows PowerShell, if script execution policy blocks `npm.ps1`, use the command shim:

```powershell
npm.cmd install
npm.cmd run dev -- --host 127.0.0.1
```

Create a production frontend bundle:

```bash
npm run build
```

**Important:** the dashboard is not connected to the backend yet. Its metrics, chart, pipeline health, execution rows, and detail steps are illustrative frontend data. Submitting the form only adds a local in-memory demo row; it does not call `POST /api/executions`. The API currently has no browser CORS or authentication support, so frontend/API integration must follow an explicit security and file-upload design.

## Current limitations and roadmap

The following work is still required before a safe public demo or production deployment:

- [ ] Connect the dashboard to real API data and display real execution/step states.
- [ ] Design secure input-file upload and worker-accessible storage; do not rely on a browser-supplied local filesystem path.
- [ ] Add authentication and authorization.
- [ ] Define HTTPS termination, CORS policy, request limits, and rate limiting.
- [ ] Add deployment configuration for API, worker, SQL Server, RabbitMQ, and shared/object storage.
- [ ] Add structured logs, health/readiness checks, metrics, alerting, and operational runbooks.
- [ ] Define backup/restore, retention, dead-letter inspection/replay, and artifact cleanup procedures.
- [ ] Validate cancellation and fencing at every side-effect boundary; define multi-instance outbox claiming.
- [ ] Add deployed end-to-end tests using synthetic data.
- [ ] Integrate SFTP publication into the Cash workflow if required by the target use case.
- [ ] Confirm official input/output specifications, business mappings, and operational requirements.

Kafka, multi-tenant administration, scheduling UI, and additional pipeline types are not part of the current first demo scope.

FlowForge is a personal development project and a work in progress. Review database permissions, credentials, network exposure, data handling, recovery behavior, and operational requirements before using it with real business data.

---

<p align="center">
  <sub>Built with Java ☕</sub>
</p>
