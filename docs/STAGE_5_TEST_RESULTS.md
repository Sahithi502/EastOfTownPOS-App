# Stage 5 verification — 2026-10-26, version 0.5.0

## Automated backend result

`mvn -B package`: **81 tests, 0 failures, 0 errors**. Suites: Catalog 7, Stage 2 21, Billing 16, Reports 16, Migration upgrade 3, Controls 18. Build produced backend/target/pos-0.5.0.jar. Tests create isolated temporary databases and TEST ONLY records; they do not seed production.

Controls tests cover percentage/fixed normalization and stored intent, HALF_UP calculations, invalid/mixed modes, exact tiny/free repeated returns, tax/discount refund reconciliation to the final paise, duplicate concurrent return idempotency, over-return/cancellation restrictions, immutable original invoices, rollback on injected refund failure, mismatched refund and reused-key rejection, non-restocked returns and monthly loss, missing cost coverage, expense retry/void audit, customer net purchases, WAL-consistent snapshots, portable export IDs, atomic restore/rescue, corrupt/tampered backup rejection, retention and destination failure, scheduled due/skip behavior, manifest SHA rejection, billing-password session revocation, self-administrator protection, unauthorized reads/writes and receipt recipient/PNG validation.

Financial examples: 3 × INR 10.01 less 12.50% line discount (INR 3.75), then 10% bill discount (INR 2.63), with 5% exclusive tax (INR 1.18), gives INR 24.83. A two-unit INR 200 sale with INR 80 cost, one INR 100 non-restocked refund, and INR 30 expenses gives net revenue INR 100, retained cost INR 80 and net loss INR 10. Unknown sale costs leave net-profit cells blank and report coverage explicitly.

## Desktop and API result

`npm.cmd run build`: TypeScript and Vite production build passed.

`npm.cmd run test:api`: catalog/auth/stock/labels and Stage 3 checkout/customer/payment/stock persistence checks passed across a real backend process restart. Retained isolated database directory: `C:\Users\lokes\AppData\Local\Temp\eastoftown-api-test-bwQcf8`.

`npm.cmd run test:desktop`: **all four Electron suites passed** (catalog/import/stock/labels; billing/receipts; customers/dashboard/reports; Stage 5 controls). Real REST/SQLite/Java/Electron code is used. Native file dialogs select test destinations; Stage 5 also mocks native confirmations and external WhatsApp opening. No message was sent and no delivery/WhatsApp login was tested.

Stage 5 desktop exercised 10% line + 5% bill discounts on INR 100 → INR 85.50, PDF/PNG preparation with the same 80 mm receipt layout, INR 10 rent expense → INR 35.50 net profit after INR 40 cost, full item return/refund/restock, administrator billing-password reset, verified manual backup, staging restore, write blocking while pending, actual app shutdown/relaunch restoration and rescue-copy retention. It verified a post-backup category disappears after restore while original expense/refund records remain. Retained final isolated profile: `C:\Users\lokes\AppData\Local\Temp\eot-desktop-stage5-V3ISDS`.

Screenshots under docs/screenshots/stage5-*.png show actual test records and were visually inspected. Inspection found the longer sidebar could hide administration/sign-out links at short window heights; it now scrolls and keeps account controls in normal flow. Screenshots were regenerated in the final passing desktop run.

## Existing installation upgrade

`node scripts/verify-existing-upgrade.cjs --source <existing database>` opened the showroom source read-only, made consistent copies, booted 0.5.0 against a test copy and compared sorted fingerprints of every pre-existing business table's original columns. **Passed**: all original rows/values and migration checksums preserved, V007 applied, health and foreign keys valid. Original source was not migrated or restored by the test.

Retained original backup: `C:\Users\lokes\AppData\Local\Temp\eot-existing-upgrade-dz7468\before-upgrade.db`.
Retained upgraded copy: `C:\Users\lokes\AppData\Local\Temp\eot-existing-upgrade-dz7468\eastoftown.db`.

## Failures detected and corrected during development

The first compile found a qualified-type validation annotation syntax error; corrected the import. Old regression assertions expected six migrations and an unavailable returns report; they now expect seven and reject an actually unknown report. A new backup-count assertion compared Integer with Long; fixed its expected type. Desktop tests found an ambiguous status selector, a native/browser confirmation mock conflict and a missing exact accessible name on the billing-user selector; corrected the tests and accessible label. A receipt annotation syntax error was fixed before the final passing package build. No migration checksum was rewritten and no barcode data/layout was changed.

## Manual acceptance for the owner

1. In an evaluation profile, test line and bill INR/% modes, limits and review totals.
2. Return one unit, retry the same pending request and attempt an excessive return; stock/refund must not duplicate. Test a non-restocked damaged return and a full cancellation.
3. Save/void an expense and reconcile a full-month profit/loss report; check missing-cost behavior and Excel/PDF exports.
4. Reset a billing password as administrator; verify old login/session no longer works and restricted actions are denied to billing users.
5. Prepare PDF/PNG receipts, cancel the native confirmation, verify the chosen recipient, then manually attach/review/send using your own WhatsApp account and verify customer receipt.
6. Choose an external backup folder/schedule, verify manual backup and destination failure, then restore only into an evaluation installation first. Reconcile invoices, refunds, stock, expenses and customer balances.

## Remaining release gates

`npm.cmd run package:dir` passed and produced release/win-unpacked/East of Town.exe with the 0.5.0 backend and bundled runtime. Windows CI and merge evidence are recorded in the Stage 5 pull request. Physical receipt/label printing and scanner acceptance, actual WhatsApp delivery, trusted installer signing, clean-machine installation, Windows reboot/crash and final release handover remain Stage 6. These automated passes do not claim hardware output or delivery success.
