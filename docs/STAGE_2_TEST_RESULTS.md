# Stage 2 verification — 24 October 2026

## Automated results

| Check | Result | Evidence |
|---|---|---|
| Java compilation/package and tests | PASS — 25 tests, 0 failures/errors | 7 Stage 1 regressions + 18 Stage 2 tests in Maven Surefire reports |
| TypeScript and renderer production build | PASS | `npm.cmd run build` |
| REST/process restart smoke test | PASS | `npm.cmd run test:api`, isolated SQLite database, Java fallback exercised |
| Electron workflow test | PASS | `npm.cmd run test:desktop`, unique temporary profile, real backend and SQLite |
| npm registry audit | PASS — zero advisories reported | lockfile install and production audit on 24 October 2026 |
| Windows application directory packaging | PASS | `npm.cmd run package:dir` created `release/win-unpacked/` with runtime and backend. Packaged EXE launch/signature acceptance is not claimed. |
| V001 upgrade compatibility | PASS | migration test preserves legacy barcode, stock and checksum |
| Physical printer/scanner calibration | PENDING | Requires the shop's selected label stock, printer driver and scanner |
| Signed installer / clean-machine/reboot acceptance | PENDING | Stage 6 release gates; unsigned installers may be blocked by Smart App Control |
| NSIS installer attempt | FAILED locally | `spawn UNKNOWN` while Electron Builder invoked its generated uninstaller; source/renderer/backend tests passed; directory packaging is available separately. No Windows protection was disabled. |

The Windows GitHub workflow repeats compilation/tests/build/API/desktop verification using Temurin Java 21 and Node 24 on pull requests and main. Its run result is recorded in the pull request/checks, not inferred from local success.

The final local API and Electron runs both used the prepared Temurin Java 21 runtime. Initial runs also exercised JAVA_HOME/PATH fallback without a runtime folder. The NSIS failure does not change the passing source/runtime test results and remains an explicit release-packaging limitation.

## Financial and inventory integrity covered

- Exact decimal-to-paise prices and database persistence; duplicate SKU/foreign-key transaction rollback; stale product edit protection; stock and barcode preservation during detail edits.
- Multi-category Excel import with grouped size/colour variants, generated unique barcodes, opening quantities, history counts and repeat-confirmation idempotence.
- Row errors for duplicate SKUs/barcodes, missing name, invalid money/negative quantity, existing barcode replacement/collision, numeric identifiers and formula cells. Invalid batches leave all business records unchanged; correction workbook export is readable.
- Details-only and opening-new policies preserve existing quantities. Explicit signed deltas create audited stock movements and reject overdraw. Stale preview/token and inconsistent shared product updates are rejected.
- A temporary test-only database trigger forces failure on the second imported variant. The first variant, new category, movements and history roll back, the batch remains previewable, and retry succeeds after removing the injected fault.
- Concurrent identical stock-adjustment requests produce one adjustment. Changed request details, stale balances and negative resulting quantities are rejected. Fractional JSON stock quantities are rejected rather than truncated.
- REST rejects missing user authorization and billing-user writes. Owner bootstrap cannot replace the existing administrator; passwords are BCrypt hashes. User creation and inventory/import changes carry audit records.
- Both .xls and .xlsx parsing; empty downloadable template with instructions/categories; corrected column assignment; invalid file errors.
- Code 128 image decoding returns the original value. Label PDFs have the requested page dimensions and copy count, render successfully, reject narrow legacy-code labels and excessive copies, and preserve stored values. Inactive variants/unavailable printers fail clearly.

## Desktop acceptance covered

The automated test starts an Electron desktop with a uniquely named temporary profile, creates a clearly test-only administrator, downloads the real Excel template through the native save bridge, uploads an invalid workbook, previews errors, exports the correction workbook, then uploads valid test stock. It confirms the batch, verifies stock 7, adjusts +2, verifies history and stock 9, renders labels and saves a real PDF. It closes/relaunches the app and verifies stock and import history persist. Only native dialog destinations are substituted; REST/controllers/services/SQLite/Excel/PDF generation are real.

Fixtures are created under `backend/target/test-fixtures/` by the Java suite. No real shop stock or customer records are used. Test profiles/databases are outside normal application data and are retained for inspection. No actual printer job is submitted by automated tests.

Screenshots visually inspected: [mapping/preview](screenshots/stage2-import-preview.png), [row errors](screenshots/stage2-import-errors.png), [stock correction](screenshots/stage2-stock-adjustment.png), [PDF-rendered label](screenshots/stage2-barcode-labels.png). Early desktop test attempts exposed test-selector and dialog-stub errors; these were corrected before the passing run. No failing check is represented as passed.

## Reproduce

Close the running app, open Command Prompt and run:

```cmd
cd /d C:\Ammu\EastOfTown\EastOfTownPOS-App
npm.cmd ci
npm.cmd run build
cd backend
mvn.cmd package
cd ..
npm.cmd run test:api
npm.cmd run test:desktop
```

The API/desktop tests use bundled Java if present, otherwise JAVA_HOME/PATH. Self-contained packaging separately requires `scripts/prepare-runtime.ps1` and `npm.cmd run package:win`. An installer build is not proof of clean-machine installation. Java process/application restart is verified; Windows reboot, crash/power-loss recovery, physical printing and optical scan acceptance remain unverified.

## Hardware acceptance before showroom use

Select the actual driver/media size; test one label, measure dimensions, check long-name and legacy-code output, scan it into product search and verify the exact SKU/size/colour, then test multiple copies and a disconnected/out-of-paper printer. Check Windows spooler state and PDF fallback. A job accepted by the spooler is reported as submitted, not physically printed. Record model, driver version, DPI, label dimensions and measured scan/print result in this document before declaring hardware acceptance.
