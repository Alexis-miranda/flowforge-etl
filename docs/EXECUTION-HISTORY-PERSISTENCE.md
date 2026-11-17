# Execution history persistence

## What is persisted

The SQL Server execution repository stores the final execution record in
`dbo.ETLExecution` and the ordered step diagnostics in
`dbo.ETLExecutionStep`.

For each completed step, the history includes its identifier and name, final
status, start and finish timestamps, exception and cause details, skip reason,
and failure classification. The `StepOrder` column preserves the pipeline
order independently of step identifiers.

The repository restores this history when callers use
`findByExecutionId` or `findByPipelineId`. Snapshot-only queries continue
to return execution-level status and timestamps.

## Apply the SQL Server schema

Run the following script against the same database configured for FlowForge:

```text
docs/sql/04-pipeline-execution-audit-schema.sql
```

The script creates `dbo.ETLExecutionStep` and its index. It also retains the
existing `dbo.ETLExecution` definition and index creation for fresh databases.
Existing execution rows are preserved; they will have no step-history rows
because the previous implementation did not persist that information.

Apply this script before deploying this version. The repository writes to
`dbo.ETLExecutionStep` even when a completed result contains an empty history,
so the table must exist.

## Configure a local integration test

Set these environment variables in the process that runs Maven:

- `FLOWFORGE_DB_JDBC_URL`
- `FLOWFORGE_DB_USER`
- `FLOWFORGE_DB_PASSWORD`

The JDBC URL must point to the database where the audit schema was applied.
Use `.env.example` as a naming reference; do not commit real credentials.

Run the connector integration test and its module dependencies:

```powershell
mvn -pl flowforge-connectors -am -Dtest=SqlServerPipelineExecutionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Then run the full Cash test suite:

```powershell
mvn --batch-mode --no-transfer-progress -pl flowforge-cash -am test
```

The SQL Server integration test uses JUnit assumptions. If the three
environment variables are missing, it is skipped rather than exercising a
database. A successful CI job without SQL Server configuration must not be
reported as a successful SQL Server integration test.

## Persistence behavior and limits

- The execution row is first moved to its final state; the step history is
  then replaced inside a separate SQL transaction.
- Repeating the history save is safe: existing child rows for that execution
  are deleted and reinserted atomically.
- A failed history write rolls back the child-row replacement. The already
  persisted parent execution row remains in its final state and the save
  operation reports an error.
- Step history is currently persisted when execution finalization calls
  `save`; it is not streamed to SQL Server after every step.
- If the process terminates before finalization, the parent row can remain
  `RUNNING` and the full step history may not be available. Automatic stale
  execution recovery is not part of this change.
- Historical step details from before this schema change cannot be recreated;
  only execution-level audit data that already exists remains queryable.

## Verification status

The GitHub Actions run associated with commit
`5a2dee7c2e69c3a4796931895f59ec220f6b8428` completed successfully. Its workflow
runs Maven tests for `flowforge-cash` and upstream modules. The SQL Server
integration test is conditional on the database environment variables, so
the workflow result alone does not establish that a real SQL Server database
was tested. Run the integration command above against a configured database
to verify the SQL schema and JDBC behavior end to end.
