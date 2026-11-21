package com.flowforge.etl.connectors.sftp;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClientFactory;
import java.nio.file.Path;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;

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
        try {
            return sftpClient.read(remotePath);
        } catch (IOException exception) {
            throw SftpFailureClassifier.toStepExecutionFailure(
                    "Failed to open remote SFTP file for reading: " + remotePath,
                    exception);
        }
    }

    @Override
    public java.io.OutputStream write(String remotePath) throws IOException {
        try {
            return sftpClient.write(remotePath);
        } catch (IOException exception) {
            throw SftpFailureClassifier.toStepExecutionFailure(
                    "Failed to open remote SFTP file for writing: " + remotePath,
                    exception);
        }
    }

    public static SftpClient connectWithPassword(
            String host,
            int port,
            String username,
            String password) throws IOException {

        Path knownHostsPath = Path.of(
                System.getProperty("user.home"),
                ".ssh",
                "known_hosts");

        SshClient sshClient = SshClient.setUpDefaultClient();

        sshClient.setServerKeyVerifier(
                new KnownHostsServerKeyVerifier(
                        RejectAllServerKeyVerifier.INSTANCE,
                        knownHostsPath));

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

        } catch (IOException | RuntimeException exception) {
            if (session != null) {
                session.close();
            }
            sshClient.stop();
            throw SftpFailureClassifier.toStepExecutionFailure(
                    "Failed to establish SFTP connection to " + host + ":" + port,
                    exception);
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
