-- FlowForge ETL - Cash master audit schema
-- Run against the FlowForgeETL_Dev database after the staging/rejected schemas.
-- Creates the table and its source-file index only when they do not exist.

IF OBJECT_ID(N'dbo.T_MASTER_CASH', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.T_MASTER_CASH
    (
        Id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_T_MASTER_CASH PRIMARY KEY,

        SourceFile NVARCHAR(260) NOT NULL,
        BackOfficeCode NVARCHAR(50) NULL,
        SourceRowNumber BIGINT NULL,

        AccountCode NVARCHAR(100) NULL,
        AdjustmentId NVARCHAR(100) NULL,
        Currency NVARCHAR(10) NULL,
        Amount DECIMAL(19,4) NULL,

        RawRecord NVARCHAR(MAX) NULL,
        ProcessingStatus NVARCHAR(20) NOT NULL,
        RejectionReason NVARCHAR(1000) NULL,

        CreatedAt DATETIME2(7) NOT NULL
            CONSTRAINT DF_T_MASTER_CASH_CreatedAt
            DEFAULT SYSUTCDATETIME(),

        CONSTRAINT CK_T_MASTER_CASH_ProcessingStatus
            CHECK (ProcessingStatus IN (N'ACCEPTED', N'REJECTED'))
    );

    CREATE INDEX IX_T_MASTER_CASH_SourceFile
        ON dbo.T_MASTER_CASH (SourceFile, SourceRowNumber);
END;
GO
