# FlowForge ETL

<p align="center">
  <img src="docs/images/flowforge-cover.png" alt="FlowForge ETL" width="200" />
</p>

<p align="center">
  <strong>A modular ETL platform built with Java 21</strong>
  <br />
  Extract · Transform · Load
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Maven-Multi--Module-C71A36?logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white" alt="JUnit 5" />
  <img src="https://img.shields.io/badge/Status-In%20Development-yellow" alt="In development" />
</p>

---

## About

**FlowForge ETL** is a personal Java project exploring how to build a modular Extract, Transform, Load (ETL) platform. It separates pipeline execution from external connectors and processes data through configurable steps.

The current implementation focuses on a Cash data workflow: reading CSV files, validating and separating accepted and rejected records, persisting results in SQL Server, and tracking the delivery of generated FRONT CSV files. It also includes SFTP file-transfer capabilities with SSH host-key verification.

The project is built with **Java 21**, **Apache Maven**, **JUnit 5**, and **SQL Server**. It is under active development and is not presented as a production-certified system.

### Current verification

- Maven test build: `BUILD SUCCESS`.
- Latest recorded test run: 177 tests, 0 failures, 0 errors, 6 skipped.
- End-to-end test report: [ETL-E2E-TEST-2026-10-25.md](docs/ETL-E2E-TEST-2026-10-25.md).

The standard test run includes tests that require external services; skipped tests are not counted as successful integration executions. See the test report for the separately recorded SQL Server end-to-end scenario.

## Features

- **CSV extraction:** read input records, including UTF-8 CSV files.
- **Data validation:** validate required Cash fields, supported currencies, and decimal amounts.
- **Currency normalization:** accepted currency codes are trimmed and converted to uppercase.
- **CSV serialization:** configure output columns and their order; serialize in UTF-8.
- **SQL Server persistence:** record accepted and rejected Cash data and register load and delivery state.
- **Output delivery:** persist the prepared output payload and its SHA-256 hash before publishing the file.
- **Delivery recovery:** retry pending FRONT output deliveries without rerunning the Cash ingestion pipeline.
- **Pipeline execution:** execute steps sequentially through the shared execution context.
- **Execution tracking:** capture execution status, duration, and failure details.
- **SFTP:** upload and download files with SSH host-key verification.
- **Automated testing:** run unit tests and optional integration tests.

## Architecture

FlowForge ETL is a Maven multi-module project.

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline domain model, execution engine, shared context, validation and transformations |
| `flowforge-connectors` | CSV, SQL Server and SFTP connectors, pipeline wiring and command-line entry points |

### Core module

The `flowforge-core` module provides the engine-independent building blocks used to define and execute pipelines.

| Component | Responsibility |
| --- | --- |
| `domain` | Pipeline definitions, steps, configurations and execution statuses |
| `execution` | Pipeline execution, shared context, results and listeners |
| `extract` | Extraction abstractions |
| `transform` | Validation and transformation logic |
| `load` | Loading abstractions |

### Connectors module

| Connector | Current capability |
| --- | --- |
| CSV | Read, serialize and write CSV files |
| SQL Server | Persist Cash staging, rejected records, master records, load control and output delivery state |
| SFTP | Upload and download files with SSH host-key verification |
| RabbitMQ | Planned; implementation pending |
| IBM MQ | Planned; implementation pending |

## Cash ETL workflow

The Cash command-line pipeline runs these steps in order:

1. **Extract:** read the input CSV.
2. **Validate:** check required fields, currency codes and amounts; separate accepted and rejected records.
3. **Prepare FRONT output:** serialize accepted records using the configured output columns and calculate the SHA-256 hash.
4. **Persist:** use SQL Server to register the load, store accepted and rejected records, store master records, and register a pending output delivery when a prepared payload is available.
5. **Publish:** retrieve the pending payload from SQL Server, verify its hash, publish the file and mark the delivery as published.

The SQL transaction registers the Cash data and pending output delivery together. Publication is handled separately so that a failed file write can be retried through the recovery command without rerunning the ingestion pipeline.

### Input requirements

The Cash validator expects these canonical input column names:

- `accountCode`
- `adjustmentId`
- `currency`
- `amount`

The default allowed currencies are `EUR`, `USD`, `GBP` and `CHF`. Currency matching is case-insensitive and ignores surrounding whitespace. Accepted records store the normalized currency code. Rejected records retain the original field values and include a rejection reason.

