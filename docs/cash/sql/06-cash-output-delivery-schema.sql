USE FlowForgeETL_Dev;
GO
IF OBJECT_ID(N'dbo.CashOutputDelivery', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CashOutputDelivery
    (
        SourceFile NVARCHAR(260) NOT NULL,
        BackOfficeCode NVARCHAR(50) NOT NULL,
        OutputFileName NVARCHAR(260) NOT NULL,
        OutputDirectory NVARCHAR(1000) NOT NULL,
        CsvPayload VARBINARY(MAX) NOT NULL,
        PayloadSha256 CHAR(64) NOT NULL,
        DeliveryStatus NVARCHAR(20) NOT NULL
            CONSTRAINT DF_CashOutputDelivery_Status
            DEFAULT N'PENDING',
        CreatedAt DATETIME2(7) NOT NULL
            CONSTRAINT DF_CashOutputDelivery_CreatedAt
            DEFAULT SYSUTCDATETIME(),
        PublishedAt DATETIME2(7) NULL,
        LastError NVARCHAR(1000) NULL,

        CONSTRAINT PK_CashOutputDelivery
            PRIMARY KEY (SourceFile),

        CONSTRAINT CK_CashOutputDelivery_Status
            CHECK (DeliveryStatus IN (N'PENDING', N'PUBLISHED'))
    );
END;
GO
