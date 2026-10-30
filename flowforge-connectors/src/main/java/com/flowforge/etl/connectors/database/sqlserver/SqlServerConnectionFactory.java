package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.execution.FailureClassification;
import com.flowforge.etl.core.execution.StepExecutionFailureException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Creates SQL Server connections from environment-based configuration. */
public final class SqlServerConnectionFactory {

    private static final String JDBC_URL_ENV = "FLOWFORGE_DB_JDBC_URL";
    private static final String USER_ENV = "FLOWFORGE_DB_USER";
    private static final String PASSWORD_ENV = "FLOWFORGE_DB_PASSWORD";

    private SqlServerConnectionFactory() {
    }

    /** Returns whether all required connection variables are configured. */
    public static boolean isConfigured() {
        return isSet(JDBC_URL_ENV) && isSet(USER_ENV) && isSet(PASSWORD_ENV);
    }

    /** Opens a connection without logging credentials or the JDBC URL. */
    public static Connection openConnection() throws SQLException {
        String jdbcUrl = requiredEnvironmentVariable(JDBC_URL_ENV);
        String user = requiredEnvironmentVariable(USER_ENV);
        String password = requiredEnvironmentVariable(PASSWORD_ENV);
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new StepExecutionFailureException(
                    FailureClassification.REQUIRES_HUMAN_INTERVENTION,
                    "Missing required environment variable: " + name);
        }
        return value;
    }

    private static boolean isSet(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
