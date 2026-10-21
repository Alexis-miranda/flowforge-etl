
package com.flowforge.etl.connectors.database.sqlserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqlServerConnectionTest {

    public static void main(String[] args) {
        String url =
                "jdbc:sqlserver://localhost;"
                        + "instanceName=MSSQLSERVER01;"
                        + "databaseName=FlowForgeETL_Dev;"
                        + "encrypt=true;"
                        + "trustServerCertificate=true;";

        String user = System.getenv("FLOWFORGE_DB_USER");
        String password = System.getenv("FLOWFORGE_DB_PASSWORD");

        if (user == null || password == null) {
            System.err.println(
                    "Faltan las variables FLOWFORGE_DB_USER "
                            + "o FLOWFORGE_DB_PASSWORD."
            );
            return;
        }

        try (Connection connection =
                     DriverManager.getConnection(url, user, password)) {

            System.out.println("¡Conexión con SQL Server correcta!");
            System.out.println(
                    "Base de datos: " + connection.getCatalog()
            );

        } catch (SQLException e) {
            System.err.println("No se pudo conectar con SQL Server.");
            System.err.println("Error: " + e.getMessage());
        }
    }
}
