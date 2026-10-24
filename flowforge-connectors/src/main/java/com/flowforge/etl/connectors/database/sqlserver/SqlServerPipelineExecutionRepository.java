
package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
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
 * data-loading transaction.
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

    private static final String SAVE_RESULT_SQL = """
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

    private static final String FIND_SNAPSHOT_SQL = """
            SELECT ExecutionId, PipelineId, Status, FailedStepId,
                   ErrorMessage, StartedAt, FinishedAt
            FROM dbo.ETLExecution
            WHERE ExecutionId = ?
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
            setFinishedAt(statement, 5, snapshot.finishedAt());
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

    @Override
    public void save(PipelineExecutionResult result) {
        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SAVE_RESULT_SQL)) {

            statement.setString(1, result.executionId());
            statement.setString(2, result.pipelineId());
            statement.setString(3, result.status().name());

            // FailedStepId: puede ser NULL.
            statement.setString(4, result.failedStepId());
            statement.setString(5, result.failedStepId());

            // ErrorMessage: puede ser NULL.
            statement.setString(6, result.errorMessage());
            statement.setString(7, result.errorMessage());

            statement.setTimestamp(
                    8, Timestamp.from(result.startedAt()));
            statement.setTimestamp(
                    9, Timestamp.from(result.finishedAt()));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new IllegalStateException(
                            "Persisted execution result does not match: "
                                    + result.executionId());
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not verify pipeline execution result",
                    exception);
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
                    results.add(readResult(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not find pipeline execution results",
                    exception);
        }

        return List.copyOf(results);
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

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readResult(resultSet));
            }
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
        setFinishedAt(statement, 7, snapshot.finishedAt());
    }

    private static void setFinishedAt(
            PreparedStatement statement,
            int index,
            java.time.Instant finishedAt) throws SQLException {

        if (finishedAt == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(finishedAt));
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

    private static PipelineExecutionResult readResult(
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

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
    }
}
