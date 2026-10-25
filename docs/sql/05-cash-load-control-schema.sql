IF OBJECT_ID(N'dbo.CashLoadControl', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CashLoadControl
    (
        SourceFile NVARCHAR(260) NOT NULL,
        LoadedAt DATETIME2 NOT NULL
            CONSTRAINT DF_CashLoadControl_LoadedAt
            DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_CashLoadControl
            PRIMARY KEY (SourceFile)
    );
END;
GO
