# East of Town Men's Fashion

[BugList and review status](docs/BUGLIST.md) — tracked navigation, user-management and error-message fixes.
Offline Windows retail application, implemented in the requested stages. **Current milestone: Stage 6 release candidate 0.6.0 — native Windows receipt sharing, country/mobile validation, live billing totals and release verification. Trusted signing, clean-machine and shop hardware acceptance remain required.**

Current release limitation: this PC's Code Integrity policy blocks the freshly rebuilt unsigned share helper and NSIS bootstrap. The directory application and ordinary billing/restart tests passed; Windows sharing needs trusted signing/device-policy acceptance here. Use the manual receipt fallback meanwhile. The incomplete installer is marked .failed. See [actual Stage 6 evidence](docs/STAGE_6_TEST_RESULTS.md); do not treat this candidate as a signed production installer.

Version 0.4.1 fixes startup for the released Stage 3 migration checksum without resetting data. See [upgrade diagnosis and validation](docs/UPGRADE_0_4_1.md). A restart alone cannot fix version 0.4.0's checksum mismatch; use the corrected build.

Implemented: persistent catalog/categories/variants, exact integer-paise prices, stock ledger and history, administrator/billing login, audited stock adjustments, Excel template/upload/mapping/validation/confirmation/history, error workbook export, Code 128 label preview/PDF/Windows print submission, configurable dimensions/printer and test printing. No business demo data or default credentials are seeded.

Stage 3 adds barcode POS billing, authorized discounts, per-variant tax, cash/UPI/card/split payments, atomic stock deduction, durable retries, invoice search and 58/80 mm PDF/print/reprint receipts. Stage 4 adds customers with audited administrator corrections, paged purchase history, date-filtered dashboard and sales/payment/stock/customer/inventory/profit reports with Excel/PDF export. Stage 5 adds returns/refunds/cancellations, user recovery/access controls, percentage/INR discounts, operating expenses/monthly net profit/loss, WhatsApp PDF/PNG preparation and verified backup/restore. Stage 6 adds country-based customer mobile validation, current sharing contacts without rewriting historical invoices, live backend-priced line totals, native Windows Share with receipt files, packaged acceptance and release/signing checks. See [Stage 6](docs/STAGE_6.md), [verification](docs/STAGE_6_TEST_RESULTS.md) and [release/handover](docs/RELEASE.md). Physical printer/scanner calibration requires your devices. See [Stage 5 operating and safety details](docs/STAGE_5.md), [Stage 5 test evidence](docs/STAGE_5_TEST_RESULTS.md), [future migration](docs/CLOUD_MIGRATION.md), [Stage 4 features](docs/STAGE_4.md), [Stage 4 verification](docs/STAGE_4_TEST_RESULTS.md), [Stage 3 billing](docs/STAGE_3.md), [database schema](docs/SCHEMA.md), [stage index](docs/STAGES.md) and [roadmap](docs/ROADMAP.md).

## Development on Windows

Prerequisites: Windows 10/11 x64 with .NET Framework 4.8, JDK 21 or 25, Maven 3.9+, Node.js 22.12+ (24 LTS recommended). Initial dependency downloads require internet. Running the built app uses local resources only.

```powershell
cd C:\Ammu\EastOfTown\EastOfTownPOS-App\backend
mvn clean package
cd ..
npm.cmd ci
npm.cmd run build
npm.cmd run build:share
npm.cmd start
```

The desktop launcher starts and monitors Java automatically. It selects a loopback port and gives the backend a random per-launch secret, never exposed to the renderer. `npm run dev` is for isolated frontend design; the complete app uses the compiled renderer with `npm start`.

Close the application before rebuilding, because Windows locks the running JAR. If using Command Prompt, start with `cd /d C:\Ammu\EastOfTown\EastOfTownPOS-App`. For subsequent daily launches, run only `npm.cmd start` in the repository root. First launch of Stage 2 asks you to create an administrator (username 3–40 permitted characters, password at least 12 characters) and displays a one-time recovery code. Save that code securely. Existing Stage 1 inventory/barcodes are preserved by V002; V003 adds recovery-code storage for existing installations.

## Data

