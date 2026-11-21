
-- FlowForge ETL - Cash rejected records schema
-- Run against the FlowForgeETL_Dev database.
-- Creates the table only if it does not already exist.

IF OBJECT_ID(N'dbo.CashRejected', N'U') IS NULL
BEGIN
CREATE TABLE dbo.CashRejected
(
    Id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_CashRejected PRIMARY KEY,

    AccountCode NVARCHAR(100) NULL,
    AdjustmentId NVARCHAR(100) NULL,
    Currency NVARCHAR(10) NULL,

    -- Preserve the original value, even if it is not numeric.
    Amount NVARCHAR(50) NULL,

    SourceFile NVARCHAR(260) NOT NULL,
    RejectionReason NVARCHAR(1000) NOT NULL,

    RejectedAt DATETIME2 NOT NULL
        CONSTRAINT DF_CashRejected_RejectedAt
        DEFAULT SYSUTCDATETIME()
);
END;
GO
