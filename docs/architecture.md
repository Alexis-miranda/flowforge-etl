# FlowForge architecture

## Overview

FlowForge ETL is a modular Java application that processes CSV data through ordered pipeline steps. The first business workflow is Cash ETL: it reads incoming records, validates them, persists accepted and rejected records in SQL Server, and publishes a FRONT CSV artifact to the local filesystem.

## Maven modules

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline domain model, execution engine, step-executor registry, execution context, execution history, and repository abstractions. |
| `flowforge-connectors` | CSV, SQL Server, and SFTP infrastructure connectors. |
| `flowforge-cash` | Cash workflow composition, validation, SQL persistence, local output delivery, recovery CLI, request resolver, and worker. |
| `flowforge-messaging` | Execution request contract, JSON serialization, RabbitMQ adapters, lease-protected request handler, and outbox abstractions. |
| `flowforge-api` | HTTP endpoints for submitting requests and querying execution history. |

The core module is independent of the Cash workflow. The Cash module composes the generic engine with business-specific steps and connectors.

## Cash workflow

1. **Extract** — reads CSV columns `accountCode`, `adjustmentId`, `currency`, and `amount`.
2. **Validate** — separates accepted and rejected records and records rejection reasons. Amounts must be numeric and zero or positive.
3. **Prepare** — prepares the FRONT CSV using the configured back-office code, output columns, and output directory.
4. **Persist** — stores accepted/rejected records, original-row audit data, load-control information, and a pending output delivery in SQL Server.
5. **Publish** — writes an immutable FRONT CSV artifact and records its path, SHA-256, and execution ID.

The recovery CLI retries pending local output deliveries without reloading the source data. The SQL delivery record is authoritative.

**Delivery boundary:** Cash currently publishes to the local filesystem. A reusable SFTP reader/writer exists in the connectors module, including SSH host-key verification, but SFTP publication is not integrated into the Cash workflow.

## Asynchronous execution

1. The API validates a request and stores it with a pending outbox message in SQL Server.
2. The outbox dispatcher publishes pending messages to RabbitMQ and records successful dispatch.
3. The Cash worker consumes requests, acquires an execution lease, and invokes the pipeline.
4. The worker records execution and step state in SQL Server. Retry and dead-letter handling are bounded.

This is an initial implementation. Multi-instance outbox claiming, complete operational DLQ tooling, and production deployment concerns remain open.

## Technology

- **Backend:** Java 21, Maven, SQL Server, RabbitMQ, JUnit 5, Mockito, Apache Commons CSV, Apache MINA SSHD.
- **Frontend:** React 18, TypeScript, Vite, lucide-react.
