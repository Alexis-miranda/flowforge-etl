-- A worker must acquire its execution lease before inserting the RUNNING
-- execution snapshot. Therefore the lease row cannot have a foreign key to
-- ETLExecution. Execution IDs remain validated by the handler and the lease
-- repository; the lease table retains fencing-token history independently.
IF OBJECT_ID(N'dbo.FK_ETLExecutionLease_ETLExecution', N'F') IS NOT NULL
BEGIN
    ALTER TABLE dbo.ETLExecutionLease
        DROP CONSTRAINT FK_ETLExecutionLease_ETLExecution;
END;
GO
