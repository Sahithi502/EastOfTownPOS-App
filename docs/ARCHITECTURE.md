# Architecture and data contract
Electron main process owns backend startup/shutdown, local API credentials, user session and the restricted request proxy. Renderer has no Node integration, runs sandboxed with context isolation, blocks navigation/popups, and accesses only enumerated operations and native file-selection/download bridges. No remote web assets are loaded. Backend listens only on 127.0.0.1 and requires a random 256-bit per-launch token. Stage 2 adds separate local administrator/billing authentication using BCrypt and expiring random sessions; see STAGE_2.md.

React → restricted preload → Electron REST proxy → Spring controller/validation → transactional service → JDBC repository → SQLite. Repository SQL is parameterized. Single pooled SQLite connection serializes writers within this process. Foreign keys, WAL and busy timeout are enabled. Do not open this database with a second running server.

## Current schema (V001)

`categories` → `products` → `product_variants` → `stock_movements`. Product holds shared name/category; variant owns SKU, barcode, prices, threshold and active state. Stock exists only as the sum of movements, so no redundant mutable stock column can drift. Opening stock is a recorded movement. Audit logs record mutations without copying customer information. Migration history stores version/checksum; previously applied migration contents must never change.

## Stage 2 schema (V002)

`users` stores unique usernames, BCrypt hashes, role, active status and timestamps. `stock_adjustments` owns signed correction quantities, effective dates, reasons, actor foreign keys and unique request keys. Its movement reference is unique so double submission cannot apply the same adjustment twice. `import_batches` retains uploaded raw worksheet JSON, mapping/policy configuration, preview token and catalog/stock revision. `import_history` has one summary per committed batch, with counts, policy, actor and timestamp. Preview does not mutate stock. Confirmation obtains a writer lock, rejects stale snapshots, revalidates, then commits the full batch transactionally. Failed commits retain the preview but roll back every business record. New audits carry authenticated user IDs; legacy `local-setup` actors remain unchanged.

Money is validated as decimal input with at most two fractional places, converted via BigDecimal to integer paise and stored as SQLite INTEGER. Renderer formatting is display-only; billing calculations run in Java. Unknown cost is NULL, not zero. No revenue or profit is fabricated.

## Remaining normalized schema

Customers, invoices, invoice_items and payments are implemented in V004/V005. Add returns and return_items in Stage 5 alongside their reversal behavior. Invoice items need immutable description/SKU/variant/price/cost/discount/tax snapshots. Invoices need unique checkout idempotency keys and a transactional numbering sequence. Reversals reference original items, bound cumulative returned quantities and record refunds and stock movements atomically. A stock movement reference should be uniquely constrained for each sale/reversal item. Account lifecycle/recovery and backup/restore remain Stage 5 work.

## Future PostgreSQL migration

Keep Java service contracts and stable UUIDs. Introduce repository implementations for dialect-specific features (SQLite PRAGMA, migration DDL, checks and backup). Freeze sales, obtain a verified consistent backup, migrate schema, export entities in foreign-key order, load into staging PostgreSQL preserving identifiers and barcodes, then reconcile table counts, per-variant movement sums, invoice totals, payments, refunds, customer totals and audit counts. Set the invoice sequence above the highest issued number. Validate representative invoices and returned quantities before cutover; retain immutable source backups and a rollback window. Do not dual-write or introduce cloud services during this phase.

## Stage 3 billing schema and contracts (V004/V005)
See SCHEMA.md for every table. Invoices/items/payments and customers are implemented. V005 adds checkout_requests, variant_taxes, invoice/line snapshots, line positions and a transactional global numbering counter. Stock still comes exclusively from movement sums.

BillingService owns quotation, permission checks, discount allocation and checkout. Controllers validate nested DTOs; services also use Jakarta method validation. CatalogRepository supplies parameterized JDBC access; financial logic uses Java integer/BigDecimal values. ReceiptService renders persisted snapshots via the existing PDFBox/font infrastructure and submits jobs to installed Windows printers. The renderer's restricted bridge permits only named billing routes, selected receipt width and native Save As.

Quotation includes a digest of cart, product snapshots and billing configuration. Confirmation recalculates and validates the digest within a writer transaction; backend prices and configured per-variant taxes are authoritative. Largest remainder bill-discount allocation and HALF_UP per-line tax rounding ensure invoice/item/payment reconciliation. SQLite has one application connection; checkout/stock/import writers acquire the same write-lock setting before reading protected balances. A future multi-branch PostgreSQL implementation must replace this with transaction/row locking while keeping the same invariants.

