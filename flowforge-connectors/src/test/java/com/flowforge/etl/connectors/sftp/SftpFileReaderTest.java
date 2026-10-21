package com.flowforge.etl.connectors.sftp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SftpFileReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDownloadRemoteFileToLocalPath() throws IOException {
        byte[] content = "id,name\n1,Alice\n".getBytes();

        SftpFileOperations fakeSftp = new SftpFileOperations() {
            @Override
            public InputStream read(String remotePath) {
                assertEquals("/remote/data.csv", remotePath);
                return new ByteArrayInputStream(content);
            }

            @Override
            public OutputStream write(String remotePath) {
                throw new UnsupportedOperationException();
            }
        };

        Path destination = tempDir.resolve("nested/data.csv");

        SftpFileReader reader = new SftpFileReader(fakeSftp);
        reader.download("/remote/data.csv", destination);

        assertTrue(Files.exists(destination));
        assertEquals(
                "id,name\n1,Alice\n",
                Files.readString(destination));
    }
}