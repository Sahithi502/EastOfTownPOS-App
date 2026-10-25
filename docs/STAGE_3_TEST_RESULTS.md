# Stage 3 verification — 25 October 2026

## Local evidence
- Maven package: 44 backend tests pass (7 catalog, 21 Stage 2, 16 Stage 3). Includes exact mixed-rate tax/discount reconciliation, half-paise rounding, zero-total checkout, split payments, stale quotes, insufficient stock, permissions, duplicate mobile detection and independent SQLite connection persistence, and Indian-midnight date filtering.
- Transaction fault injection: failure during payment insertion rolls back invoice, lines, movements and invoice sequence.
- Concurrent requests: identical keys return one invoice; competing sales of the last unit complete once without overselling. Changed payload/actor on a used key is rejected.
- Receipt tests: preserved prices/shop/customer details, 58/80 mm page width, long text/pagination and missing-printer failure without losing invoice. PDFBox renders actual PDFs; PNG/PDF fixtures are retained under backend/target/test-fixtures.
- Renderer TypeScript/Vite build passes.
- REST smoke tests use the bundled Java runtime and real backend stop/start. Checkout/payment/stock and identical retry persist across restart. Missing printer returns an error and retains the sale.
- Electron Stage 2 acceptance retains Excel template/error export, mapping/validation/import, inventory adjustment/history, label preview/export and restart checks.
- Electron Stage 3 acceptance covers scanner text+Enter, repeated scans, unknown barcode, line/bill discounts, new customer, split payment, receipt PDF at both widths, invoice search and recovery after a committed request with lost confirmation.
- Generated screenshots: stage3-new-bill.png, stage3-payment.png, stage3-receipt.png, stage3-invoice-history.png, stage3-billing-settings.png in docs/screenshots.

Commands: mvn -B package (backend directory), npm.cmd run build, npm.cmd run test:api, npm.cmd run test:desktop. The last runs both desktop suites. npm.cmd run test:billing-desktop targets billing alone.

## Corrections found during development
The earlier billing draft was incomplete and was not merged. Work corrected missing SQL placeholders, lack of checkout idempotency, unsupported first-invoice lookup, absent tax/discount permissions and missing receipt workflow. Tests also caught receipt service proxy wiring, an outdated migration-count expectation and an inaccessible payment selector and a hidden automatic-print error; these were corrected before delivery. Java test spawning requires execution outside the restricted sandbox on this workstation.

## Remaining acceptance
The automated test creates no real printer jobs and uses isolated test databases. Real USB scanner hardware, 58/80 mm driver calibration, paper/cutter/output, power-loss/reboot testing and signed installer acceptance still require the target hardware and Stage 6 checks. Cash/UPI/card entries record the operator's confirmed payment; no payment gateway is integrated. Customer reports, returns/refunds, backups/restore and the two deferred recovery/WhatsApp features remain later stages.

GitHub Actions runs the same backend, frontend, REST and Electron checks on the PR before merge; the PR check result is the authoritative CI evidence.
