# Architecture and data contract

Electron main process owns backend startup/shutdown, local API credentials and the restricted request proxy. Renderer has no Node integration, runs sandboxed with context isolation, blocks navigation/popups, and accesses only enumerated catalog operations. No remote web assets are loaded. Backend listens only on 127.0.0.1 and requires a random 256-bit per-launch token. This token isolates browser callers; it is not business user authentication. This milestone assumes a trusted local operator; administrator/billing authorization is a Stage 5 prerequisite for production.

React → restricted preload → Electron REST proxy → Spring controller/validation → transactional service → JDBC repository → SQLite. Repository SQL is parameterized. Single pooled SQLite connection serializes writers within this process. Foreign keys, WAL and busy timeout are enabled. Do not open this database with a second running server.

## Current schema (V001)

`categories` → `products` → `product_variants` → `stock_movements`. Product holds shared name/category; variant owns SKU, barcode, prices, threshold and active state. Stock exists only as the sum of movements, so no redundant mutable stock column can drift. Opening stock is a recorded movement. Audit logs record mutations without copying customer information. Migration history stores version/checksum; previously applied migration contents must never change. Add V002 and extend the explicit migration runner when implementing the next stage.

Money is validated as decimal input with at most two fractional places, converted via BigDecimal to integer paise and stored as SQLite INTEGER. Renderer formatting is display-only; later billing calculations must run in Java. Unknown cost is NULL, not zero. No revenue or profit is fabricated.

## Remaining normalized schema

Add customers, users, invoices, invoice_items, payments, returns, return_items and import_history in subsequent migrations alongside their behavior. Invoice items need immutable description/SKU/variant/price/cost/discount/tax snapshots. Invoices need unique checkout idempotency keys and a transactional numbering sequence. Reversals reference original items, bound cumulative returned quantities and record refunds and stock movements atomically. A stock movement reference should be uniquely constrained for each sale/reversal item. User references replace the explicit `local-setup` audit actor once authentication is implemented.

## Future PostgreSQL migration

Keep Java service contracts and stable UUIDs. Introduce repository implementations for dialect-specific features (SQLite PRAGMA, migration DDL, checks and backup). Freeze sales, obtain a verified consistent backup, migrate schema, export entities in foreign-key order, load into staging PostgreSQL preserving identifiers and barcodes, then reconcile table counts, per-variant movement sums, invoice totals, payments, refunds, customer totals and audit counts. Set the invoice sequence above the highest issued number. Validate representative invoices and returned quantities before cutover; retain immutable source backups and a rollback window. Do not dual-write or introduce cloud services during this phase.
