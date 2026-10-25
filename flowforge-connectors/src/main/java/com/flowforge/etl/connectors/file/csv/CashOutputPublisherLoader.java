package com.flowforge.etl.connectors.file.csv;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;

public class CashOutputPublisherLoader implements StepExecutor {

    @Override
    public StepType supportedType() {
        return StepType.LOAD;
    }

    @Override
    public String connectorKey() {
        return "cash-output-publisher";
    }

    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context) {

        String sourceFile = step.configuration().get("sourceFile");

        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required configuration: sourceFile");
        }

        Delivery delivery = readDelivery(sourceFile);

        if (delivery == null) {
            throw new IllegalStateException(
                    "No output delivery found for source: " + sourceFile);
        }

        if ("PUBLISHED".equals(delivery.status())) {
            return;
        }

        try {
            publish(delivery);
            markPublished(sourceFile);
            System.out.println("Published FRONT output: "
                    + Path.of(delivery.directory())
                            .resolve(delivery.fileName())
                            .toAbsolutePath().normalize());
        } catch (Exception exception) {
            recordError(sourceFile, exception.getMessage());
            throw new IllegalStateException(
                    "Failed to publish FRONT output for " + sourceFile,
                    exception);
        }
    }

    private Delivery readDelivery(String sourceFile) {
        String sql = """
                SELECT OutputDirectory, OutputFileName, CsvPayload,
                       PayloadSha256, DeliveryStatus
                FROM dbo.CashOutputDelivery
                WHERE SourceFile = ?
                """;

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, sourceFile);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                return new Delivery(
                        result.getString("OutputDirectory"),
                        result.getString("OutputFileName"),
                        result.getBytes("CsvPayload"),
                        result.getString("PayloadSha256"),
                        result.getString("DeliveryStatus")
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not read pending output delivery", exception);
        }
    }

    private void publish(Delivery delivery) throws Exception {
        Path directory = Path.of(delivery.directory())
                .toAbsolutePath().normalize();

        Path target = directory.resolve(delivery.fileName()).normalize();

        if (!target.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "Output file must be a direct child of its output directory");
        }

        if (delivery.payload() == null
                || delivery.sha256() == null
                || !delivery.sha256().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(
                    "Stored delivery payload or SHA-256 is invalid");
        }

        String actualPayloadHash = sha256(delivery.payload());

        if (!actualPayloadHash.equals(delivery.sha256())) {
            throw new IllegalStateException(
                    "Stored payload SHA-256 does not match its content");
        }

        Files.createDirectories(directory);

        if (Files.exists(target)) {
            verifyExistingFile(target, delivery.sha256());
            return;
        }

        Path temporary = directory.resolve(
                "." + delivery.fileName() + "." + UUID.randomUUID() + ".tmp");

        try {
            Files.write(
                    temporary,
                    delivery.payload(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            try {
                // No REPLACE_EXISTING: never intentionally overwrite a file.
                Files.move(temporary, target);
            } catch (AtomicMoveNotSupportedException exception) {
                // The filesystem may not support an atomic rename.
                // The move still must not request replacement.
                Files.move(temporary, target);
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                verifyExistingFile(target, delivery.sha256());
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void verifyExistingFile(Path target, String expectedHash)
            throws IOException, NoSuchAlgorithmException {

        if (!Files.isRegularFile(target)) {
            throw new IOException(
                    "Output path exists but is not a regular file: " + target);
        }

        String existingHash = sha256(Files.readAllBytes(target));

        if (!existingHash.equals(expectedHash)) {
            throw new IOException(
                    "Output file collision: existing file has different content: "
                            + target);
        }
    }

    private void markPublished(String sourceFile) {
        String sql = """
                UPDATE dbo.CashOutputDelivery
                SET DeliveryStatus = N'PUBLISHED',
                    PublishedAt = SYSUTCDATETIME(),
                    LastError = NULL
                WHERE SourceFile = ?
                  AND DeliveryStatus = N'PENDING'
                """;

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, sourceFile);
            int updated = statement.executeUpdate();

            if (updated != 1) {
                throw new IllegalStateException(
                        "Delivery was not pending when publication completed: "
                                + sourceFile);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "File was published but SQL delivery status could not be updated",
                    exception);
        }
    }

    private void recordError(String sourceFile, String message) {
        String sql = """
                UPDATE dbo.CashOutputDelivery
                SET LastError = ?
                WHERE SourceFile = ?
                  AND DeliveryStatus = N'PENDING'
                """;

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            String safeMessage = message == null
                    ? "Unknown publication error"
                    : message;

            statement.setString(1,
                    safeMessage.length() > 1000
                            ? safeMessage.substring(0, 1000)
                            : safeMessage);
            statement.setString(2, sourceFile);
            statement.executeUpdate();
        } catch (SQLException exception) {
            System.err.println(
                    "Could not record delivery error: "
                            + exception.getMessage());
        }
    }

    private String sha256(byte[] content)
            throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
    }

    private record Delivery(
            String directory,
            String fileName,
            byte[] payload,
            String sha256,
            String status) {
    }
}