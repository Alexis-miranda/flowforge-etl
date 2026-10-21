
package com.flowforge.etl.connectors.sftp;

import org.apache.sshd.sftp.client.SftpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SftpFileReader {

    private final SftpFileOperations sftpClient;

    public SftpFileReader(SftpFileOperations sftpClient) {
        this.sftpClient = sftpClient;
    }

    public void download(String remotePath, Path localPath)
            throws IOException {

        Path parent = localPath.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream input = sftpClient.read(remotePath);
             OutputStream output = Files.newOutputStream(localPath)) {

            input.transferTo(output);
        }
    }
}
