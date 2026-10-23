package com.flowforge.etl.connectors.database.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;

/** Manual SQL Server connectivity check for local development. */
public class SqlServerConnectionTest {

    public static void main(String[] args) {
        if (!SqlServerConnectionFactory.isConfigured()) {
            System.err.println(
                    "Configure FLOWFORGE_DB_JDBC_URL, FLOWFORGE_DB_USER "
                            + "and FLOWFORGE_DB_PASSWORD before running this check.");
            return;
        }

        try (Connection connection = SqlServerConnectionFactory.openConnection()) {
            System.out.println("SQL Server connection successful.");
            System.out.println("Database: " + connection.getCatalog());
        } catch (SQLException | IllegalStateException exception) {
            // Do not print the exception message: drivers may include connection details.
            System.err.println("Could not connect to SQL Server. Check configuration and logs.");
        }
    }
}
