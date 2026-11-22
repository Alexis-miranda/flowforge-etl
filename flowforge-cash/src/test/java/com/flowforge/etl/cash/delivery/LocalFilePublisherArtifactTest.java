package com.flowforge.etl.cash.delivery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFilePublisherArtifactTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldPublishStableArtifactPerExecutionWithoutOverwritingOtherExecutions()
            throws Exception {
        byte[] payload = "immutable-cash-output".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload));
        CashOutputDelivery delivery = new CashOutputDelivery(
                tempDir.toString(), "front.csv", payload, hash, "PENDING");
        LocalFilePublisher publisher = new LocalFilePublisher();

        Path first = publisher.publish(delivery, "execution-one");
        Path firstRetry = publisher.publish(delivery, "execution-one");
        Path second = publisher.publish(delivery, "execution-two");

        assertTrue(first.startsWith(tempDir.resolve(".flowforge-artifacts")));
        assertNotEquals(first, second);
        assertEquals(first, firstRetry);
        assertArrayEquals(payload, Files.readAllBytes(first));
        assertArrayEquals(payload, Files.readAllBytes(second));
    }

    @Test
    void shouldRejectBlankExecutionId() {
        CashOutputDelivery delivery = new CashOutputDelivery(
                tempDir.toString(), "front.csv", new byte[]{1}, "0".repeat(64), "PENDING");

        assertThrows(IllegalArgumentException.class,
                () -> new LocalFilePublisher().publish(delivery, " "));
    }
}