Amounts are validated as decimal values. Their original textual representation is retained for CSV serialization; SQL persistence converts valid amounts to the configured decimal type.

The input CSV must use the canonical field names expected by the pipeline. The CLI does not automatically apply arbitrary source-to-canonical column mappings.

### FRONT output

The default output columns are:

`accountCode,currency,amount`

The output serializer supports a configured column list and writes UTF-8 CSV with a header. The default delimiter is a comma.

Output filenames follow this pattern:

`newcash_XXX_YYYYMMDD_HHMMSS.csv`

- `XXX` is the BackOffice code supplied to the CLI.
- The timestamp identifies the output filename generation time.

The filename pattern is an implementation convention; the official FRONT file contract, column mapping, and BackOffice codes must be confirmed with the relevant system specification before production integration.

## SQL Server persistence

The Cash pipeline uses the following tables:

| Table | Purpose |
| --- | --- |
| `dbo.CashLoadControl` | Register a source file and its load time |
| `dbo.CashStaging` | Store accepted Cash records for staging |
| `dbo.CashRejected` | Store rejected records and their rejection reasons |
| `dbo.T_MASTER_CASH` | Preserve master records, processing status and raw-record information |
| `dbo.CashOutputDelivery` | Persist the serialized output payload, SHA-256 hash, publication status and error details |
| `dbo.ETLExecution` | Execution audit schema, where configured by the application |

SQL schema scripts are maintained under `docs/sql/`. Apply the appropriate schema scripts to the target development database before running the pipeline. Verify the actual table definitions and database permissions for your environment.

The output-delivery state is tracked as `PENDING` or `PUBLISHED`. The publisher verifies the payload hash before publication. If a file already exists at the target path, the publisher verifies its contents rather than blindly overwriting it.

## Getting started

### Prerequisites

- JDK 21
- Apache Maven
- Git
- Microsoft SQL Server for Cash database integration
- Docker Desktop for the optional local SFTP integration test

Verify the environment:

```powershell
java -version
mvn -version
git --version
```

### Clone the repository

```powershell
git clone https://github.com/Alexis-miranda/flowforge-etl.git
cd flowforge-etl
```

### Build and test

Validate the Maven project:

```powershell
mvn validate
```

Run the connector module tests and required upstream modules:

```powershell
mvn -pl flowforge-connectors -am test
```

Build and verify all modules:

```powershell
mvn clean verify
```

Some integration tests are conditional and may be skipped unless the required services and environment variables are configured.

## Configuration

### SQL Server

The Cash SQL Server connector reads these environment variables:

- `FLOWFORGE_DB_JDBC_URL` — JDBC connection URL.
- `FLOWFORGE_DB_USER` — database username.
- `FLOWFORGE_DB_PASSWORD` — database password.

Example for PowerShell (replace the example values with your own settings):

```powershell
$env:FLOWFORGE_DB_JDBC_URL = "jdbc:sqlserver://localhost:1433;databaseName=FlowForgeETL_Dev;encrypt=true;trustServerCertificate=true"
$env:FLOWFORGE_DB_USER = "your_local_sql_user"
$env:FLOWFORGE_DB_PASSWORD = "your_local_sql_password"
```

The port, instance configuration, database name, encryption options and credentials must match your SQL Server installation. The application does not automatically load `.env` files; set variables in the shell, IDE run configuration or deployment environment.

`trustServerCertificate=true` is intended for local development and testing. For production, configure a trusted server certificate and certificate validation. Never commit actual credentials or print secrets in logs.

### SFTP

The SFTP connector uses Apache MINA SSHD. Server host keys are verified against the configured SSH `known_hosts` file.

For local integration testing, start the development SFTP server using the repository's Compose configuration. The integration test expects a server at `localhost:2222` and suitable test credentials and host-key configuration.

Do not disable host-key verification to work around connection errors.

## Running the Cash pipeline

The CLI class is:

`com.flowforge.etl.connectors.cli.CashPipelineCli`

Its arguments are:

```text
<input.csv> <backOfficeCode> <outputDir> [columns] [sourceFile]
```

- `input.csv`: path to the input CSV.
- `backOfficeCode`: BackOffice code used in the output filename.
- `outputDir`: destination directory for the FRONT CSV.
- `columns`: optional comma-separated output column names; defaults to `accountCode,currency,amount`.
- `sourceFile`: optional source-file identifier used for load and delivery tracking.

