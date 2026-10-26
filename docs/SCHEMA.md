# Database schema through Stage 4

Persistent SQLite location and safe shutdown instructions are in README.md. Versioned migrations live in backend/src/main/resources/db/migration. Applied migration files are checksummed and must remain unchanged; extend the schema with a new migration.

| Table | Purpose / ownership |
|---|---|
| categories | Unique category name; shared by products |
| products | Product name/category and timestamps |
| product_variants | Stable variant UUID, unique SKU/barcode, size/colour, prices, reorder threshold, active flag and optimistic version |
| stock_movements | Sole source of stock: sum of signed quantities per variant; opening/adjustment/sale/return/cancellation types |
| audit_logs | Action/entity/actor/time; original audit records retained |
| application_settings | Currency, write lock, invoice sequence and billing/label preferences |
| users | Unique username, BCrypt hash, ADMIN/BILLING role, active flag and administrator recovery hash |
| stock_adjustments | Authorized reason/date/delta, actor and unique retry key; references one adjustment movement |
| import_batches | Uploaded workbook JSON, mapping/policy, preview token/state/revision |
| import_history | One committed summary per batch, counts and actor/time |
| customers | Customer UUID/name/unique mobile/optional email/registration time, optimistic correction version and updated timestamp |
| customer_changes | Audited before/after master snapshots, customer/user FKs and time; administrator-only read |
| invoices | Unique number, status, exact totals, operator/time, optional customer FK plus customer and shop snapshots |
| invoice_items | Invoice/variant FKs, original description/category/SKU/barcode/size/colour/price/cost/quantity/discount/tax/line total and line position |
| payments | Invoice FK, CASH/UPI/CARD method, applied positive amount in paise and timestamp |
| checkout_requests | Unique client request key, request hash and operator; one-to-one link to completed invoice |
| variant_taxes | Optional per-variant exclusive tax rate in basis points; absent means 0% |
| schema_migrations | Internal migration version/checksum/applied time |

Stock quantity is not duplicated in product_variants. Opening stock is an OPENING movement. Cost may be NULL when unknown. Invoice snapshots intentionally retain historical values even after catalog/customer/receipt-setting edits. Reports and customer totals must derive from committed invoice/payment/reversal records.

Invoice subtotal is pre-discount, pre-tax. Invoice discount includes line and bill discounts; bill_discount_paise identifies the bill portion. Invoice-item discount_paise is its line discount and bill_discount_paise is its allocated share. Line total = unit price × quantity - both discounts + rounded tax. Sales movement references the invoice, with a unique (reference_id,variant_id) constraint for SALE. No invoice deletion/update endpoint is provided.

Indexes support category lookup, variant/time stock history, invoice date/customer, invoice items/payments and sold variant. Foreign keys are enabled per connection. SQLite-specific PRAGMA/migration/backup operations stay in infrastructure; billing arithmetic uses Java values. A PostgreSQL cutover must carry forward all UUIDs, invoice numbering, checkout keys/hashes, permanent barcodes, snapshots and movement/audit history. Reconcile all counts, stock balances, totals and payments; never migrate just mutable catalog values.

V006 adds customer correction history/version/timestamps and customer/movement/payment indexes. There are 18 tables including schema_migrations. No dashboard/report summary tables duplicate sales or stock facts. ReportRepository derives data from ledger/snapshots; ReportService aggregates and applies Indian business dates. Customer correction commits atomically with audit/history while preserving original invoices.

Returns/return_items and refund records are planned for Stage 5. Full customer history/reporting is implemented in Stage 4; net sales/customer/collection calculations must be extended to reversal records when that workflow is implemented.
