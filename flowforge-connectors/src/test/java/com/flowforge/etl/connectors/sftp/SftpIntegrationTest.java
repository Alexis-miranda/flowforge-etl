
package com.flowforge.etl.connectors.sftp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SftpIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIfEnvironmentVariable(
            named = "FLOWFORGE_SFTP_INTEGRATION",
            matches = "true")
    void shouldUploadAndDownloadFileThroughSftp() throws Exception {
        Path originalFile = tempDir.resolve("original.txt");
        Path downloadedFile = tempDir.resolve("downloaded.txt");

        byte[] expected = "Prueba SFTP de FlowForge"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Files.write(originalFile, expected);

        try (SftpClient client = SftpClient.connectWithPassword(
                "localhost",
                2222,
                "flowforge",
                "FlowForgeTest2026!")) {

            SftpFileWriter writer = new SftpFileWriter(client);
            writer.upload(originalFile, "/upload/integration-test.txt");

            SftpFileReader reader = new SftpFileReader(client);
            reader.download(
                    "/upload/integration-test.txt",
                    downloadedFile);
        }

        assertArrayEquals(expected, Files.readAllBytes(downloadedFile));
    }
}
