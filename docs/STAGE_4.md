# Stage 4 — customers, dashboard and reports (0.4.0)

## Delivered scope

Customers can be searched by literal name/mobile, created with a unique mobile, checked for possible same-name duplicates, and viewed with lifetime completed purchase count, amount including tax, last purchase and last invoice. Customers and invoices are paged in groups of 50; history does not stop at the invoice screen's 500-row limit. Each purchase opens the original receipt with products, quantities, saved prices, both discounts, tax and applied payments.

Administrators can correct name, mobile and email. V006 adds an optimistic version and updated timestamp. Correction takes the shared writer lock, checks unique mobile/version, and commits the new master details, before/after customer_changes record and audit action together. Invoice customer snapshots are preserved. Billing users can search, create and view histories, but cannot correct customers or read correction snapshots.

Dashboard and report access is administrator-only because they expose store collections, costs and profit estimates. The sidebar hides these operations for billing users and controllers enforce the same restriction. No default/demo customers or sales are seeded. Test suites use isolated temporary profiles/databases.

## Dashboard and date rules

Dashboard: pre-discount/pre-tax gross sales; combined discounts; net sales before tax; tax; invoice value including tax; completed bill count; average invoice including tax (HALF_UP to a paise); cash/UPI/card applied collections; current stock units; known inventory cost value and units missing cost; low-stock/out-of-stock counts and up to 20 alerts each; 10 recent invoices and 10 bestselling variants. All figures come from committed database records.

Date presets: today, yesterday, Monday through today, month through today, calendar year through today, financial year April 1 through today, and inclusive custom dates. Calendar grouping and boundaries use Asia/Kolkata independently of the Windows timezone. Repository date bounds use UTC second prefixes to include legacy ISO timestamps with and without fractional seconds. Daily/monthly/yearly grouping occurs in Java, not SQLite date functions.

Inventory metrics and valuation always describe CURRENT stock across active and inactive variants, regardless of the selected sales dates. Alerts count active variants only. Low stock means positive quantity at or below the variant's reorder threshold; out of stock means zero or less. Opening/adjustment/sale movements remain the only stock ownership source.

## Available reports and accounting definitions

| Report | Basis |
|---|---|
| Daily / monthly / yearly sales | Indian invoice date; gross, discounts, net sales excluding tax, tax and invoice value |
| Product sales | Permanent product ID; description/category from latest sale snapshot in the range |
| Category sales | Historical category names stored on invoice items |
| SKU / size / colour | Permanent variant ID; saved variant labels and quantities |
| Payment collections | Applied payment records, one group per CASH/UPI/CARD; split invoices contribute to each method |
| Stock movements and adjustments | Recording timestamp in range; signed quantity/reason/reference/actor and separate adjustment effective date |
| Inventory valuation | Current movement balance × current recorded cost; unknown cost stays blank; known zero stays zero |
| Gross profit estimate | Discounted revenue excluding tax minus cost snapshotted at sale, ONLY for lines with known cost; missing-cost units shown separately |
| Customer purchases | Selected-period completed invoice totals by permanent customer ID, excluding guests; last-sale customer snapshot labels |
| Individual customer history | Product rows for selected customer/period, original prices/discounts/tax and invoice payment descriptions |

Gross sales are not profit. Tax is not included in net sales or gross profit estimates. Inventory value is not sales revenue. Profit excludes rent, salary and other operating expenses; NULL costs never become zero. Repeated invoice payment descriptions in customer product rows must not be summed.

Completed invoices are the sales/collection basis. No cancellation or return write workflow exists until Stage 5. There are no fake empty return/refund reports: those reports and the extension of net sales/customer/collection totals to reversal records belong with Stage 5's transactional ledger. Historical CANCELLED invoices, if present, are excluded from completed sales/customer aggregates and remain visible in customer invoice history.

## Exports and operating bounds

Every report supports Excel and PDF via a restricted Electron native Save As operation. Excel uses literal string cells (never formulas), numeric INR cells with two-decimal formatting, frozen/filterable headers and a Read me sheet containing range/definitions. Extremely large values beyond Excel's 15 significant digits are preserved as decimal text. PDF uses landscape A4 for up to 10 columns and landscape A3 for wider reports, wrapped cells, repeated headers and page numbers; long rows continue on another page without truncation.

Each report read is a transaction, so related totals share one database snapshot. Export generates a fresh snapshot using the last applied preview filters; sales committed after preview can therefore appear in the export. No browser paths or arbitrary backend download URLs are accepted. A cancelled Save As makes no file. No cloud service is used.

Date-scoped source datasets are limited to 20,000 rows each. A larger selection fails with an explicit request to shorten the range, rather than returning truncated totals. Aggregates reject numbers outside exact renderer integer precision. Inventory reports cover the full current variant set; customer screen histories use pagination.

## API and implementation

CustomerController/CustomerService own customer search/history/correction, using ReportRepository for queries. ReportController/ReportService own authorized dashboard/report reads and Java aggregation. ReportExport reuses installed Apache POI/PDFBox and LabelService's OS font loader. Parameterized JDBC and SELECT/GROUP BY support a future PostgreSQL repository; no SQLite-specific reporting date expressions are used.

Routes: GET/POST /api/customers; GET /api/customers/duplicates?name=; GET/PUT /api/customers/{id}; GET /api/customers/{id}/changes. GET /api/reports/dashboard; GET /api/reports/{type}; GET /api/reports/{type}/export?format=xlsx|pdf. All report routes accept preset/from/to; customer-history also requires customerId. The application token, user session, role and restricted preload allowlist still apply.

V001–V005 are unchanged. V006 adds customer correction version/timestamps, customer_changes and supporting indexes. It never rewrites stock, barcodes or invoice amounts. See SCHEMA.md for ownership and migration reconciliation requirements.

## Deferred requests retained

Administrator reset of billing-user passwords and WhatsApp receipt sharing remain for explicit approval in Stage 5. Barcode orientation/size remains unchanged pending the shopkeeper discussion. The existing Stage 3 UI accepts fixed INR discounts; earlier chat statements claiming percentage input already existed were incorrect. A percentage/fixed-rupee selector remains a separate approved future billing enhancement and is not included in Stage 4.
