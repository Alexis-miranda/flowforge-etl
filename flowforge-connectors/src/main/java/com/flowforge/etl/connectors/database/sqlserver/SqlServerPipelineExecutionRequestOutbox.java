package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SQL Server implementation of the pipeline request outbox.
 *
 * <p>The request payload and its PENDING outbox row are committed in one
 * database transaction. RabbitMQ is deliberately not contacted here.</p>
 */
public final class SqlServerPipelineExecutionRequestOutbox
        implements PipelineExecutionRequestOutbox {

    private static final String INSERT_REQUEST_SQL = """
            INSERT INTO dbo.ETLPipelineExecutionRequest
                (ExecutionId, PipelineType, RequestedAt, Payload)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_OUTBOX_SQL = """
            INSERT INTO dbo.ETLOutboxMessage
                (MessageId, ExecutionId, Status, CreatedAt, PublishAttempts)
            VALUES (?, ?, 'PENDING', SYSUTCDATETIME(), 0)
            """;

    private final PipelineExecutionRequestJsonCodec codec;

    public SqlServerPipelineExecutionRequestOutbox(
            PipelineExecutionRequestJsonCodec codec) {
        this.codec = Objects.requireNonNull(codec, "Codec must not be null");
    }

    @Override
    public void enqueue(PipelineExecutionRequest request) {
        Objects.requireNonNull(request, "Request must not be null");
        byte[] payload = codec.serialize(request);
        String executionId = request.executionId().toString();

        try (Connection connection = SqlServerConnectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(INSERT_REQUEST_SQL)) {
                    statement.setString(1, executionId);
                    statement.setString(2, request.pipelineType());
                    statement.setTimestamp(3, Timestamp.from(request.requestedAt()));
                    statement.setBytes(4, payload);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(INSERT_OUTBOX_SQL)) {
                    statement.setString(1, executionId);
                    statement.setString(2, executionId);
                    statement.executeUpdate();
                }

                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                if (isDuplicateKey(exception)) {
                    verifyIdenticalExistingRequest(executionId, payload);
                    return;
                }
                throw new IllegalStateException(
                        "Could not atomically enqueue pipeline execution request",
                        exception);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not open or close SQL Server request outbox transaction",
                    exception);
        }
    }

    private static void verifyIdenticalExistingRequest(
            String executionId, byte[] expectedPayload) {
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT Payload
                     FROM dbo.ETLPipelineExecutionRequest
                     WHERE ExecutionId = ?
                     """)) {
            statement.setString(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()
                        || !Arrays.equals(expectedPayload, resultSet.getBytes("Payload"))) {
                    throw new IllegalStateException(
                            "Execution ID already exists with different request content: "
                                    + executionId);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not verify an existing pipeline execution request",
                    exception);
        }
    }

    @Override
    public List<PipelineExecutionOutboxMessage> findPending(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be greater than zero");
        }

        String sql = """
                SELECT TOP (?) o.MessageId, o.CreatedAt, o.PublishAttempts,
                       o.LastError, r.Payload
                FROM dbo.ETLOutboxMessage o
                INNER JOIN dbo.ETLPipelineExecutionRequest r
                    ON r.ExecutionId = o.ExecutionId
                WHERE o.Status = 'PENDING'
                ORDER BY o.CreatedAt, o.MessageId
                """;
        List<PipelineExecutionOutboxMessage> messages = new ArrayList<>();

        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(new PipelineExecutionOutboxMessage(
                            UUID.fromString(resultSet.getString("MessageId")),
                            codec.deserialize(resultSet.getBytes("Payload")),
                            resultSet.getTimestamp("CreatedAt").toInstant(),
                            resultSet.getInt("PublishAttempts"),
                            resultSet.getString("LastError")));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not read pending pipeline execution outbox messages",
                    exception);
        }
        return List.copyOf(messages);
    }

    @Override
    public boolean markPublished(UUID messageId, Instant publishedAt) {
        Objects.requireNonNull(messageId, "Message ID must not be null");
        Objects.requireNonNull(publishedAt, "Published at must not be null");
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE dbo.ETLOutboxMessage
                     SET Status = 'PUBLISHED', PublishedAt = ?, LastError = NULL
                     WHERE MessageId = ? AND Status = 'PENDING'
                     """)) {
            statement.setTimestamp(1, Timestamp.from(publishedAt));
            statement.setString(2, messageId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not mark pipeline execution outbox message as published",
                    exception);
        }
    }

    @Override
    public boolean recordPublishFailure(UUID messageId, String errorMessage) {
        Objects.requireNonNull(messageId, "Message ID must not be null");
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("Error message must not be blank");
        }
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE dbo.ETLOutboxMessage
                     SET PublishAttempts = PublishAttempts + 1, LastError = ?
                     WHERE MessageId = ? AND Status = 'PENDING'
                     """)) {
            statement.setString(1, errorMessage);
            statement.setString(2, messageId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not record pipeline execution outbox publish failure",
                    exception);
        }
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
}
