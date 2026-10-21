package com.flowforge.etl.connectors.sftp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SftpFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadLocalFileToRemotePath() throws IOException {
        byte[] content = "id,name\n1,Alice\n".getBytes();
        ByteArrayOutputStream uploadedContent =
                new ByteArrayOutputStream();

        SftpFileOperations fakeSftp = new SftpFileOperations() {
            @Override
            public InputStream read(String remotePath) {
                throw new UnsupportedOperationException();
            }

            @Override
            public OutputStream write(String remotePath) {
                assertEquals("/remote/data.csv", remotePath);
                return uploadedContent;
            }
        };

        Path source = tempDir.resolve("data.csv");
        Files.write(source, content);

        SftpFileWriter writer = new SftpFileWriter(fakeSftp);
        writer.upload(source, "/remote/data.csv");

        assertArrayEquals(content, uploadedContent.toByteArray());
    }

    @Test
    void shouldThrowExceptionWhenLocalFileDoesNotExist() {
        SftpFileOperations fakeSftp = new SftpFileOperations() {
            @Override
            public InputStream read(String remotePath) {
                throw new UnsupportedOperationException();
            }

            @Override
            public OutputStream write(String remotePath) {
                throw new UnsupportedOperationException();
            }
        };

        Path missingFile = tempDir.resolve("missing.csv");
        SftpFileWriter writer = new SftpFileWriter(fakeSftp);

        assertThrows(
                IOException.class,
                () -> writer.upload(missingFile, "/remote/missing.csv"));
    }
}
