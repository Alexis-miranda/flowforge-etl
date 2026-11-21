package com.flowforge.etl.cash.cli;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.cash.delivery.CashOutputRecoveryService;

/**
 * Retries pending FRONT output deliveries without reloading cash transactions.
 */
public final class CashOutputRecoveryCli {

    private CashOutputRecoveryCli() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 0) {
                System.err.println(
                        "Usage: CashOutputRecoveryCli (no arguments)");
                System.exit(2);
                return;
            }

            int failures = runRecovery();

            if (failures > 0) {
                System.err.println(
                        "Recovery finished with " + failures + " failure(s).");
                System.exit(1);
            }
        } catch (Exception exception) {
            System.err.println(
                    "Cash output recovery failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    static int runRecovery() {
        if (!SqlServerConnectionFactory.isConfigured()) {
            throw new IllegalStateException(
                    "SQL Server environment variables are not configured.");
        }

        CashOutputRecoveryService.RecoveryResult result =
                new CashOutputRecoveryService().recoverPending();

        if (result.total() == 0) {
            System.out.println("No pending FRONT output deliveries.");
            return 0;
        }

        System.out.println(
                "Found " + result.total()
                        + " pending FRONT output delivery/deliveries.");

        result.recoveredSources().forEach(sourceFile ->
                System.out.println("Recovered delivery: " + sourceFile));

        result.failures().forEach(failure ->
                System.err.println(
                        "Could not recover " + failure.sourceFile() + ": "
                                + failure.message()));

        System.out.println(
                "Recovery summary: total=" + result.total()
                        + ", failed=" + result.failed()
                        + ", succeeded=" + result.succeeded());

        return result.failed();
    }
}
