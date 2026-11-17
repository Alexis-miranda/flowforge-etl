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

/*
 * One row per step in a completed execution. StepOrder preserves the
 * execution order; nullable StartedAt is intentional for SKIPPED steps.
 * Existing execution audit rows remain valid and simply have no child rows.
 */
IF OBJECT_ID(N'dbo.ETLExecutionStep', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLExecutionStep
    (
        ExecutionId            VARCHAR(36)    NOT NULL,
        StepOrder              INT            NOT NULL,
        StepId                 NVARCHAR(200)  NOT NULL,
        StepName               NVARCHAR(300)  NOT NULL,
        Status                 VARCHAR(20)    NOT NULL,
        StartedAt              DATETIME2(7)   NULL,
        FinishedAt             DATETIME2(7)   NOT NULL,
        ExceptionType          NVARCHAR(1000) NULL,
        ErrorMessage           NVARCHAR(MAX)  NULL,
        CauseType              NVARCHAR(1000) NULL,
        CauseMessage           NVARCHAR(MAX)  NULL,
        SkipReason             NVARCHAR(MAX)  NULL,
        FailureClassification  VARCHAR(40)    NULL,

        CONSTRAINT PK_ETLExecutionStep
            PRIMARY KEY (ExecutionId, StepOrder),

        CONSTRAINT FK_ETLExecutionStep_Execution
            FOREIGN KEY (ExecutionId)
            REFERENCES dbo.ETLExecution (ExecutionId)
            ON DELETE CASCADE,

        CONSTRAINT CK_ETLExecutionStep_Status
            CHECK (Status IN ('SUCCEEDED', 'FAILED', 'SKIPPED')),

        CONSTRAINT CK_ETLExecutionStep_Classification
            CHECK (
                FailureClassification IS NULL
                OR FailureClassification IN (
                    'RETRYABLE',
                    'REQUIRES_HUMAN_INTERVENTION',
                    'NON_RECOVERABLE',
                    'UNCLASSIFIED'
                )
            ),

        CONSTRAINT CK_ETLExecutionStep_Timestamps
            CHECK (StartedAt IS NULL OR FinishedAt >= StartedAt),

        CONSTRAINT CK_ETLExecutionStep_StartedAt
            CHECK (Status = 'SKIPPED' OR StartedAt IS NOT NULL)
    );
END;
GO

IF NOT EXISTS
(
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_ETLExecutionStep_ExecutionId_StepOrder'
      AND object_id = OBJECT_ID(N'dbo.ETLExecutionStep')
)
BEGIN
    CREATE INDEX IX_ETLExecutionStep_ExecutionId_StepOrder
        ON dbo.ETLExecutionStep (ExecutionId, StepOrder);
END;
GO
