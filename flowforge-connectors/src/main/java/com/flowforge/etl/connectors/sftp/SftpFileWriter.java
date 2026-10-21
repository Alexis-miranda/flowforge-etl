
package com.flowforge.etl.connectors.sftp;

import org.apache.sshd.sftp.client.SftpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SftpFileWriter {

    private final SftpFileOperations sftpClient;

    public SftpFileWriter(SftpFileOperations sftpClient) {
        this.sftpClient = sftpClient;
    }

    public void upload(Path localPath, String remotePath)
            throws IOException {

        if (!Files.isRegularFile(localPath)) {
            throw new IOException(
                    "Local file does not exist or is not a regular file: "
                            + localPath);
        }

        try (InputStream input = Files.newInputStream(localPath);
             OutputStream output = sftpClient.write(remotePath)) {

            input.transferTo(output);
        }
    }
}
