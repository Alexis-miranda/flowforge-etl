package com.flowforge.etl.connectors.sftp;

import com.flowforge.etl.core.execution.FailureClassification;
import com.flowforge.etl.core.execution.StepExecutionFailureException;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Classifies Apache MINA SSHD failures using structured exception types and codes.
 *
 * <p>Unknown failures remain unclassified. This class never retries an operation.</p>
 */
final class SftpFailureClassifier {

    private SftpFailureClassifier() {
    }

    static FailureClassification classify(Throwable exception) {
        if (exception == null) {
            return FailureClassification.UNCLASSIFIED;
        }

        boolean retryable = false;
        Set<Throwable> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.add(exception);

        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            FailureClassification classification = classifySingle(current);
            if (classification == FailureClassification.REQUIRES_HUMAN_INTERVENTION) {
                return classification;
            }
            if (classification == FailureClassification.RETRYABLE) {
                retryable = true;
            }

            if (current.getCause() != null && !visited.contains(current.getCause())) {
                pending.addLast(current.getCause());
            }
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed != null && !visited.contains(suppressed)) {
                    pending.addLast(suppressed);
                }
            }
        }

        return retryable
                ? FailureClassification.RETRYABLE
                : FailureClassification.UNCLASSIFIED;
    }

    static StepExecutionFailureException toStepExecutionFailure(
            String message,
            Throwable cause) {
        return new StepExecutionFailureException(
                classify(cause),
                message,
                cause);
    }

    private static FailureClassification classifySingle(Throwable exception) {
        if (exception instanceof SftpException sftpException) {
            return classifySftpStatus(sftpException.getStatus());
        }

        if (exception instanceof SshException sshException) {
            return classifySshDisconnect(sshException.getDisconnectCode());
        }

        if (exception instanceof UnknownHostException) {
            return FailureClassification.REQUIRES_HUMAN_INTERVENTION;
        }

        if (exception instanceof SocketTimeoutException
                || exception instanceof ConnectException
                || exception instanceof NoRouteToHostException
                || exception instanceof SocketException) {
            return FailureClassification.RETRYABLE;
        }

        return FailureClassification.UNCLASSIFIED;
    }

    private static FailureClassification classifySshDisconnect(int code) {
        return switch (code) {
            case SshConstants.SSH2_DISCONNECT_HOST_KEY_NOT_VERIFIABLE,
                    SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE,
                    SshConstants.SSH2_DISCONNECT_ILLEGAL_USER_NAME ->
                    FailureClassification.REQUIRES_HUMAN_INTERVENTION;
            case SshConstants.SSH2_DISCONNECT_CONNECTION_LOST,
                    SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS ->
                    FailureClassification.RETRYABLE;
            default -> FailureClassification.UNCLASSIFIED;
        };
    }

    private static FailureClassification classifySftpStatus(int status) {
        return switch (status) {
            case SftpConstants.SSH_FX_NO_CONNECTION,
                    SftpConstants.SSH_FX_CONNECTION_LOST ->
                    FailureClassification.RETRYABLE;
            case SftpConstants.SSH_FX_NO_SUCH_FILE,
                    SftpConstants.SSH_FX_PERMISSION_DENIED,
                    SftpConstants.SSH_FX_OP_UNSUPPORTED,
                    SftpConstants.SSH_FX_NO_SUCH_PATH,
                    SftpConstants.SSH_FX_FILE_ALREADY_EXISTS,
                    SftpConstants.SSH_FX_WRITE_PROTECT,
                    SftpConstants.SSH_FX_NO_SPACE_ON_FILESYSTEM,
                    SftpConstants.SSH_FX_QUOTA_EXCEEDED,
                    SftpConstants.SSH_FX_NOT_A_DIRECTORY,
                    SftpConstants.SSH_FX_INVALID_FILENAME,
                    SftpConstants.SSH_FX_FILE_IS_A_DIRECTORY ->
                    FailureClassification.REQUIRES_HUMAN_INTERVENTION;
            default -> FailureClassification.UNCLASSIFIED;
        };
    }
}
