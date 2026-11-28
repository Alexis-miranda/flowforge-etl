# FlowForge architecture

## Overview

FlowForge ETL is a modular Java 21 application that processes CSV data through ordered pipeline steps. Its first business workflow is Cash ETL: it reads incoming records, validates them, persists accepted and rejected data in SQL Server, and publishes a FRONT CSV artifact to the local filesystem.

The React dashboard is a client of the REST API. It does not connect directly to SQL Server: execution data flows from SQL Server-backed repositories through the Java API and then to the frontend.

## Maven modules

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline domain model, execution engine, step-executor registry, execution context, execution history, and repository abstractions. |
| `flowforge-connectors` | CSV, SQL Server, and SFTP infrastructure connectors. |
| `flowforge-cash` | Cash workflow composition, validation, SQL persistence, local output delivery, recovery CLI, request resolver, and worker. |
| `flowforge-messaging` | Execution request contract, JSON serialization, RabbitMQ adapters, lease-protected request handler, and outbox abstractions. |
| `flowforge-api` | HTTP endpoints for submitting requests and querying execution history. |
| `frontend` | React 18 / TypeScript dashboard, CSV upload, execution list, and execution details. |

The core module is independent of the Cash workflow. The Cash module composes the generic engine with business-specific steps and connectors.

## End-to-end execution flow

1. The dashboard uploads a CSV to `POST /api/uploads`.
2. The API stores the file in its configured upload directory and submits a Cash execution request.
3. The request and a `PENDING` outbox message are inserted into SQL Server in one database transaction.
4. The API outbox dispatcher publishes pending requests to RabbitMQ and marks successfully published messages.
5. The Cash worker consumes a request, acquires an execution lease, and invokes the pipeline.
6. The pipeline extracts and validates the CSV rows, then persists accepted/rejected business data and audit records in SQL Server.
7. The worker records execution and step state in SQL Server.
8. The dashboard calls the REST API to read recent executions, individual execution status, and step history.

The API and worker must be able to access the same uploaded-file path. For separate hosts, configure shared storage or an explicit file-transfer mechanism.

## Cash workflow

1. **Extract** — reads CSV columns such as `accountCode`, `adjustmentId`, `currency`, and `amount`.
2. **Validate** — separates accepted and rejected records and records rejection reasons. Amounts must be numeric and zero or positive.
3. **Prepare** — prepares the FRONT CSV using the configured back-office code, output columns, and output directory.
4. **Persist** — stores accepted/rejected records, original-row audit data, load-control information, and a pending output delivery in SQL Server.
5. **Publish** — writes an immutable FRONT CSV artifact and records its path, SHA-256, and execution ID.

The recovery CLI retries pending local output deliveries without reloading the source data. The SQL delivery record is authoritative.

**Delivery boundary:** Cash currently publishes to the local filesystem. A reusable SFTP reader/writer exists in the connectors module, including SSH host-key verification, but SFTP publication is not integrated into the Cash workflow.

## SQL Server integration

`SqlServerConnectionFactory` centralizes JDBC connection creation. It reads `FLOWFORGE_DB_JDBC_URL`, `FLOWFORGE_DB_USER`, and `FLOWFORGE_DB_PASSWORD` from the process environment, checks required values, and opens connections through `DriverManager`. Credentials and the JDBC URL are not logged.

SQL Server stores two related but distinct areas of data:

- **Execution infrastructure:** request/outbox records, execution audit, step audit, and worker leases.
- **Cash business data:** staging rows, rejected rows, master audit rows, load-control records, and output-delivery records.

See [Database schema and relationships](database-schema.md) for the table inventory, Mermaid ER diagram, and example read-only queries. The diagram is derived from the checked-in SQL schema scripts; compare it with the target SQL Server instance before treating it as a live database export.

## Asynchronous execution

The API validates and stores requests; it does not execute ETL steps itself. The outbox dispatcher publishes pending messages to RabbitMQ. The Cash worker consumes requests, acquires an execution lease, and invokes the pipeline. The worker records execution and step state in SQL Server. Retry and dead-letter handling are bounded.

This is an initial implementation. Multi-instance outbox claiming, complete operational DLQ tooling, and production deployment concerns remain open.

## Technology

- **Backend:** Java 21, Maven, JDBC, SQL Server, RabbitMQ, JUnit 5, Mockito, Apache Commons CSV, Apache MINA SSHD.
- **Frontend:** React 18, TypeScript, Vite, lucide-react.