Electron stores the production database under `app.getPath('userData')\data\eastoftown.db` (normally `%APPDATA%\east-of-town-pos\data`). `backend.log` is in the same folder. Upgrades must preserve this folder. Direct Java execution defaults to `%LOCALAPPDATA%\EastOfTown\data`; use the launcher consistently to avoid separate databases. An `EOT_DATA_DIR` override is supported for standalone backend tests. Never copy a live WAL database by copying only its `.db` file.

The automated integration suite uses a unique temporary SQLite test database. No production database is opened by tests.

## Windows packaging

Build the backend and renderer first. Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-runtime.ps1` to download and SHA-256 verify Temurin 21 into `runtime/`. The exact downloaded release/checksum/source is recorded in `docs/runtime-manifest.json`; upstream legal notices remain in the runtime. Then run `npm.cmd run package:win`. Electron Builder creates the NSIS installer in `release/`. The runtime is bundled so the shop PC does not need Java installed separately. Signing, clean-machine installation, reboot, scanner and printer acceptance remain Stage 6 release gates.

`npm.cmd run package:dir` builds `release/win-unpacked/` without invoking NSIS's generated uninstaller. The local Stage 2 NSIS attempt failed during generated-uninstaller execution (`spawn UNKNOWN`); it is not claimed as a working installer. The directory build and source launch are available for evaluation. See STAGE_2_TEST_RESULTS.md for current limitations.

For Microsoft Store packaging, `npm.cmd run package:store:test` builds and inspects an unsigned test-identity AppX package; it is not installable or ready for Partner Center. A production Store package requires the reserved Partner Center identity values and is built with `npm.cmd run package:store`. Store submission/signing, private-flight testing and shop-device acceptance remain pending. See [Microsoft Store release steps](docs/RELEASE.md#microsoft-store-package-appxmsix-family).

Run `mvn test` in `backend/` for the integration suite. After building the backend, run `npm.cmd run test:api` from the project root for REST tests with real process restart. Java resolution is bundled runtime → JAVA_HOME → PATH; runtime preparation is only required for self-contained packaging. Run `npm.cmd run test:desktop` after `mvn package` and `npm run build` for the Electron import/stock/label and billing/payment/receipt/recovery workflows. Both smoke tests use unique temporary databases and retain them for inspection. Desktop tests generate screenshots in `docs/screenshots/` and mock only native file-chooser destinations, not the backend or import behavior. A development desktop can use a separate profile by setting `EOT_TEST_PROFILE` before `npm start`; the packaged app ignores this override.

Read [operating steps](docs/USER_MANUAL.md), [architecture](docs/ARCHITECTURE.md), [dependencies](docs/DEPENDENCIES.md), and [milestones](docs/ROADMAP.md).

Stage 4: administrators see **Dashboard**, **Customers** and **Reports** in the sidebar. Billing users can create/view customers and purchases; store reports, cost data and corrections require an administrator. Date filters use Indian time. Inventory valuation is current; gross profit estimates exclude missing-cost lines and operating expenses. See STAGE_4.md for all definitions and limits. `npm.cmd run test:desktop` now runs all five desktop suites; `npm.cmd run test:reports-desktop` runs Stage 4 alone. No extra dependency installation is required when the existing locked packages are already installed.

Stage 5: sign in as Administrator and open **Business controls** for Returns, Expenses, Backup and restore, and User access. **New bill** has INR/% selectors. **Reports → Monthly net profit / loss** uses recorded expenses and historical costs; missing costs are explicit. **Saved invoice → Share purchase receipt** saves an 80 mm PDF/PNG and opens WhatsApp; attach/review/send manually. No message is sent automatically and no delivery is claimed. `node scripts/controls-desktop.cjs` verifies Stage 5 against its own temporary profile, with external WhatsApp opening mocked.

Stage 6: **New Bill** shows live line totals including quantity, discounts and tax while retaining the per-item price. **Customers** and checkout creation require a country and national mobile; India defaults to +91 with exactly ten digits beginning 6–9. An administrator corrects invalid legacy contacts in Customers. **Saved invoice → Send via Windows Share** supplies the PDF/PNG attachment; select WhatsApp, the customer and Send there. **Save and open WhatsApp** is the manual fallback. Windows controls which targets appear; opening the panel does not prove sending or delivery. Barcode orientation remains unchanged. Source/package releases share the same existing data folder. See docs/RELEASE.md for exact build, signing and acceptance commands.
