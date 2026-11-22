# Local development setup

## Prerequisites

- JDK 21
- Apache Maven
- Node.js 20 or newer and npm (for the dashboard)
- SQL Server and RabbitMQ for the full asynchronous workflow

## Environment variables

Configure database settings in the process environment:

- `FLOWFORGE_DB_JDBC_URL`
- `FLOWFORGE_DB_USER`
- `FLOWFORGE_DB_PASSWORD`

The repository's `.env.example` lists expected variable names and a local-development JDBC URL. Java does not load `.env` files automatically; export the variables or use an environment-loading mechanism before starting an application. Never commit a real `.env` file or credentials.

For the asynchronous worker and API, also configure:

- `FLOWFORGE_RABBITMQ_URI`

The API and worker must use the same RabbitMQ queue configuration. The default queue is `flowforge.pipeline.requests`.

## SQL Server

The Cash workflow expects a database named `FlowForgeETL_Dev`. Create the database, then follow the ordered schema instructions in [the Cash SQL setup guide](../cash/sql/README.md).

Use a dedicated development/test database for integration tests. Review existing table definitions before applying schema changes to a database containing business data.

## Build and tests

Run backend verification from the repository root:

```bash
mvn --batch-mode clean verify
```

The GitHub Actions workflow provisions disposable SQL Server and RabbitMQ services, initializes the database schema, runs Maven verification, and builds the dashboard.

- Unit and filesystem-based integration tests do not require external services.
- SQL Server integration tests run when the database environment variables are configured.
- RabbitMQ/SQL Server asynchronous integration tests also require `FLOWFORGE_TEST_RABBITMQ_URI`; they are skipped when it is absent.
- SFTP integration tests require a separately configured test SFTP server; otherwise, they are skipped.

## Run the dashboard

```bash
cd frontend
npm install
npm run dev
```

On Windows PowerShell, if script execution policy blocks `npm.ps1`, use:

```powershell
npm.cmd install
npm.cmd run dev -- --host 127.0.0.1
```

Build the frontend:

```bash
npm run build
```

## Run the Cash CLI

Entry point: `com.flowforge.etl.cash.cli.CashPipelineCli`.

Arguments:

```text
<input.csv> <backOfficeCode> <outputDir> [columns] [sourceFile]
```

Example:

```bash
java com.flowforge.etl.cash.cli.CashPipelineCli \
  cash-input.csv BNYM ./output accountCode,currency,amount cash-delivery-2026-11-21.csv
```

Configure SQL Server before running. Use a unique `sourceFile` identifier for each distinct input batch; reusing an identifier already registered by load control is treated as a duplicate load.

Recovery entry point: `com.flowforge.etl.cash.cli.CashOutputRecoveryCli`. It retries pending local output deliveries recorded in SQL Server.

## Run the worker and API

Worker entry point: `com.flowforge.etl.cash.worker.CashPipelineWorker`.

API entry point: `com.flowforge.etl.api.FlowForgeApiApplication`.

Both require the SQL Server environment variables and `FLOWFORGE_RABBITMQ_URI`. The API uses a scheduled outbox dispatcher to publish requests to RabbitMQ; the worker consumes those requests and runs the Cash pipeline.

Each request carries an input-file path, not the file itself. The path must be readable by the worker host, and the worker must have access to the output directory. For separate hosts, use a shared filesystem or an explicit file-transfer mechanism. A producer's local path is not automatically available to a remote worker.

See [the API reference](../api-reference.md) for endpoints and request format.

## Worker configuration

| Variable | Default |
| --- | --- |
| `FLOWFORGE_RABBITMQ_QUEUE` | `flowforge.pipeline.requests` |
| `FLOWFORGE_WORKER_ID` | Generated unique process identifier |
| `FLOWFORGE_WORKER_PREFETCH` | `1` |
| `FLOWFORGE_WORKER_LEASE_SECONDS` | `90` |
| `FLOWFORGE_WORKER_LEASE_RENEWAL_SECONDS` | `20` |
| `FLOWFORGE_WORKER_MAX_RETRIES` | `3` (initial attempt not included) |
| `FLOWFORGE_WORKER_RETRY_DELAY_MILLIS` | `30000` |
| `FLOWFORGE_WORKER_RETRY_CONFIRM_TIMEOUT_MILLIS` | `5000` |
| `FLOWFORGE_RABBITMQ_RETRY_QUEUE` | `<main-queue>.retry` |
| `FLOWFORGE_RABBITMQ_DEAD_LETTER_QUEUE` | `<main-queue>.dead` |
| `FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE` | Unset |

The worker uses bounded fixed-delay retries, not exponential backoff. Inspect SQL execution state and side effects before manually replaying a dead-lettered request.
