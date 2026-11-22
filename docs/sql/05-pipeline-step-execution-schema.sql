-- ExecutionId must match dbo.ETLExecution.ExecutionId (NVARCHAR(36)) exactly
-- so SQL Server can create the foreign key below.
IF OBJECT_ID(N'dbo.ETLStepExecution', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLStepExecution
    (
        ExecutionId NVARCHAR(36) NOT NULL,
        StepId NVARCHAR(200) NOT NULL,
        StepName NVARCHAR(500) NOT NULL,
        Status VARCHAR(20) NOT NULL,
        StartedAt DATETIME2(7) NULL,
        FinishedAt DATETIME2(7) NULL,
        ExceptionType NVARCHAR(1000) NULL,
        ErrorMessage NVARCHAR(MAX) NULL,
        CauseType NVARCHAR(1000) NULL,
        CauseMessage NVARCHAR(MAX) NULL,
        SkipReason NVARCHAR(1000) NULL,
        FailureClassification VARCHAR(50) NULL,
        CONSTRAINT PK_ETLStepExecution PRIMARY KEY (ExecutionId, StepId),
        CONSTRAINT FK_ETLStepExecution_Execution
            FOREIGN KEY (ExecutionId) REFERENCES dbo.ETLExecution(ExecutionId),
        CONSTRAINT CK_ETLStepExecution_Status
            CHECK (Status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'))
    );
END;
GO

IF NOT EXISTS
(
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_ETLStepExecution_Execution_Status'
      AND object_id = OBJECT_ID(N'dbo.ETLStepExecution')
)
BEGIN
    CREATE INDEX IX_ETLStepExecution_Execution_Status
        ON dbo.ETLStepExecution (ExecutionId, Status);
END;
GO
