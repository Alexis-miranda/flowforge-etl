package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashOutputPublisherLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPublishPendingDeliveryAndMarkItPublished() throws Exception {
        byte[] payload = "accountCode,amount\nACC001,10.00\n"
                .getBytes(StandardCharsets.UTF_8);
        FakeRepository repository = new FakeRepository(
                delivery("output.csv", payload, "PENDING"));
        CashOutputPublisherLoader loader =
                new CashOutputPublisherLoader(repository, new LocalFilePublisher());

        loader.execute(step("source.csv"), new PipelineExecutionContext());

        assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve("output.csv")));
        assertEquals(1, repository.markPublishedCalls);
        assertEquals(0, repository.recordErrorCalls);
    }

    @Test
    void shouldAcceptExistingFileWhenItsHashMatches() throws Exception {
        byte[] payload = "same-content".getBytes(StandardCharsets.UTF_8);
        Files.write(tempDir.resolve("output.csv"), payload);
        FakeRepository repository = new FakeRepository(
                delivery("output.csv", payload, "PENDING"));

        new CashOutputPublisherLoader(repository, new LocalFilePublisher())
                .execute(step("source.csv"), new PipelineExecutionContext());

        assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve("output.csv")));
        assertEquals(1, repository.markPublishedCalls);
    }

    @Test
    void shouldRejectExistingFileWithDifferentContent() throws Exception {
        byte[] payload = "expected".getBytes(StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("output.csv"), "different");
        FakeRepository repository = new FakeRepository(
                delivery("output.csv", payload, "PENDING"));

        assertThrows(IllegalStateException.class,
                () -> new CashOutputPublisherLoader(
                        repository, new LocalFilePublisher())
                        .execute(step("source.csv"), new PipelineExecutionContext()));

        assertEquals(0, repository.markPublishedCalls);
        assertEquals(1, repository.recordErrorCalls);
    }

    @Test
    void shouldNotRepublishDeliveryAlreadyMarkedPublished() throws Exception {
        byte[] payload = "already-done".getBytes(StandardCharsets.UTF_8);
        FakeRepository repository = new FakeRepository(
                delivery("output.csv", payload, "PUBLISHED"));

        new CashOutputPublisherLoader(repository, new LocalFilePublisher())
                .execute(step("source.csv"), new PipelineExecutionContext());

        assertTrue(Files.notExists(tempDir.resolve("output.csv")));
        assertEquals(0, repository.markPublishedCalls);
        assertEquals(0, repository.recordErrorCalls);
    }

    @Test
    void shouldAllowRetryWhenSqlStatusUpdateFailsAfterFilePublication()
            throws Exception {
        byte[] payload = "retry-safe".getBytes(StandardCharsets.UTF_8);
        FakeRepository repository = new FakeRepository(
                delivery("output.csv", payload, "PENDING"));
        repository.failMarkPublished = true;

        CashOutputPublisherLoader loader =
                new CashOutputPublisherLoader(repository, new LocalFilePublisher());

        assertThrows(IllegalStateException.class,
                () -> loader.execute(
                        step("source.csv"), new PipelineExecutionContext()));
        assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve("output.csv")));

        repository.failMarkPublished = false;
        loader.execute(step("source.csv"), new PipelineExecutionContext());

        assertEquals(2, repository.markPublishedCalls);
        assertEquals(1, repository.recordErrorCalls);
        assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve("output.csv")));
    }

    @Test
    void shouldRejectPayloadWhenStoredHashDoesNotMatch() {
        byte[] payload = "tampered".getBytes(StandardCharsets.UTF_8);
        CashOutputDelivery invalid = new CashOutputDelivery(
                tempDir.toString(), "output.csv", payload, "0".repeat(64), "PENDING");
        FakeRepository repository = new FakeRepository(invalid);

        assertThrows(IllegalStateException.class,
                () -> new CashOutputPublisherLoader(
                        repository, new LocalFilePublisher())
                        .execute(step("source.csv"), new PipelineExecutionContext()));

        assertEquals(0, repository.markPublishedCalls);
        assertEquals(1, repository.recordErrorCalls);
    }

    @Test
    void shouldFailWhenDeliveryDoesNotExist() {
        FakeRepository repository = new FakeRepository(null);

        assertThrows(IllegalStateException.class,
                () -> new CashOutputPublisherLoader(
                        repository, new LocalFilePublisher())
                        .execute(step("source.csv"), new PipelineExecutionContext()));

        assertEquals(0, repository.recordErrorCalls);
    }

    private CashOutputDelivery delivery(
            String fileName,
            byte[] payload,
            String status) throws Exception {
        return new CashOutputDelivery(
                tempDir.toString(), fileName, payload, sha256(payload), status);
    }

    private String sha256(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload));
    }

    private PipelineStep step(String sourceFile) {
        return new PipelineStep(
                "publish-front",
                "Publish FRONT output",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of("sourceFile", sourceFile))
        );
    }

    private static final class FakeRepository
            implements CashOutputDeliveryRepository {

        private final CashOutputDelivery delivery;
        private int markPublishedCalls;
        private int recordErrorCalls;
        private boolean failMarkPublished;

        private FakeRepository(CashOutputDelivery delivery) {
            this.delivery = delivery;
        }

        @Override
        public CashOutputDelivery findBySourceFile(String sourceFile) {
            return delivery;
        }

        @Override
        public void markPublished(String sourceFile) {
            markPublishedCalls++;
            if (failMarkPublished) {
                throw new IllegalStateException("Simulated SQL update failure");
            }
        }

        @Override
        public void recordError(String sourceFile, String message) {
            recordErrorCalls++;
        }
    }
}
