# Stage 3 — POS, payments and receipts (0.3.0)

## Delivered behavior
- New bill screen with exact barcode lookup, product/SKU search, per-variant cart, quantity edits/removal and line/bill discounts. Repeated scans increment one line up to available stock. Unknown/inactive/out-of-stock scans show errors. F2 focuses scanning; F9 reviews the bill.
- Backend quotation and confirmation. Price/tax/receipt-setting changes after review require a new review. Quantity/stock is checked again at commit. Prices cannot be overridden through checkout.
- Cash, UPI, card and split payment records. Confirm only after receiving payment: this app does not initiate or verify bank/UPI transfers. Split amounts must reconcile exactly. Optional cash tender/change is display-only; collections record only the applied sale amount.
- Guest checkout, customer search and creation with duplicate mobile detection. Stable customer ID and immutable customer name/mobile snapshots link purchases. Full customer master management, purchase history screens, totals and dashboards remain Stage 4.
- Unique sequential EOT-YYYYMMDD-000001 invoice numbering, using a global counter which does not reset at midnight. Administrator may configure the prefix; old invoices retain their numbers.
- Invoice history search by invoice number, customer/mobile, date range and exact amount; view immutable invoice lines/payments and print again.
- Dynamic-height 58 mm and 80 mm PDF receipts; long receipts paginate. Shop name/address/contact/footer are snapshotted at sale. Windows print submission, default printer, test receipt, optional automatic printing, explicit print failure and PDF fallback.
- Administrators configure billing users' maximum combined discount percentage and each variant's tax rate. Billing users default to zero discount authority. Administrators may discount up to the pre-tax value. Tax defaults to 0%; there is no assumed business tax rate.

## Calculation policy
All REST currency inputs are non-negative integer paise; fractional integer coercion is disabled. UI parses typed rupees as decimal text, without floating-point currency conversion. Catalog prices use BigDecimal conversion. Backend uses exact integer operations; tax uses BigDecimal HALF_UP rounding to one paise per line.

Line discount is an absolute amount for the entire quantity on that line. Bill discount is distributed across lines after line discounts, proportionally, using largest remainder allocation. This makes allocations sum exactly to the bill discount. Per-variant tax is exclusive: it is added to each line's discounted base, with its configured basis-point rate (100 basis points = 1%). Invoice payable = subtotal - all discounts + tax. A zero-total sale records stock and an invoice with no payment rows.

Example: two units at INR 125.00, line discount INR 5.00, bill discount INR 10.00, no tax: subtotal INR 250.00, total INR 235.00. Cash INR 100.00 + UPI INR 135.00 reconciles to that sale.

## Transaction and recovery
Checkout obtains the shared database writer lock before rereading inventory, validating quotation and advancing the sequence. One transaction saves invoice, invoice lines, payments, sale movements, checkout request and audit. Late failure rolls back all records and the sequence advance.

The renderer persists a UUID request key and the exact request before submitting; it disables repeat confirmation and retains the request after an uncertain result/restart. Check status or retry the identical request. The server stores its hash and actor. Same request returns the original invoice before checking present-day catalog/stock; changed request or actor is rejected. A unique sale-movement index also prevents duplicate movement for the same invoice/variant. A status check takes the writer lock, so it waits behind an in-progress checkout.

A saved invoice survives printer failure. Receipt printing is a separate operation after commit; retries cannot create another sale. Submitted means accepted by the print spooler, not proof of paper output.

## Schema and endpoints
V004 adds invoices, invoice_items and payments. V005 preserves V004 and adds customers, checkout_requests, variant_taxes, sequence/configuration and invoice snapshot columns. See SCHEMA.md.

Under /api/billing: quote, checkout, requests/{UUID}, invoices (filters), invoices/{UUID}, customers (search/create), settings, taxes, invoices/{UUID}/pdf, invoices/{UUID}/print and test-print. Reads and billing require a signed-in user; settings/tax writes require ADMIN. The restricted Electron bridge explicitly allows these operations and receipt downloads only.

## Evaluation and limits
See STAGE_3_TEST_RESULTS.md and USER_MANUAL.md. Automated suites use temporary test databases/profiles and TEST ONLY fixtures. No sample sales are seeded in the normal profile. Physical printer calibration, actual scanner readability, signed installer, clean-machine/reboot acceptance and backup/restore remain later release gates. Barcode label orientation is unchanged. WhatsApp receipt export and administrator reset of billing-user passwords remain deferred for approval in Stage 5.
