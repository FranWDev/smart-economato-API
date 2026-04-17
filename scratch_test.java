package test;
// Just checking if we can write a query like this in RecipeCookingAuditRepository:
// @Query("SELECT rca FROM RecipeCookingAudit rca WHERE rca.correlationId IN (SELECT sl.correlationId FROM StockLedgerBatchDetail d JOIN d.ledgerTransaction sl WHERE d.batch.id = :batchId AND sl.correlationId IS NOT NULL)")
// List<RecipeCookingAudit> findByBatchId(@Param("batchId") Long batchId);
