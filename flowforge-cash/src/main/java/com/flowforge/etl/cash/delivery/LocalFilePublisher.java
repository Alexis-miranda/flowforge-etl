package com.flowforge.etl.cash.delivery;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Publishes a stored delivery payload to the local filesystem.
 */
public class LocalFilePublisher {

    public Path publish(CashOutputDelivery delivery) throws IOException {
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
            return target;
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
                // Do not request replacement: existing files must never be overwritten.
                Files.move(temporary, target);
            } catch (AtomicMoveNotSupportedException exception) {
                // The filesystem may not support an atomic rename.
                Files.move(temporary, target);
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                // Another publisher may have created the target concurrently.
                verifyExistingFile(target, delivery.sha256());
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        return target;
    }

    private void verifyExistingFile(Path target, String expectedHash)
            throws IOException {

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

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by every Java implementation.
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
