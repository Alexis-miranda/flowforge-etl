package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Optional;

/**
 * Persists worker leases in SQL Server.
 *
 * <p>SQL Server's UTC clock is authoritative for lease expiration, avoiding
 * disagreements between clocks on different worker machines. Lease rows are
 * retained after release so fencing tokens can never be reset.</p>
 */
public final class SqlServerPipelineExecutionLeaseRepository
        implements PipelineExecutionLeaseRepository {

    private static final String RECLAIM_EXPIRED_SQL = """
            UPDATE dbo.ETLExecutionLease
            SET OwnerId = ?, LeaseUntil = DATEADD(SECOND, ?, SYSUTCDATETIME()),
                FencingToken = FencingToken + 1
            OUTPUT INSERTED.ExecutionId, INSERTED.OwnerId,
                   INSERTED.LeaseUntil, INSERTED.FencingToken
            WHERE ExecutionId = ?
              AND LeaseUntil <= SYSUTCDATETIME()
            """;

    private static final String INSERT_SQL = """
            INSERT INTO dbo.ETLExecutionLease
                (ExecutionId, OwnerId, LeaseUntil, FencingToken)
            VALUES (?, ?, DATEADD(SECOND, ?, SYSUTCDATETIME()), 1)
            """;

    private static final String RENEW_SQL = """
            UPDATE dbo.ETLExecutionLease
            SET LeaseUntil = DATEADD(SECOND, ?, SYSUTCDATETIME())
            WHERE ExecutionId = ?
              AND OwnerId = ?
              AND FencingToken = ?
              AND LeaseUntil > SYSUTCDATETIME()
            """;

    private static final String RELEASE_SQL = """
            UPDATE dbo.ETLExecutionLease
            SET OwnerId = NULL, LeaseUntil = SYSUTCDATETIME()
            WHERE ExecutionId = ?
              AND OwnerId = ?
              AND FencingToken = ?
            """;

    @Override
    public Optional<PipelineExecutionLease> tryAcquire(
            String executionId, String ownerId, Duration leaseDuration) {
        int durationSeconds = validateRequest(executionId, ownerId, leaseDuration);

        try (Connection connection = SqlServerConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PipelineExecutionLease> reclaimed =
                        reclaimExpired(connection, executionId, ownerId, durationSeconds);
                if (reclaimed.isPresent()) {
                    connection.commit();
                    return reclaimed;
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(INSERT_SQL)) {
                    statement.setString(1, executionId);
                    statement.setString(2, ownerId);
                    statement.setInt(3, durationSeconds);
                    statement.executeUpdate();
                }

                // Read back SQL Server's clock-derived expiry, not a worker clock.
                Optional<PipelineExecutionLease> inserted =
                        findOwnedLease(connection, executionId, ownerId);
                connection.commit();
                return inserted;
            } catch (SQLException exception) {
                rollback(connection, exception);
                if (isDuplicateKey(exception)) {
                    return Optional.empty();
                }
                throw new IllegalStateException(
                        "Could not acquire pipeline execution lease", exception);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not acquire pipeline execution lease", exception);
        }
    }

    private Optional<PipelineExecutionLease> reclaimExpired(
            Connection connection,
            String executionId,
            String ownerId,
            int durationSeconds) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(RECLAIM_EXPIRED_SQL)) {
            statement.setString(1, ownerId);
            statement.setInt(2, durationSeconds);
            statement.setString(3, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readLease(resultSet))
                        : Optional.empty();
            }
        }
    }

    private Optional<PipelineExecutionLease> findOwnedLease(
            Connection connection, String executionId, String ownerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ExecutionId, OwnerId, LeaseUntil, FencingToken
                FROM dbo.ETLExecutionLease
                WHERE ExecutionId = ? AND OwnerId = ?
                """)) {
            statement.setString(1, executionId);
            statement.setString(2, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(readLease(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public boolean renew(PipelineExecutionLease lease, Duration leaseDuration) {
        validateLease(lease);
        int durationSeconds = validateDuration(leaseDuration);

        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(RENEW_SQL)) {
            statement.setInt(1, durationSeconds);
            statement.setString(2, lease.executionId());
            statement.setString(3, lease.ownerId());
            statement.setLong(4, lease.fencingToken());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not renew pipeline execution lease", exception);
        }
    }

    @Override
    public boolean release(PipelineExecutionLease lease) {
        validateLease(lease);
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(RELEASE_SQL)) {
            statement.setString(1, lease.executionId());
            statement.setString(2, lease.ownerId());
            statement.setLong(3, lease.fencingToken());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not release pipeline execution lease", exception);
        }
    }

    private static PipelineExecutionLease readLease(ResultSet resultSet)
            throws SQLException {
        return new PipelineExecutionLease(
                resultSet.getString("ExecutionId"),
                resultSet.getString("OwnerId"),
                resultSet.getTimestamp("LeaseUntil").toInstant(),
                resultSet.getLong("FencingToken"));
    }

    private static boolean isDuplicateKey(SQLException exception) {
        for (SQLException current = exception;
             current != null;
             current = current.getNextException()) {
            if (current.getErrorCode() == 2601 || current.getErrorCode() == 2627) {
                return true;
            }
        }
        return false;
    }

    private static void rollback(Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static int validateRequest(
            String executionId, String ownerId, Duration leaseDuration) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Lease owner ID must not be blank");
        }
        return validateDuration(leaseDuration);
    }

    private static int validateDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
        long seconds = duration.getSeconds();
        if (duration.getNano() > 0) {
            seconds++;
        }
        if (seconds < 1 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 1 second and "
                            + Integer.MAX_VALUE + " seconds");
        }
        return (int) seconds;
    }

    private static void validateLease(PipelineExecutionLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
    }
}
