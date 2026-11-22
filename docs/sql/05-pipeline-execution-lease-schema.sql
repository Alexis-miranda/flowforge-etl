IF OBJECT_ID(N'dbo.ETLExecutionLease', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ETLExecutionLease
    (
        ExecutionId  VARCHAR(36)   NOT NULL,
        OwnerId      NVARCHAR(200) NULL,
        LeaseUntil   DATETIME2(7)  NOT NULL,
        FencingToken BIGINT        NOT NULL,

        CONSTRAINT PK_ETLExecutionLease
            PRIMARY KEY (ExecutionId),

        CONSTRAINT CK_ETLExecutionLease_FencingToken
            CHECK (FencingToken >= 1)
    );
END;
GO
