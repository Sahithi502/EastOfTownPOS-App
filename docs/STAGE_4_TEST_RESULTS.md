# Stage 4 verification — 26 October 2026

0.4.1 adds three released-database migration regressions (63 total backend tests). The original 0.4.0 tests below used fresh/canonical migration files and missed the final-line CRLF checksum in the released Stage 3 JAR. See UPGRADE_0_4_1.md for the cause, restricted compatibility check and existing-database copy validation procedure.

Test databases and Electron profiles are temporary and separate from production.

Verified locally: `mvn -B package` passes all 60 tests (7 catalog, 21 Stage 2, 16 billing, 16 reports); `npm.cmd run build` passes; `npm.cmd run test:api` passes with real backend restart; Stage 2/3 Electron regressions pass; `npm.cmd run test:reports-desktop` passes the Stage 4 workflow. The GitHub Windows PR check runs all three desktop suites together and is the authoritative CI gate before merge.

`npm.cmd run package:dir` succeeds and creates the version 0.4.0 Windows application folder with backend and bundled runtime under release/win-unpacked. This is a directory build, not a claim of signed-installer or clean-machine acceptance.

## Reconciliation fixture

Two completed invoices: known-cost shirt, 2 × INR 100.00 less INR 10.00 line discount and INR 10.00 bill discount, plus 5% tax = INR 189.00; guest sale of unknown-cost tee = INR 50.00. Payments: INR 100.00 cash, INR 89.00 card, INR 50.00 UPI.

Expected gross INR 250.00; discounts INR 20.00; net sales before tax INR 230.00; tax INR 9.00; invoice value/collections INR 239.00; 2 bills; average INR 119.50. Known-cost revenue INR 180.00 less snapshotted cost INR 120.00 = INR 60.00 gross profit estimate; one sold unit has unknown cost. Guest is excluded from customer totals, so the customer has one purchase of INR 189.00.

## Automated backend coverage

ReportTest adds 16 tests to the prior 44 tests: exact dashboard/payment totals; product/category/variant reconciliation; historical profit costs after catalog changes; current inventory with missing costs/inactive variants; daily/monthly/yearly Indian date boundaries including fractional/exact midnight; empty periods; week/financial-year presets and invalid ranges; transactional customer correction/version/audit with preserved receipts; duplicate/literal searches; customer history/payment exports; more than 50 purchases and paging; cancellation exclusion; signed stock movements; typed Excel/formula-safe literal text; long multi-page PDF with final-record preservation; REST input validation, role enforcement and PDF download.

## Desktop and process coverage

scripts/reports-desktop.cjs builds isolated stock/customer/invoices through the real backend and checks dashboard amounts, inventory/missing costs, customer history/receipt reopening, correction audit, every report type, native Excel/PDF saves and customer history exports. It restarts Electron and confirms sales, corrected contact information and audits persist; billing users cannot access store reports/costs or correct customers. Native destination selection is mocked; report bytes and business behavior are real.

Existing Stage 2 and Stage 3 Electron suites and REST process restart suite remain regression gates. CI runs all backend tests, npm ci, TypeScript/build, REST restart tests and all three Electron suites on Windows.

## Development findings

Initial PDF verification assumed random UUID ordering would select the known-cost product; the fixture now explicitly selects that SKU. A separate text assertion now accounts for wrapped final-row text. Visual inspection found a PDF row separator intersecting the next row; spacing was corrected. The first Electron run found the report selector lacked an explicit accessible name for exact selection; report/customer selectors now have explicit labels, and the full Stage 4 run passes. These were detected before merge. Dashboard, customer history/audit and report screenshots were visually reviewed and saved under docs/screenshots/stage4-*.png; backend/target/test-fixtures/stage4-profit.png verifies the rendered PDF spacing.

## Remaining acceptance

Real printer calibration, signed installer, clean-machine reboot and hardware acceptance remain Stage 6. Reports print through exported PDFs; thermal receipt/label printing retains Stage 2/3 behavior. No real customer fixtures are inserted into production. Returns/refund-aware totals require Stage 5 reversal records.
