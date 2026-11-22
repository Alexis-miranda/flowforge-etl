package com.flowforge.etl.cash.delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Publishes a stored delivery payload to the local filesystem. */
public class LocalFilePublisher {

    /**
     * Publishes an immutable artifact in a directory unique to the execution.
     * The execution identifier is hashed before it is used as a path segment.
     */
    public Path publish(CashOutputDelivery delivery, String executionId) throws IOException {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution id must not be blank");
        }
        Path root = Path.of(delivery.directory()).toAbsolutePath().normalize();
        String executionDirectory = sha256(executionId.getBytes(StandardCharsets.UTF_8));
        CashOutputDelivery artifact = new CashOutputDelivery(
                root.resolve(".flowforge-artifacts").resolve(executionDirectory).toString(),
                delivery.fileName(), delivery.payload(), delivery.sha256(), delivery.status(),
                delivery.publishedPath(), delivery.publishedSha256(),
                delivery.publishedExecutionId());
        return publish(artifact);
    }

    /** Legacy publication path retained for direct local recovery callers. */
    public Path publish(CashOutputDelivery delivery) throws IOException {
        Path directory = Path.of(delivery.directory()).toAbsolutePath().normalize();
        Path target = directory.resolve(delivery.fileName()).normalize();
        if (!target.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "Output file must be a direct child of its output directory");
        }
        if (delivery.payload() == null || delivery.sha256() == null
                || !delivery.sha256().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Stored delivery payload or SHA-256 is invalid");
        }
        String actualPayloadHash = sha256(delivery.payload());
        if (!actualPayloadHash.equals(delivery.sha256())) {
            throw new IllegalStateException("Stored payload SHA-256 does not match its content");
        }
        Files.createDirectories(directory);
        if (Files.exists(target)) {
            verifyExistingFile(target, delivery.sha256());
            return target;
        }
        Path temporary = directory.resolve(
                "." + delivery.fileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, delivery.payload(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                verifyExistingFile(target, delivery.sha256());
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    private void verifyExistingFile(Path target, String expectedHash) throws IOException {
        if (!Files.isRegularFile(target)) {
            throw new IOException("Output path exists but is not a regular file: " + target);
        }
        String existingHash = sha256(Files.readAllBytes(target));
        if (!existingHash.equals(expectedHash)) {
            throw new IOException("Output file collision: existing file has different content: "
                    + target);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}