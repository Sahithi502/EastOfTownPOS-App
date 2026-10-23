# Architecture and data contract

Electron main process owns backend startup/shutdown, local API credentials, user session and the restricted request proxy. Renderer has no Node integration, runs sandboxed with context isolation, blocks navigation/popups, and accesses only enumerated operations and native file-selection/download bridges. No remote web assets are loaded. Backend listens only on 127.0.0.1 and requires a random 256-bit per-launch token. Stage 2 adds separate local administrator/billing authentication using BCrypt and expiring random sessions; see STAGE_2.md.

React → restricted preload → Electron REST proxy → Spring controller/validation → transactional service → JDBC repository → SQLite. Repository SQL is parameterized. Single pooled SQLite connection serializes writers within this process. Foreign keys, WAL and busy timeout are enabled. Do not open this database with a second running server.

## Current schema (V001)

`categories` → `products` → `product_variants` → `stock_movements`. Product holds shared name/category; variant owns SKU, barcode, prices, threshold and active state. Stock exists only as the sum of movements, so no redundant mutable stock column can drift. Opening stock is a recorded movement. Audit logs record mutations without copying customer information. Migration history stores version/checksum; previously applied migration contents must never change.

## Stage 2 schema (V002)

`users` stores unique usernames, BCrypt hashes, role, active status and timestamps. `stock_adjustments` owns signed correction quantities, effective dates, reasons, actor foreign keys and unique request keys. Its movement reference is unique so double submission cannot apply the same adjustment twice. `import_batches` retains uploaded raw worksheet JSON, mapping/policy configuration, preview token and catalog/stock revision. `import_history` has one summary per committed batch, with counts, policy, actor and timestamp. Preview does not mutate stock. Confirmation obtains a writer lock, rejects stale snapshots, revalidates, then commits the full batch transactionally. Failed commits retain the preview but roll back every business record. New audits carry authenticated user IDs; legacy `local-setup` actors remain unchanged.

Money is validated as decimal input with at most two fractional places, converted via BigDecimal to integer paise and stored as SQLite INTEGER. Renderer formatting is display-only; later billing calculations must run in Java. Unknown cost is NULL, not zero. No revenue or profit is fabricated.

## Remaining normalized schema

Add customers, invoices, invoice_items, payments, returns and return_items in subsequent migrations alongside their behavior. Invoice items need immutable description/SKU/variant/price/cost/discount/tax snapshots. Invoices need unique checkout idempotency keys and a transactional numbering sequence. Reversals reference original items, bound cumulative returned quantities and record refunds and stock movements atomically. A stock movement reference should be uniquely constrained for each sale/reversal item. Account lifecycle/recovery and backup/restore remain Stage 5 work.

## Future PostgreSQL migration

Keep Java service contracts and stable UUIDs. Introduce repository implementations for dialect-specific features (SQLite PRAGMA, migration DDL, checks and backup). Freeze sales, obtain a verified consistent backup, migrate schema, export entities in foreign-key order, load into staging PostgreSQL preserving identifiers and barcodes, then reconcile table counts, per-variant movement sums, invoice totals, payments, refunds, customer totals and audit counts. Set the invoice sequence above the highest issued number. Validate representative invoices and returned quantities before cutover; retain immutable source backups and a rollback window. Do not dual-write or introduce cloud services during this phase.
