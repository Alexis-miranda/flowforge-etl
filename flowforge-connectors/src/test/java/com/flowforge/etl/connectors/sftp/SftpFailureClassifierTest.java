package com.flowforge.etl.connectors.sftp;

import com.flowforge.etl.core.execution.FailureClassification;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SftpFailureClassifierTest {

    @Test
    void shouldClassifyConnectionTimeoutAsRetryable() {
        assertEquals(
                FailureClassification.RETRYABLE,
                SftpFailureClassifier.classify(
                        new java.net.SocketTimeoutException("timeout")));
    }

    @Test
    void shouldClassifyConnectionLostAsRetryable() {
        assertEquals(
                FailureClassification.RETRYABLE,
                SftpFailureClassifier.classify(
                        new SshException(
                                SshConstants.SSH2_DISCONNECT_CONNECTION_LOST)));
    }

    @Test
    void shouldClassifySftpConnectionLossAsRetryable() {
        assertEquals(
                FailureClassification.RETRYABLE,
                SftpFailureClassifier.classify(
                        new SftpException(
                                SftpConstants.SSH_FX_CONNECTION_LOST,
                                "connection lost")));
    }

    @Test
    void shouldRequireHumanInterventionForAuthenticationFailure() {
        assertEquals(
                FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SftpFailureClassifier.classify(
                        new SshException(
                                SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE)));
    }

    @Test
    void shouldRequireHumanInterventionForUntrustedHostKey() {
        assertEquals(
                FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SftpFailureClassifier.classify(
                        new SshException(
                                SshConstants.SSH2_DISCONNECT_HOST_KEY_NOT_VERIFIABLE)));
    }

    @Test
    void shouldRequireHumanInterventionForMissingRemoteFile() {
        assertEquals(
                FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SftpFailureClassifier.classify(
                        new SftpException(
                                SftpConstants.SSH_FX_NO_SUCH_FILE,
                                "missing")));
    }

    @Test
    void shouldRequireHumanInterventionForPermissionDenied() {
        assertEquals(
                FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SftpFailureClassifier.classify(
                        new SftpException(
                                SftpConstants.SSH_FX_PERMISSION_DENIED,
                                "denied")));
    }

    @Test
    void shouldClassifyUnknownHostAsHumanIntervention() {
        assertEquals(
                FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SftpFailureClassifier.classify(new UnknownHostException("host")));
    }

    @Test
    void shouldPreferHumanInterventionWhenCauseChainContainsDifferentFailures() {
        IOException failure = new IOException(
                "operation failed",
                new ConnectException("connection refused"));
        failure.addSuppressed(new SftpException(
                SftpConstants.SSH_FX_PERMISSION_DENIED,
                "denied"));

        assertEquals(
                FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SftpFailureClassifier.classify(failure));
    }

    @Test
    void shouldLeaveUnknownIoFailuresUnclassified() {
        assertEquals(
                FailureClassification.UNCLASSIFIED,
                SftpFailureClassifier.classify(new IOException("unknown")));
    }

    @Test
    void shouldLeaveNullUnclassified() {
        assertEquals(
                FailureClassification.UNCLASSIFIED,
                SftpFailureClassifier.classify(null));
    }
}
