package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Persists worker leases in SQL Server.
 *
 * <p>The lease row is retained after release so its fencing token can never
 * be reset. SQL Server row locks and the execution ID primary key arbitrate
 * competing acquisitions.</p>
 */
public final class SqlServerPipelineExecutionLeaseRepository
        implements PipelineExecutionLeaseRepository {

    private static final String RECLAIM_EXPIRED_SQL = """
            UPDATE dbo.ETLExecutionLease
            SET OwnerId = ?, LeaseUntil = ?, FencingToken = FencingToken + 1
            OUTPUT INSERTED.ExecutionId, INSERTED.OwnerId,
                   INSERTED.LeaseUntil, INSERTED.FencingToken
            WHERE ExecutionId = ?
              AND LeaseUntil <= ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO dbo.ETLExecutionLease
                (ExecutionId, OwnerId, LeaseUntil, FencingToken)
            VALUES (?, ?, ?, 1)
            """;

    private static final String RENEW_SQL = """
            UPDATE dbo.ETLExecutionLease
            SET LeaseUntil = ?
            WHERE ExecutionId = ?
              AND OwnerId = ?
              AND FencingToken = ?
              AND LeaseUntil > ?
            """;

    private static final String RELEASE_SQL = """
            UPDATE dbo.ETLExecutionLease
            SET OwnerId = NULL, LeaseUntil = ?
            WHERE ExecutionId = ?
              AND OwnerId = ?
              AND FencingToken = ?
            """;

    @Override
    public Optional<PipelineExecutionLease> tryAcquire(
            String executionId,
            String ownerId,
            Instant now,
            Duration leaseDuration) {
        validateRequest(executionId, ownerId, now, leaseDuration);
        Instant leaseUntil = now.plus(leaseDuration);

        try (Connection connection = SqlServerConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PipelineExecutionLease> reclaimed =
                        reclaimExpired(connection, executionId, ownerId,
                                now, leaseUntil);
                if (reclaimed.isPresent()) {
                    connection.commit();
                    return reclaimed;
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(INSERT_SQL)) {
                    statement.setString(1, executionId);
                    statement.setString(2, ownerId);
                    statement.setTimestamp(3, Timestamp.from(leaseUntil));
                    statement.executeUpdate();
                }

                connection.commit();
                return Optional.of(new PipelineExecutionLease(
                        executionId, ownerId, leaseUntil, 1));
            } catch (SQLException exception) {
                rollback(connection, exception);
                if (isDuplicateKey(exception)) {
                    return Optional.empty();
                }
                throw new IllegalStateException(
                        "Could not acquire pipeline execution lease",
                        exception);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not acquire pipeline execution lease",
                    exception);
        }
    }

    private Optional<PipelineExecutionLease> reclaimExpired(
            Connection connection,
            String executionId,
            String ownerId,
            Instant now,
            Instant leaseUntil) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(RECLAIM_EXPIRED_SQL)) {
            statement.setString(1, ownerId);
            statement.setTimestamp(2, Timestamp.from(leaseUntil));
            statement.setString(3, executionId);
            statement.setTimestamp(4, Timestamp.from(now));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readLease(resultSet));
            }
        }
    }

    @Override
    public boolean renew(
            PipelineExecutionLease lease,
            Instant now,
            Duration leaseDuration) {
        validateLease(lease);
        validateTimeAndDuration(now, leaseDuration);
        Instant renewedUntil = now.plus(leaseDuration);

        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(RENEW_SQL)) {
            statement.setTimestamp(1, Timestamp.from(renewedUntil));
            statement.setString(2, lease.executionId());
            statement.setString(3, lease.ownerId());
            statement.setLong(4, lease.fencingToken());
            statement.setTimestamp(5, Timestamp.from(now));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not renew pipeline execution lease", exception);
        }
    }

    @Override
    public boolean release(
            PipelineExecutionLease lease,
            Instant releasedAt) {
        validateLease(lease);
        if (releasedAt == null) {
            throw new IllegalArgumentException("Release time must not be null");
        }

        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(RELEASE_SQL)) {
            statement.setTimestamp(1, Timestamp.from(releasedAt));
            statement.setString(2, lease.executionId());
            statement.setString(3, lease.ownerId());
            statement.setLong(4, lease.fencingToken());
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
            if (current.getErrorCode() == 2601
                    || current.getErrorCode() == 2627) {
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

    private static void validateRequest(
            String executionId,
            String ownerId,
            Instant now,
            Duration leaseDuration) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Lease owner ID must not be blank");
        }
        validateTimeAndDuration(now, leaseDuration);
    }

    private static void validateTimeAndDuration(
            Instant now,
            Duration leaseDuration) {
        if (now == null) {
            throw new IllegalArgumentException("Current time must not be null");
        }
        if (leaseDuration == null || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "Lease duration must be positive");
        }
        try {
            now.plus(leaseDuration);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Lease duration is too large", exception);
        }
    }

    private static void validateLease(PipelineExecutionLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
    }
}
