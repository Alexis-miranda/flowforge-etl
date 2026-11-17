package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.FailureClassification;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.execution.StepExecutionRecord;
import com.flowforge.etl.core.execution.StepExecutionStatus;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores pipeline execution audit data in SQL Server.
 *
 * Each operation uses its own connection, independently of the business
 * data-loading transaction. Completed step diagnostics are stored in
 * dbo.ETLExecutionStep and loaded whenever a completed result is queried.
 */
public final class SqlServerPipelineExecutionRepository
        implements PipelineExecutionRepository {

    private static final String INSERT_SQL = """
            INSERT INTO dbo.ETLExecution
                (ExecutionId, PipelineId, Status, FailedStepId,
                 ErrorMessage, StartedAt, FinishedAt)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_RUNNING_SQL = """
            UPDATE dbo.ETLExecution
            SET Status = ?, FailedStepId = ?, ErrorMessage = ?,
                StartedAt = ?, FinishedAt = ?
            WHERE ExecutionId = ?
              AND PipelineId = ?
              AND Status = 'RUNNING'
            """;

    private static final String VERIFY_RESULT_SQL = """
        SELECT COUNT(*)
        FROM dbo.ETLExecution
        WHERE ExecutionId = ?
          AND PipelineId = ?
          AND Status = ?
          AND (FailedStepId = ? OR (FailedStepId IS NULL AND ? IS NULL))
          AND (ErrorMessage = ? OR (ErrorMessage IS NULL AND ? IS NULL))
          AND StartedAt = ?
          AND FinishedAt = ?
        """;

    private static final String INSERT_STEP_SQL = """
            INSERT INTO dbo.ETLExecutionStep
                (ExecutionId, StepOrder, StepId, StepName, Status,
                 StartedAt, FinishedAt, ExceptionType, ErrorMessage,
                 CauseType, CauseMessage, SkipReason, FailureClassification)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String DELETE_STEPS_SQL = """
            DELETE FROM dbo.ETLExecutionStep WHERE ExecutionId = ?
            """;

    private static final String FIND_SNAPSHOT_SQL = """
            SELECT ExecutionId, PipelineId, Status, FailedStepId,
                   ErrorMessage, StartedAt, FinishedAt
            FROM dbo.ETLExecution
            WHERE ExecutionId = ?
            """;
    private static final String FIND_SNAPSHOTS_BY_PIPELINE_SQL = """
        SELECT ExecutionId, PipelineId, Status, FailedStepId,
               ErrorMessage, StartedAt, FinishedAt
        FROM dbo.ETLExecution
        WHERE PipelineId = ?
        ORDER BY StartedAt DESC
        """;
    private static final String FIND_RESULTS_BY_PIPELINE_SQL = """
            SELECT ExecutionId, PipelineId, Status, FailedStepId,
                   ErrorMessage, StartedAt, FinishedAt
            FROM dbo.ETLExecution
            WHERE PipelineId = ?
              AND Status IN ('SUCCEEDED', 'FAILED')
            ORDER BY StartedAt DESC
            """;

    private static final String FIND_RESULT_SQL = """
            SELECT ExecutionId, PipelineId, Status, FailedStepId,
                   ErrorMessage, StartedAt, FinishedAt
            FROM dbo.ETLExecution
            WHERE ExecutionId = ?
              AND Status IN ('SUCCEEDED', 'FAILED')
            """;

    private static final String FIND_STEPS_SQL = """
            SELECT StepId, StepName, Status, StartedAt, FinishedAt,
                   ExceptionType, ErrorMessage, CauseType, CauseMessage,
                   SkipReason, FailureClassification
            FROM dbo.ETLExecutionStep
            WHERE ExecutionId = ?
            ORDER BY StepOrder
            """;

    @Override
    public void start(PipelineExecutionSnapshot snapshot) {
        if (snapshot.status() != PipelineStatus.RUNNING) {
            throw new IllegalArgumentException(
                    "Initial execution snapshot must have RUNNING status");
        }

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            bindSnapshot(statement, snapshot);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not start pipeline execution audit record",
                    exception);
        }
    }

    @Override
    public void update(PipelineExecutionSnapshot snapshot) {
        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(UPDATE_RUNNING_SQL)) {

            statement.setString(1, snapshot.status().name());
            statement.setString(2, snapshot.failedStepId());
            statement.setString(3, snapshot.errorMessage());
            statement.setTimestamp(4, Timestamp.from(snapshot.startedAt()));
            setTimestamp(statement, 5, snapshot.finishedAt());
            statement.setString(6, snapshot.executionId());
            statement.setString(7, snapshot.pipelineId());

            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "Execution audit record was not found, "
                                + "did not match the pipeline, or was "
                                + "already finished: "
                                + snapshot.executionId());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not update pipeline execution audit record",
                    exception);
        }
    }

    /**
     * Persists the step history for a completed execution.
     *
     * The operation replaces the history for this execution in one database
     * transaction, making a retry safe if a previous attempt was interrupted.
     * The parent execution row must already have been updated by update().
     */
    @Override
    public void save(PipelineExecutionResult result) {
        try (Connection connection =
                     SqlServerConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                verifyPersistedResult(connection, result);
                try (PreparedStatement delete =
                             connection.prepareStatement(DELETE_STEPS_SQL)) {
                    delete.setString(1, result.executionId());
                    delete.executeUpdate();
                }

                try (PreparedStatement insert =
                             connection.prepareStatement(INSERT_STEP_SQL)) {
                    List<StepExecutionRecord> history = result.stepHistory();
                    for (int index = 0; index < history.size(); index++) {
                        bindStep(insert, result.executionId(), index + 1,
                                history.get(index));
                        insert.addBatch();
                    }
                    if (!history.isEmpty()) {
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not persist pipeline execution step history",
                    exception);
        }
    }

    private static void verifyPersistedResult(
            Connection connection,
            PipelineExecutionResult result) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(VERIFY_RESULT_SQL)) {
            statement.setString(1, result.executionId());
            statement.setString(2, result.pipelineId());
            statement.setString(3, result.status().name());
            statement.setString(4, result.failedStepId());
            statement.setString(5, result.failedStepId());
            statement.setString(6, result.errorMessage());
            statement.setString(7, result.errorMessage());
            statement.setTimestamp(8, Timestamp.from(result.startedAt()));
            statement.setTimestamp(9, Timestamp.from(result.finishedAt()));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new IllegalStateException(
                            "Persisted execution result does not match: "
                                    + result.executionId());
                }
            }
        }
    }

    @Override
    public Optional<PipelineExecutionSnapshot> findSnapshotByExecutionId(
            String executionId) {
        requireText(executionId, "Execution ID");

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_SNAPSHOT_SQL)) {

            statement.setString(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readSnapshot(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not find pipeline execution snapshot",
                    exception);
        }
    }

    @Override
    public List<PipelineExecutionSnapshot> findSnapshotsByPipelineId(
            String pipelineId) {
        requireText(pipelineId, "Pipeline ID");
        List<PipelineExecutionSnapshot> snapshots = new ArrayList<>();

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_SNAPSHOTS_BY_PIPELINE_SQL)) {
            statement.setString(1, pipelineId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snapshots.add(readSnapshot(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not find pipeline execution snapshots",
                    exception);
        }
        return List.copyOf(snapshots);
    }

    @Override
    public List<PipelineExecutionResult> findByPipelineId(
            String pipelineId) {
        requireText(pipelineId, "Pipeline ID");
        List<PipelineExecutionResult> results = new ArrayList<>();

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_RESULTS_BY_PIPELINE_SQL)) {
            statement.setString(1, pipelineId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(readResultHeader(resultSet));
                }
            }
            List<PipelineExecutionResult> hydrated = new ArrayList<>();
            for (PipelineExecutionResult header : results) {
                hydrated.add(withStepHistory(connection, header));
            }
            return List.copyOf(hydrated);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not find pipeline execution results",
                    exception);
        }
    }

    @Override
    public Optional<PipelineExecutionResult> findByExecutionId(
            String executionId) {
        requireText(executionId, "Execution ID");

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_RESULT_SQL)) {
            statement.setString(1, executionId);
            PipelineExecutionResult header;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                header = readResultHeader(resultSet);
            }
            return Optional.of(withStepHistory(connection, header));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not find pipeline execution result",
                    exception);
        }
    }

    private static void bindSnapshot(
            PreparedStatement statement,
            PipelineExecutionSnapshot snapshot) throws SQLException {
        statement.setString(1, snapshot.executionId());
        statement.setString(2, snapshot.pipelineId());
        statement.setString(3, snapshot.status().name());
        statement.setString(4, snapshot.failedStepId());
        statement.setString(5, snapshot.errorMessage());
        statement.setTimestamp(6, Timestamp.from(snapshot.startedAt()));
        setTimestamp(statement, 7, snapshot.finishedAt());
    }

    private static void bindStep(
            PreparedStatement statement,
            String executionId,
            int order,
            StepExecutionRecord step) throws SQLException {
        statement.setString(1, executionId);
        statement.setInt(2, order);
        statement.setString(3, step.stepId());
        statement.setString(4, step.stepName());
        statement.setString(5, step.status().name());
        setTimestamp(statement, 6, step.startedAt());
        statement.setTimestamp(7, Timestamp.from(step.finishedAt()));
        statement.setString(8, step.exceptionType());
        statement.setString(9, step.errorMessage());
        statement.setString(10, step.causeType());
        statement.setString(11, step.causeMessage());
        statement.setString(12, step.skipReason());
        statement.setString(13, step.failureClassification() == null
                ? null : step.failureClassification().name());
    }

    private static void setTimestamp(
            PreparedStatement statement,
            int index,
            java.time.Instant instant) throws SQLException {
        if (instant == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(instant));
        }
    }

    private static PipelineExecutionSnapshot readSnapshot(
            ResultSet resultSet) throws SQLException {
        Timestamp finishedAt = resultSet.getTimestamp("FinishedAt");
        return new PipelineExecutionSnapshot(
                resultSet.getString("ExecutionId"),
                resultSet.getString("PipelineId"),
                PipelineStatus.valueOf(resultSet.getString("Status")),
                resultSet.getString("FailedStepId"),
                resultSet.getString("ErrorMessage"),
                resultSet.getTimestamp("StartedAt").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant());
    }

    private static PipelineExecutionResult readResultHeader(
            ResultSet resultSet) throws SQLException {
        Timestamp finishedAt = resultSet.getTimestamp("FinishedAt");
        return new PipelineExecutionResult(
                resultSet.getString("ExecutionId"),
                resultSet.getString("PipelineId"),
                PipelineStatus.valueOf(resultSet.getString("Status")),
                resultSet.getString("FailedStepId"),
                resultSet.getString("ErrorMessage"),
                resultSet.getTimestamp("StartedAt").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant());
    }

    private static PipelineExecutionResult withStepHistory(
            Connection connection,
            PipelineExecutionResult header) throws SQLException {
        return new PipelineExecutionResult(
                header.executionId(),
                header.pipelineId(),
                header.status(),
                header.failedStepId(),
                header.errorMessage(),
                header.startedAt(),
                header.finishedAt(),
                readStepHistory(connection, header.executionId()));
    }

    private static List<StepExecutionRecord> readStepHistory(
            Connection connection,
            String executionId) throws SQLException {
        List<StepExecutionRecord> history = new ArrayList<>();
        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_STEPS_SQL)) {
            statement.setString(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp startedAt = resultSet.getTimestamp("StartedAt");
                    Timestamp finishedAt = resultSet.getTimestamp("FinishedAt");
                    String classification =
                            resultSet.getString("FailureClassification");
                    history.add(new StepExecutionRecord(
                            resultSet.getString("StepId"),
                            resultSet.getString("StepName"),
                            StepExecutionStatus.valueOf(
                                    resultSet.getString("Status")),
                            startedAt == null ? null : startedAt.toInstant(),
                            finishedAt.toInstant(),
                            resultSet.getString("ExceptionType"),
                            resultSet.getString("ErrorMessage"),
                            resultSet.getString("CauseType"),
                            resultSet.getString("CauseMessage"),
                            resultSet.getString("SkipReason"),
                            classification == null ? null
                                    : FailureClassification.valueOf(classification)));
                }
            }
        }
        return List.copyOf(history);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
    }
}
