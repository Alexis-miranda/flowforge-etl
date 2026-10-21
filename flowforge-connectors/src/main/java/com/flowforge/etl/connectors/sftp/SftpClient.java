
package com.flowforge.etl.connectors.sftp;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.time.Duration;

public final class SftpClient
        implements AutoCloseable, SftpFileOperations {

    private final SshClient sshClient;
    private final ClientSession session;
    private final org.apache.sshd.sftp.client.SftpClient sftpClient;

    private SftpClient(
            SshClient sshClient,
            ClientSession session,
            org.apache.sshd.sftp.client.SftpClient sftpClient) {
        this.sshClient = sshClient;
        this.session = session;
        this.sftpClient = sftpClient;
    }
    @Override
    public java.io.InputStream read(String remotePath) throws IOException {
        return sftpClient.read(remotePath);
    }

    @Override
    public java.io.OutputStream write(String remotePath) throws IOException {
        return sftpClient.write(remotePath);
    }

    public static SftpClient connectWithPassword(
            String host,
            int port,
            String username,
            String password) throws IOException {

        SshClient sshClient = SshClient.setUpDefaultClient();
        ClientSession session = null;

        try {
            sshClient.start();

            session = sshClient.connect(username, host, port)
                    .verify(Duration.ofSeconds(15))
                    .getSession();

            session.addPasswordIdentity(password);
            session.auth().verify(Duration.ofSeconds(15));

            var sftpClient = SftpClientFactory.instance()
                    .createSftpClient(session);

            return new SftpClient(sshClient, session, sftpClient);

        } catch (IOException | RuntimeException e) {
            if (session != null) {
                session.close();
            }
            sshClient.stop();
            throw e;
        }
    }

    public org.apache.sshd.sftp.client.SftpClient getSftpClient() {
        return sftpClient;
    }

    @Override
    public void close() throws IOException {
        try {
            sftpClient.close();
        } finally {
            try {
                session.close();
            } finally {
                sshClient.stop();
            }
        }
    }
}
