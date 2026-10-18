
# FlowForge ETL

<p align="center">
  <!-- PROJECT COVER IMAGE -->
<p align="center">
  <img src="docs/images/flowforge-cover.png"
       alt="FlowForge ETL"
       width="200" />
</p>
<p align="center">
  <strong>A modular ETL platform built with Java 21</strong>
  <br />
  Extract · Transform · Load
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white" alt="JUnit 5" />
  <img src="https://img.shields.io/badge/Status-In%20Development-yellow" alt="In development" />
</p>

---

##  About

**FlowForge ETL** is a modular and extensible platform for building and executing data pipelines.

The project provides a foundation for extracting, transforming, and loading data through configurable workflows, with a focus on clean architecture, testability, and maintainability.

##  Features

- **CSV Extraction** — read structured data from CSV files.
-  **Data Transformation** — transform selected column values to uppercase.
-  **CSV Loading** — write processed records to CSV files.
-  **Pipeline Execution** — execute pipeline steps sequentially in the defined order.
-  **Shared Execution Context** — pass data between pipeline steps.
-  **Execution Tracking** — track execution status, duration, and failure details.
-  **Automated Testing** — verify components with unit and integration tests.
-  **Modular Architecture** — keep domain, execution, extraction, transformation, and loading responsibilities separate.

##  Architecture

FlowForge ETL currently consists of the `flowforge-core` Maven module.

| Package | Responsibility |
| --- | --- |
| `domain` | Pipeline definitions, steps, configurations, and statuses |
| `execution` | Pipeline execution, context, results, and listeners |
| `extract` | CSV data extraction |
| `transform` | Data transformation |
| `load` | CSV output |

### ETL Workflow

<p align="center">
  <!-- ETL WORKFLOW IMAGE -->
  <!--  workflow image -->
  <img src="https://images.openai.com/static-rsc-4/p4qlWVH6dyswPsvh6eQUQPkLalh5CPMheViSKJk0vkQi3bj8GSerhyoW4AMldRydSzvsE3eW3237gA4ss9N0osiVE7o_hVOJXWrY6MbtLY_5HasZ5iidGxyeidzmIdp22gLF89Qc6hurKxZqao9o9wy5C1LSAcQxG_uj6zsyrKz9iAVF4nX6dSN0XPY0zb_3?purpose=fullsize" alt="FlowForge ETL Workflow: CSV Extraction, Transformation, and Loading" width="100%" />
</p>

The current workflow demonstrates how data moves through the pipeline:

1. **Extract** — read records from an input CSV file.
2. **Transform** — convert the selected column values to uppercase.
3. **Load** — write the processed records to an output CSV file.

## Technology Stack

| Technology | Purpose |
| --- | --- |
| Java 21 | Programming language |
| Apache Maven | Build and dependency management |
| Apache Commons CSV | CSV parsing and writing |
| JUnit Jupiter 5 | Automated testing |

###  Planned Technologies

- Spring Boot integration.
- Microsoft SQL Server connectivity.
- Additional data sources, transformations, and loaders.

## Getting Started

### Prerequisites

- JDK 21
- Apache Maven

Verify your environment:

```bash
java -version
mvn -version
```

### Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/flowforge-etl.git
cd flowforge-etl
```

Replace `YOUR_USERNAME` with your GitHub username.

### Validate the Project

```bash
mvn validate
```

### Build and Run Tests

```bash
mvn clean verify
```

This command compiles the project and runs the automated test suite.

##  Project Status

 **Under active development**

The current implementation supports CSV extraction, uppercase transformation, CSV loading, sequential pipeline execution, execution status tracking, and integration testing.

##  Roadmap

- [x] Core pipeline execution
- [x] CSV extraction
- [x] Uppercase transformation
- [x] CSV loading
- [x] Unit and integration tests
- [ ] Additional transformation types
- [ ] Microsoft SQL Server integration
- [ ] Spring Boot integration

##  License

No license has been selected yet.

---

<p align="center">
  <sub>Built with Java ☕ and a focus on clean, modular ETL architecture.</sub>
</p>