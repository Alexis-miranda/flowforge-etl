-- Durable request acceptance and transactional outbox for asynchronous execution.

IF OBJECT_ID(N'dbo.ETLPipelineExecutionRequest', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLPipelineExecutionRequest
    (
        ExecutionId NVARCHAR(36) NOT NULL,
        PipelineType NVARCHAR(200) NOT NULL,
        RequestedAt DATETIME2(7) NOT NULL,
        Payload VARBINARY(MAX) NOT NULL,

        CONSTRAINT PK_ETLPipelineExecutionRequest
            PRIMARY KEY (ExecutionId)
    );
END;
GO

IF OBJECT_ID(N'dbo.ETLOutboxMessage', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLOutboxMessage
    (
        MessageId NVARCHAR(36) NOT NULL,
        ExecutionId NVARCHAR(36) NOT NULL,
        Status VARCHAR(20) NOT NULL,
        CreatedAt DATETIME2(7) NOT NULL,
        PublishedAt DATETIME2(7) NULL,
        PublishAttempts INT NOT NULL CONSTRAINT DF_ETLOutboxMessage_Attempts DEFAULT (0),
        LastError NVARCHAR(MAX) NULL,

        CONSTRAINT PK_ETLOutboxMessage PRIMARY KEY (MessageId),
        CONSTRAINT UQ_ETLOutboxMessage_ExecutionId UNIQUE (ExecutionId),
        CONSTRAINT FK_ETLOutboxMessage_Request
            FOREIGN KEY (ExecutionId)
            REFERENCES dbo.ETLPipelineExecutionRequest(ExecutionId),
        CONSTRAINT CK_ETLOutboxMessage_Status
            CHECK (Status IN ('PENDING', 'PUBLISHED')),
        CONSTRAINT CK_ETLOutboxMessage_Attempts
            CHECK (PublishAttempts >= 0),
        CONSTRAINT CK_ETLOutboxMessage_PublishedAt
            CHECK (
                (Status = 'PENDING' AND PublishedAt IS NULL)
                OR (Status = 'PUBLISHED' AND PublishedAt IS NOT NULL)
            )
    );

    CREATE INDEX IX_ETLOutboxMessage_Status_CreatedAt
        ON dbo.ETLOutboxMessage (Status, CreatedAt, MessageId);
END;
GO
