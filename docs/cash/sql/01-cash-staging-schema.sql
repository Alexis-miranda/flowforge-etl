
-- FlowForge ETL - Initial cash staging schema
-- Run against the FlowForgeETL_Dev database.
-- Creates the table if missing and migrates Amount to DECIMAL(19,4).

IF OBJECT_ID(N'dbo.CashStaging', N'U') IS NULL
BEGIN
CREATE TABLE dbo.CashStaging
(
    Id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_CashStaging PRIMARY KEY,

    AccountCode NVARCHAR(100) NULL,
    AdjustmentId NVARCHAR(100) NULL,
    Currency NVARCHAR(10) NULL,
    Amount DECIMAL(19,4) NULL,

    SourceFile NVARCHAR(260) NOT NULL,

    ReceivedAt DATETIME2 NOT NULL
        CONSTRAINT DF_CashStaging_ReceivedAt
        DEFAULT SYSUTCDATETIME(),

    ProcessingStatus NVARCHAR(20) NOT NULL
            CONSTRAINT DF_CashStaging_ProcessingStatus
            DEFAULT ('RECEIVED')
);
END;
GO

-- Migrate existing installations from NVARCHAR to DECIMAL.
-- Abort if any non-null amount cannot be converted safely.
IF EXISTS
(
    SELECT 1
    FROM dbo.CashStaging
    WHERE Amount IS NOT NULL
      AND TRY_CONVERT(DECIMAL(19,4), Amount) IS NULL
)
BEGIN
    THROW 50001,
        'Cannot migrate CashStaging.Amount: invalid numeric values exist.',
        1;
END;
GO

-- Alter only when the column is not already DECIMAL(19,4).
IF EXISTS
(
    SELECT 1
    FROM sys.columns c
    INNER JOIN sys.types t ON c.user_type_id = t.user_type_id
    WHERE c.object_id = OBJECT_ID(N'dbo.CashStaging')
      AND c.name = N'Amount'
      AND
      (
          t.name <> N'decimal'
          OR c.precision <> 19
          OR c.scale <> 4
      )
)
BEGIN
ALTER TABLE dbo.CashStaging
ALTER COLUMN Amount DECIMAL(19,4) NULL;
END;
GO
