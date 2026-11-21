-- FlowForge ETL - Cash reconciliation schema
-- Target database: FlowForgeETL_Dev
-- This script creates new tables only. It does not alter CashStaging.

IF OBJECT_ID(N'dbo.CashExpectedTransactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CashExpectedTransactions
    (
        Id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_CashExpectedTransactions PRIMARY KEY,
        AdjustmentId NVARCHAR(100) NOT NULL,
        AccountCode NVARCHAR(100) NOT NULL,
        Currency NVARCHAR(10) NOT NULL,
        ExpectedAmount DECIMAL(19,4) NOT NULL,
        CreatedAt DATETIME2 NOT NULL
            CONSTRAINT DF_CashExpectedTransactions_CreatedAt
            DEFAULT SYSUTCDATETIME()
    );

    CREATE INDEX IX_CashExpectedTransactions_AdjustmentId
        ON dbo.CashExpectedTransactions (AdjustmentId);
END;
GO

IF OBJECT_ID(N'dbo.CashReconciliationResults', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CashReconciliationResults
    (
        Id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_CashReconciliationResults PRIMARY KEY,
        StagingId BIGINT NOT NULL,
        ExpectedTransactionId BIGINT NULL,
        ReconciliationStatus NVARCHAR(20) NOT NULL,
        DifferenceAmount DECIMAL(19,4) NULL,
        Details NVARCHAR(500) NULL,
        ReconciledAt DATETIME2 NOT NULL
            CONSTRAINT DF_CashReconciliationResults_ReconciledAt
            DEFAULT SYSUTCDATETIME(),

        CONSTRAINT FK_CashReconciliationResults_CashStaging
            FOREIGN KEY (StagingId)
            REFERENCES dbo.CashStaging (Id),

        CONSTRAINT FK_CashReconciliationResults_Expected
            FOREIGN KEY (ExpectedTransactionId)
            REFERENCES dbo.CashExpectedTransactions (Id),

        CONSTRAINT CK_CashReconciliationResults_Status
            CHECK (ReconciliationStatus IN
                ('RECONCILED', 'DIFFERENCE', 'DUPLICATE',
                 'UNMATCHED', 'FAILED'))
    );

    CREATE INDEX IX_CashReconciliationResults_StagingId
        ON dbo.CashReconciliationResults (StagingId);

    CREATE INDEX IX_CashReconciliationResults_Status
        ON dbo.CashReconciliationResults (ReconciliationStatus);
END;
GO
