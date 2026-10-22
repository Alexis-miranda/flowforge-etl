-- FlowForge ETL - Initial cash staging schema
-- Run against the FlowForgeETL_Dev database.
-- This script creates the table only if it does not already exist.

IF OBJECT_ID(N'dbo.CashStaging', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CashStaging
    (
        Id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_CashStaging PRIMARY KEY,

        AccountCode NVARCHAR(100) NULL,
        AdjustmentId NVARCHAR(100) NULL,
        Currency NVARCHAR(10) NULL,
        Amount NVARCHAR(50) NULL,

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