Checkout request key, exact request hash and operator are committed with the invoice. Identical retries return the original sale before revalidating current stock/prices. A unique sale-movement key reinforces one deduction per invoice/variant. Renderer local storage keeps the pending request per user across restart, and status checks serialize behind in-progress checkout. Printing runs after sale commit; printing failure cannot roll back a paid invoice. Cash tender/change is distinct from the applied payment amount; UPI/card transactions are manually confirmed by the operator.

Basic customers were pulled forward for Stage 3 checkout. Full history/corrections/aggregate reports are implemented in Stage 4 below. Returns/refunds/reversal records and account lifecycle/backup are still Stage 5. Administrator recovery from V003 is already implemented; billing-user password resets and WhatsApp remain deferred for approval.

## Stage 4 customer/report contracts (V006)

CustomerService serializes optimistic customer corrections and before/after snapshots in customer_changes with an audit action, leaving invoice snapshots intact. Paginated customer/history queries derive lifetime counts/totals from completed invoices; full item/payment information uses the saved invoice endpoint. Customer creation still uses BillingService's validated unique-mobile rules.

ReportRepository owns parameterized read queries. ReportService obtains one read transaction for related data, groups by permanent product/variant/customer IDs or historical category, and applies Asia/Kolkata date presets/grouping in Java. Integer paise sums and BigDecimal average rounding remain authoritative. Unknown cost is preserved; current inventory value and historical known-cost profit have separate definitions. ReportExport renders the same report contracts to typed POI workbooks and paginated PDFBox tables. Native Save As handles destinations through explicitly allowed report types/formats. Reports and store costs require ADMIN at the backend as well as in navigation.

No materialized report tables or cloud dependencies are introduced. Date-scoped datasets over 20,000 source rows are rejected with an explicit smaller-range message, not silently truncated. See STAGE_4.md for accounting definitions, export behavior, customer snapshot privacy and Stage 5 reversal extension requirements.

## Stage 5 extension (0.5.0)

The earlier future-stage notes describe the historical Stage 3/4 boundary. Stage 5 now implements ReversalService (atomic quantity/refund/stock ledger and durable retries), ExpenseService (audited expenses/voids), account session revocation/role controls and percentage-discount normalization in BillingService. Controllers enforce administrator access to sensitive operations. ReportRepository synthesizes signed reversal events; ReportService aggregates revenue/cost/expenses by Indian business dates. Original invoices remain immutable.

BackupFiles isolates SQLite-only read-only snapshots, integrity/FK/checksum validation and pre-datasource atomic restore. BackupService schedules verified backups and retention while the application is open. LocalSecurity blocks business writes while a restore marker is pending. The launcher never restores a running datasource. Restricted native maintenance IPC handles file selection and explicit confirmations; no Node APIs are exposed to React. WhatsApp preparation saves the same receipt layout and opens a fixed validated wa.me destination for operator review/attachment/send. STAGE_5.md documents exact contracts and CLOUD_MIGRATION.md documents portable export reconciliation.

## Stage 6 extension (0.6.0)

PhoneNumbers owns offline country/mobile parsing and E.164 normalization using pinned libphonenumber metadata. Shared frontend PhoneInput supplies country and national digits to customer creation/correction, including checkout. V008 preserves legacy rows and adds normalized phone fields/index. API phone-validity metadata identifies legacy corrections; invoice sharing contact is returned separately from immutable invoice snapshots.

New Bill uses cancellable-by-generation backend quotations for live line totals; no frontend copy of financial rules is introduced. Final checkout still quotes/checks/writes transactionally. Native Windows sharing lives in an isolated C#/.NET Framework helper with per-window IDataTransferManagerInterop and a StorageItems file package. The Electron main process controls receipt generation, local-file paths, confirmations, helper lifetime and seven-day cache cleanup; the renderer cannot share arbitrary files or execute programs. Manual WhatsApp click-to-chat remains a separately labelled fallback. Neither route reports delivery.

Source/package launches explicitly retain the existing business profile. Packaged acceptance uses an explicit isolated UUID profile, bundled Java and an unchanged backend API. Build/check scripts record binary hashes and actual signature state, and provide fail-closed production signing gates. See STAGE_6.md, STAGE_6_TEST_RESULTS.md and RELEASE.md for implementation, evidence and outstanding hardware/signing acceptance.
