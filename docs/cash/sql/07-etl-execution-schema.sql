-- FlowForge ETL - Pipeline execution audit schema
-- Run against the FlowForgeETL_Dev database.
-- Stores pipeline-level execution state independently of business tables.

IF OBJECT_ID(N'dbo.ETLExecution', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLExecution
    (
        ExecutionId NVARCHAR(36) NOT NULL,
        PipelineId NVARCHAR(200) NOT NULL,
        Status NVARCHAR(20) NOT NULL,
        FailedStepId NVARCHAR(200) NULL,
        ErrorMessage NVARCHAR(MAX) NULL,
        StartedAt DATETIME2(7) NOT NULL,
        FinishedAt DATETIME2(7) NULL,

        CONSTRAINT PK_ETLExecution
            PRIMARY KEY (ExecutionId),

        CONSTRAINT CK_ETLExecution_Status
            CHECK (Status IN (N'RUNNING', N'SUCCEEDED', N'FAILED')),

        CONSTRAINT CK_ETLExecution_FinishedAt
            CHECK (
                (Status = N'RUNNING' AND FinishedAt IS NULL)
                OR
                (Status IN (N'SUCCEEDED', N'FAILED') AND FinishedAt IS NOT NULL)
            )
    );

    CREATE INDEX IX_ETLExecution_Pipeline_StartedAt
        ON dbo.ETLExecution (PipelineId, StartedAt DESC);
END;
GO
