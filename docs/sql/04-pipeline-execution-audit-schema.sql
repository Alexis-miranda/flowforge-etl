
IF OBJECT_ID(N'dbo.ETLExecution', N'U') IS NULL
BEGIN
CREATE TABLE dbo.ETLExecution
(
    ExecutionId  VARCHAR(36)     NOT NULL,
    PipelineId   NVARCHAR(200)   NOT NULL,
    Status       VARCHAR(20)     NOT NULL,
    FailedStepId NVARCHAR(200)   NULL,
    ErrorMessage NVARCHAR(MAX)   NULL,
    StartedAt    DATETIME2(7)    NOT NULL,
    FinishedAt   DATETIME2(7)    NULL,

    CONSTRAINT PK_ETLExecution
        PRIMARY KEY (ExecutionId),

    CONSTRAINT CK_ETLExecution_Status
        CHECK (Status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),

    CONSTRAINT CK_ETLExecution_Timestamps
        CHECK (FinishedAt IS NULL OR FinishedAt >= StartedAt)
);
END;
GO

IF NOT EXISTS
(
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ETLExecution_PipelineId_StartedAt'
      AND object_id = OBJECT_ID(N'dbo.ETLExecution')
)
BEGIN
CREATE INDEX IX_ETLExecution_PipelineId_StartedAt
    ON dbo.ETLExecution (PipelineId, StartedAt DESC);
END;
GO