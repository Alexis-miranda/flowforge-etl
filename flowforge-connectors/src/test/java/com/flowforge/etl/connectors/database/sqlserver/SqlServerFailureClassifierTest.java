package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.execution.FailureClassification;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlServerFailureClassifierTest {

    @Test
    void shouldClassifyConnectionFailuresAsRetryable() {
        SQLException exception =
                new SQLException("connection failed", "08S01", 0);

        assertEquals(FailureClassification.RETRYABLE,
                SqlServerFailureClassifier.classify(exception));
    }

    @Test
    void shouldClassifyDeadlocksAsRetryable() {
        SQLException exception =
                new SQLException("deadlock", "40001", 1205);

        assertEquals(FailureClassification.RETRYABLE,
                SqlServerFailureClassifier.classify(exception));
    }

    @Test
    void shouldClassifyDuplicateKeysAsRequiringHumanIntervention() {
        SQLException exception =
                new SQLException("duplicate key", "23000", 2627);

        assertEquals(FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SqlServerFailureClassifier.classify(exception));
    }

    @Test
    void shouldClassifyAuthenticationFailuresAsRequiringHumanIntervention() {
        SQLException exception =
                new SQLException("authentication failed", "28000", 18456);

        assertEquals(FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SqlServerFailureClassifier.classify(exception));
    }

    @Test
    void shouldPreferHumanInterventionWhenChainedExceptionContainsDuplicate() {
        SQLException connectionFailure =
                new SQLException("connection issue", "08S01", 0);
        SQLException duplicateFailure =
                new SQLException("duplicate key", "23000", 2627);
        connectionFailure.setNextException(duplicateFailure);

        assertEquals(FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                SqlServerFailureClassifier.classify(connectionFailure));
    }

    @Test
    void shouldReturnUnclassifiedForUnknownSqlErrors() {
        SQLException exception =
                new SQLException("unknown database failure", "HY000", 99999);

        assertEquals(FailureClassification.UNCLASSIFIED,
                SqlServerFailureClassifier.classify(exception));
    }

    @Test
    void shouldHandleCyclicChainedExceptions() {
        SQLException first = new SQLException("first", "HY000", 1);
        SQLException second = new SQLException("second", "HY000", 2);
        first.setNextException(second);
        second.setNextException(first);

        assertEquals(FailureClassification.UNCLASSIFIED,
                SqlServerFailureClassifier.classify(first));
    }

    @Test
    void shouldReturnUnclassifiedForNullException() {
        assertEquals(FailureClassification.UNCLASSIFIED,
                SqlServerFailureClassifier.classify(null));
    }
}
