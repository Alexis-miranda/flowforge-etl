-- FlowForge ETL - Pipeline execution audit schema
-- Run against the FlowForgeETL_Dev database.
-- Stores pipeline-level execution state independently of business tables.
-- ExecutionId uses VARCHAR(36) consistently with docs/sql/04 and ETLStepExecution.

IF OBJECT_ID(N'dbo.ETLExecution', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLExecution
    (
        ExecutionId VARCHAR(36) NOT NULL,
        PipelineId NVARCHAR(200) NOT NULL,
        Status VARCHAR(20) NOT NULL,
        FailedStepId NVARCHAR(200) NULL,
        ErrorMessage NVARCHAR(MAX) NULL,
        StartedAt DATETIME2(7) NOT NULL,
        FinishedAt DATETIME2(7) NULL,

        CONSTRAINT PK_ETLExecution
            PRIMARY KEY (ExecutionId),

        CONSTRAINT CK_ETLExecution_Status
            CHECK (Status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),

        CONSTRAINT CK_ETLExecution_FinishedAt
            CHECK (
                (Status = 'RUNNING' AND FinishedAt IS NULL)
                OR
                (Status IN ('SUCCEEDED', 'FAILED') AND FinishedAt IS NOT NULL)
            )
    );

    CREATE INDEX IX_ETLExecution_Pipeline_StartedAt
        ON dbo.ETLExecution (PipelineId, StartedAt DESC);
END;
GO

-- Worker ownership is stored separately from the audit lifecycle.
-- Keep the row after release so fencing tokens remain monotonic.
IF OBJECT_ID(N'dbo.ETLExecutionLease', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLExecutionLease
    (
        ExecutionId  VARCHAR(36) NOT NULL,
        OwnerId      NVARCHAR(200) NULL,
        LeaseUntil   DATETIME2(7) NOT NULL,
        FencingToken BIGINT NOT NULL,

        CONSTRAINT PK_ETLExecutionLease
            PRIMARY KEY (ExecutionId),

        CONSTRAINT CK_ETLExecutionLease_FencingToken
            CHECK (FencingToken >= 1),

        CONSTRAINT FK_ETLExecutionLease_ETLExecution
            FOREIGN KEY (ExecutionId)
            REFERENCES dbo.ETLExecution (ExecutionId)
    );
END;
GO
