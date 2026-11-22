USE FlowForgeETL_Dev;
GO

IF COL_LENGTH(N'dbo.CashOutputDelivery', N'PublishedPath') IS NULL
BEGIN
    ALTER TABLE dbo.CashOutputDelivery
        ADD PublishedPath NVARCHAR(2000) NULL;
END;
GO

IF COL_LENGTH(N'dbo.CashOutputDelivery', N'PublishedSha256') IS NULL
BEGIN
    ALTER TABLE dbo.CashOutputDelivery
        ADD PublishedSha256 CHAR(64) NULL;
END;
GO

IF COL_LENGTH(N'dbo.CashOutputDelivery', N'PublishedExecutionId') IS NULL
BEGIN
    ALTER TABLE dbo.CashOutputDelivery
        ADD PublishedExecutionId NVARCHAR(100) NULL;
END;
GO
