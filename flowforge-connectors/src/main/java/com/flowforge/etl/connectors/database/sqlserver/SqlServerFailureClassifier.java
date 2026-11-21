package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.execution.FailureClassification;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Classifies SQL Server JDBC failures using structured SQLState and vendor codes.
 *
 * <p>Unknown failures remain unclassified. This class does not perform retries.</p>
 */
public final class SqlServerFailureClassifier {

    private static final int DEADLOCK_ERROR_CODE = 1205;
    private static final int DUPLICATE_INDEX_ERROR_CODE = 2601;
    private static final int DUPLICATE_KEY_ERROR_CODE = 2627;

    private SqlServerFailureClassifier() {
    }

    public static FailureClassification classify(SQLException exception) {
        if (exception == null) {
            return FailureClassification.UNCLASSIFIED;
        }

        boolean retryable = false;
        Set<SQLException> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (SQLException current = exception;
             current != null && visited.add(current);
             current = current.getNextException()) {

            String sqlState = current.getSQLState();
            int errorCode = current.getErrorCode();

            if (errorCode == DUPLICATE_INDEX_ERROR_CODE
                    || errorCode == DUPLICATE_KEY_ERROR_CODE
                    || (sqlState != null && sqlState.startsWith("28"))) {
                return FailureClassification.REQUIRES_HUMAN_INTERVENTION;
            }

            if (errorCode == DEADLOCK_ERROR_CODE
                    || "40001".equals(sqlState)
                    || "08S01".equals(sqlState)
                    || "08006".equals(sqlState)) {
                retryable = true;
            }
        }

        return retryable
                ? FailureClassification.RETRYABLE
                : FailureClassification.UNCLASSIFIED;
    }
}
