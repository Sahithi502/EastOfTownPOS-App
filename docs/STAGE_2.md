# Stage 2 — imports, stock ledger and labels (0.2.0)

## Included

The Excel import and barcode label screens are functional desktop workflows backed by SQLite. Inventory now provides administrator-only stock adjustments and movement history. Local owner setup/login and administrator/billing users were brought forward from Stage 5 so sensitive Stage 2 operations are authorized at the REST boundary. The per-launch local API token remains separate from user authorization. Accounts use BCrypt, sessions expire after eight hours or backend restart, and the login and recovery endpoints limit attempts. Administrator recovery uses a one-time recovery code; the code is shown once during setup or when an authenticated administrator rotates it from Users. There is no password recovery by email or cloud service.

## Upgrade and schema

V001 and V002 are unchanged. The migration runner applies all three files in order and verifies SHA-256 on every previously applied file. V003 adds nullable BCrypt recovery-code columns to users, preserving existing installations. V002 creates users, stock_adjustments, import_batches and import_history, plus movement/reference indexes and a writer coordination setting. Products/variants/opening stock and legacy barcode values are retained. No seed password or recovery code is installed. On first Stage 2 launch, the trusted operator creates the local owner account and saves the displayed recovery code.

Money remains BigDecimal → integer paise. Stock remains a sum of immutable movements; there is no directly editable current-stock column. Product editing still cannot modify quantity. New internal barcode values are 14 numeric digits (`20` + 12 random digits), checked for collision and protected by the unique database constraint. Existing `EOT...` and supplied barcodes are kept.

## Excel lifecycle

1. Download a workbook template (Stock, Instructions and Categories sheets; no sample business rows).
2. Choose an .xlsx/.xls workbook in the native file dialog. Electron reads only that selected file and sends multipart data to the loopback API. Limits: 10 MB, first sheet only, 5,000 data rows, 64 columns, 1,000 characters per cell. Blank data rows are ignored. Formulas and cell errors are rejected, not evaluated. Encrypted/unreadable files produce a readable error.
3. Map columns by header/index; automatic suggestions can be corrected. A category assignment overrides the category column. SKU/barcode must be text cells to preserve leading zeroes. Required fields: name, SKU, size, colour, selling price and category (mapped or assigned).
4. Choose a stock policy. Quantity is never interpreted as a replacement current balance.
5. Preview all rows, with action, stock delta, errors/warnings and new categories. Preview changes only staging records. Errors block the complete import. Export correction errors/warnings as literal text cells in an Excel workbook.
6. Review and confirm. The server checks the preview token and catalog/stock snapshot inside a transaction, revalidates the entire plan, and commits categories, products, variants, opening/adjustment movements, audit and summary together. A changed catalog or stock invalidates the preview. Repeating the same confirmed batch/token returns the original summary without adding stock again. A separately uploaded file is a new batch; deliberate adjustment reimports add their deltas again.

| Policy | New SKU | Existing SKU |
|---|---|---|
| DETAILS_ONLY | Product details; zero opening stock | Update details; quantity ignored with warning |
| OPENING_NEW | Explicit non-negative opening quantity | Details update only; quantity ignored with warning |
| ADJUST_DELTA | Non-negative opening quantity | Signed quantity creates an authorized movement; reason required |

Blank cost means unknown and clears a previous cost value during detail update. Blank active preserves existing state or defaults to true for new variants. Blank reorderLevel defaults to 3. Plain prices allow up to two decimal places and nine integer digits. An existing SKU identifies an update; another supplied barcode is rejected. New variants are grouped by product name/category and cannot duplicate an existing size/colour under another SKU. Shared name/category corrections affect sibling variants and produce warnings; conflicting updates in one workbook fail validation. Unchanged existing rows with no stock delta are skipped.

## Stock adjustments

Inventory → Adjust stock asks for signed units, effective date (not in the future), reason and confirmation. The caller must be an administrator. Current stock is rechecked; a stale balance or negative result rejects the transaction. A unique request key prevents double submission. The immutable movement and stock_adjustment record store operator, server timestamp, effective date, reason and quantity. Incorrect adjustments are corrected with a new compensating movement, never by deleting history. The history screen currently shows the most recent 500 movements per variant.

## Labels and printing

Select active variants and copies, configure width 35–110 mm / height 25–100 mm, preview, save PDF or choose an installed Windows printer. Maximum 50 selected variants and 200 total copies per job. Preview images are rendered from the same PDF geometry as printing at 203 DPI. Each label contains product description, variant, price and a readable permanent Code 128 value. Long product names wrap to two lines with visible truncation. Missing glyphs fail with an explanation instead of silently changing text. System Arial (Windows) or DejaVu (Linux tests) is used; system font files are not distributed.

The barcode retains quiet zones and rejects widths below a 0.19 mm module threshold. Long legacy codes can require wider labels; widening the label preserves the permanent code. Actual minimum readable size depends on printer/scanner DPI and calibration. Java PrintService / PrinterJob submits actual-size PDF pages to the chosen Windows driver. Print exceptions and unavailable printers show errors; PDFs remain available. A successful API response says **submitted to spooler**, not physically printed. Test print submits one clearly marked test label. Set printer-driver media dimensions to match the application, print at 100%, and verify the physical output. Windows driver/printer hardware faults after spooling are not reliably observable by this integration.

## REST / Electron boundaries

`/auth/status`, `/auth/setup`, `/auth/login`, `/auth/logout`, `/auth/users`; `/imports/upload`, `/{batch}/preview`, `/{batch}/commit`, `/{batch}/errors`, `/imports/template`, `/imports/history`; `/stock/adjustments`, `/stock/movements/{variant}`; `/labels/preview`, `/labels/pdf`, `/labels/printers`, `/labels/print`, `/labels/settings`. Every API call requires the local token. Mutations/imports need an administrator session; labels/history need a signed-in user. Catalog read endpoints retain Stage 1's token gate; desktop users see them after login. Passwords/session values are not logged. Electron holds session credentials in its main process and exposes only explicit request/upload/download operations through its sandboxed preload.

Uploaded worksheets are retained in local import staging for reproducible preview/history; retention management is deferred. No external workbook links/macros/formulas are executed. The source data may contain business information, so protect the local Windows account and data folder.

## Remaining stages

This stage does not implement POS checkout, payments, receipts, customer purchases, sales reports, returns or database backup/restore. See ROADMAP.md and STAGES.md. Printer/scanner hardware calibration and signed installer acceptance remain outstanding release checks.
