# FlowForge ETL

<p align="center">
  <img src="docs/images/flowforge-cover.png" alt="FlowForge ETL" width="200" />
</p>

<p align="center">
  <strong>A modular ETL platform built with Java 21</strong><br />
  Extract · Transform · Load
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Maven-Multi--Module-C71A36?logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white" alt="JUnit 5" />
  <img src="https://img.shields.io/badge/Status-In%20Development-yellow" alt="In development" />
</p>

---

## Overview

**FlowForge ETL** is a personal Java project exploring how to build a modular Extract, Transform, Load (ETL) platform. It separates pipeline execution from external connectors and processes data through configurable steps.

The current use case is a Cash data workflow: reading CSV files, validating records, separating accepted and rejected data, persisting results in SQL Server, and delivering generated FRONT CSV files. The project also includes SFTP transfers with SSH host-key verification.

Built with **Java 21, Maven, JUnit 5 and SQL Server**. This project is under active development and is not presented as production-certified software.

## Key features

- **Modular pipeline engine:** sequential execution, shared context, execution results and failure diagnostics.
- **Execution audit history:** SQL Server persistence and restoration of completed step-by-step execution diagnostics.
- **CSV processing:** extraction, validation, accepted/rejected record handling and configurable UTF-8 output.
- **SQL Server persistence:** Cash data, load information and output-delivery state.
- **Reliable file delivery:** SHA-256 payload verification and recovery of pending FRONT deliveries without rerunning ingestion.
- **Secure SFTP:** file upload/download with SSH host-key verification.
- **Automated tests:** unit tests and optional integration tests.

## Architecture

The project uses a Maven multi-module structure:

| Module | Responsibility |
| --- | --- |
| `flowforge-core` | Pipeline model, execution engine, shared context and generic abstractions |
| `flowforge-connectors` | Reusable CSV, SQL Server and SFTP connectors |
| `flowforge-cash` | Cash validation, persistence, FRONT delivery, pipeline composition and CLI |

This separation keeps reusable execution logic and technology-specific connectors independent from Cash business rules.

## Technology relevance

FlowForge demonstrates practical work with Java pipelines, ETL processing, SQL persistence, secure file transfer, execution tracking and connector-failure diagnosis.

**Kafka and IBM MQ are not currently integrated into FlowForge.** They remain learning and future integration goals, not implemented features.

## Build and test

**Prerequisites:** JDK 21, Apache Maven and Git. SQL Server is required for the Cash database workflow; Docker can be used for the optional SFTP integration test.

Clone the repository:

```bash
git clone https://github.com/Alexis-miranda/flowforge-etl.git
cd flowforge-etl
```

Run the Cash tests and required upstream modules:

```bash
mvn -pl flowforge-cash -am test
```

Build and verify all modules:

```bash
mvn clean verify
```

### Test information

The GitHub Actions workflow builds the Cash module and its Maven dependencies with Java 21. SQL Server integration tests are conditional: they are skipped when `FLOWFORGE_DB_JDBC_URL`, `FLOWFORGE_DB_USER` and `FLOWFORGE_DB_PASSWORD` are not configured. A green CI run therefore confirms the Maven build and tests that ran, but does not by itself prove connectivity to a real SQL Server instance.

For the SQL Server execution-audit schema and local verification steps, see [Execution history persistence](docs/EXECUTION-HISTORY-PERSISTENCE.md). The [end-to-end test report](docs/ETL-E2E-TEST-2026-10-25.md) documents a previous local Cash test, not a current CI result.

## Project status

**Under active development.**

Implemented capabilities include the core execution engine, CSV extraction and serialization, Cash validation, SQL Server persistence, output-delivery tracking and recovery, and SFTP transfers with SSH host-key verification.

Before production use, the official FRONT file contract, field mappings, BackOffice identifiers, operational monitoring, database permissions and deployment security settings must be confirmed.

## Roadmap

- [x] Core pipeline execution and shared context
- [x] CSV extraction, validation and serialization
- [x] SQL Server Cash persistence
- [x] Accepted/rejected record handling
- [x] FRONT output delivery tracking and recovery
- [x] SFTP transfer support and SSH host-key verification
- [ ] Confirm official FRONT specification and production mappings
- [ ] Additional transformation types
- [ ] RabbitMQ and IBM MQ integrations
- [ ] Spring Boot web interface
- [ ] Windows service deployment

## License

No license has been selected yet. Until a license is added, permissions for reuse and redistribution are not explicitly granted.

---

<p align="center">
  <sub>Built with Java ☕ and a focus on clean, modular ETL architecture.</sub>
</p>