Build the project first:

```powershell
mvn -pl flowforge-connectors -am test
```

For local command-line execution, build the connector module and create or refresh its dependency classpath as required by the project build. Then run the CLI with the compiled classes and dependency JARs on the Java classpath.

Example invocation (replace paths and values with your local settings):

```powershell
java -cp "<connector-classes>;<core-classes>;<dependency-jars>" `
  com.flowforge.etl.connectors.cli.CashPipelineCli `
  ".\input\cash.csv" `
  "YOUR_BACKOFFICE_CODE" `
  ".\output"
```

The SQL Server environment variables must be set in the same PowerShell session before running the pipeline.

## Recovering pending output deliveries

If SQL Server contains deliveries in `PENDING` state because publication failed, the recovery CLI can retry them without rerunning ingestion.

The recovery entry point is:

`com.flowforge.etl.connectors.cli.CashOutputRecoveryCli`

From the repository root, after compiling the modules, run:

```powershell
$deps = (Get-Content .\flowforge-connectors\connectors-classpath.txt -Raw).Trim()
$cp = ".\flowforge-connectors\target\classes;.\flowforge-core\target\classes;$deps"
java -cp $cp com.flowforge.etl.connectors.cli.CashOutputRecoveryCli
```

The dependency classpath file must exist and contain the dependency JAR paths for the local build. The recovery command finds pending deliveries, verifies payload integrity, attempts publication and updates delivery status. It does not execute the extraction, validation or Cash persistence stages.

If no pending deliveries exist, the CLI reports that there are no pending FRONT output deliveries. Review the `LastError` field in `dbo.CashOutputDelivery` when troubleshooting a failed publication.

## Testing

### Standard test suite

```powershell
mvn -pl flowforge-connectors -am test
```

### SFTP integration test

Start the local SFTP test server and ensure its host key is available in your user's `known_hosts` file.

Enable the integration test for the current PowerShell session:

```powershell
$env:FLOWFORGE_SFTP_INTEGRATION = "true"
mvn -pl flowforge-connectors -am -Dtest=SftpIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Remove the variable when finished:

```powershell
Remove-Item Env:FLOWFORGE_SFTP_INTEGRATION -ErrorAction SilentlyContinue
```

The integration test transfers a file to the SFTP server, downloads it and verifies the contents.

## Project structure

```text
flowforge-etl/
├── pom.xml
├── flowforge-core/
│   └── src/
│       ├── main/java/
│       └── test/java/
├── flowforge-connectors/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/flowforge/etl/connectors/
│       │   ├── cli/
│       │   ├── file/csv/
│       │   ├── database/sqlserver/
│       │   ├── integration/
│       │   └── sftp/
│       └── test/java/
└── docs/
    ├── images/
    └── sql/
```

This is a simplified view; individual packages may contain additional classes and tests.

## Project status

**Under active development**

Implemented capabilities include the core execution engine, CSV extraction and serialization, Cash validation, SQL Server persistence, output-delivery tracking, publication recovery, SFTP transfers and SSH host-key verification.

The Cash pipeline and output-recovery path have been exercised in a development environment, and the Maven connector test suite has passed with one SFTP integration test skipped. This does not by itself constitute production certification.

Before production use, confirm the official FRONT contract, required field mappings, BackOffice identifiers, operational monitoring, database permissions and deployment-specific security settings.

## Roadmap

- [x] Core pipeline execution
- [x] Shared execution context
- [x] CSV extraction and serialization
- [x] Uppercase transformation
- [x] Execution status tracking
- [x] Maven multi-module architecture
- [x] SQL Server Cash persistence
- [x] Accepted/rejected record handling
- [x] FRONT output delivery tracking
- [x] Pending output recovery
- [x] SFTP transfer support
- [x] SSH host-key verification
- [ ] Confirm official FRONT specification and production mappings
- [ ] Additional transformation types
- [ ] RabbitMQ integration
- [ ] IBM MQ integration
- [ ] Spring Boot web interface
- [ ] Windows service deployment

## License

No license has been selected yet. Until a license is added to the repository, permissions for reuse and redistribution are not explicitly granted.

---

<p align="center">
  <sub>Built with Java ☕ and a focus on clean, modular ETL architecture.</sub>
</p>
