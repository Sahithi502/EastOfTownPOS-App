# East of Town Men's Fashion

Offline Windows retail application, implemented in the requested stages. **Current milestone: Stage 3, version 0.3.0 — offline billing, payments, invoice history and thermal receipts.**

Implemented: persistent catalog/categories/variants, exact integer-paise prices, stock ledger and history, administrator/billing login, audited stock adjustments, Excel template/upload/mapping/validation/confirmation/history, error workbook export, Code 128 label preview/PDF/Windows print submission, configurable dimensions/printer and test printing. No business demo data or default credentials are seeded.

Stage 3 adds barcode POS billing, authorized discounts, per-variant tax, cash/UPI/card/split payments, basic customer matching/creation, atomic stock deduction, durable checkout retries, invoice search and 58/80 mm PDF/print/reprint receipts. Not yet implemented: full customer history/reporting, returns/refunds, backup/restore and final release acceptance. Physical label-printer calibration and scanning require your devices. Use isolated test stock until release acceptance is complete. See [Stage 3 billing and acceptance](docs/STAGE_3.md), [Stage 3 tests](docs/STAGE_3_TEST_RESULTS.md), [database schema](docs/SCHEMA.md), [Stage 2 design and acceptance](docs/STAGE_2.md), [stage index](docs/STAGES.md) and [roadmap](docs/ROADMAP.md).

## Development on Windows

Prerequisites: Windows 10/11 x64, JDK 21 or 25, Maven 3.9+, Node.js 22.12+ (24 LTS recommended). Initial dependency downloads require internet. Running the built app uses local resources only.

```powershell
cd C:\Ammu\EastOfTown\EastOfTownPOS-App\backend
mvn clean package
cd ..
npm.cmd ci
npm.cmd run build
npm.cmd start
```

The desktop launcher starts and monitors Java automatically. It selects a loopback port and gives the backend a random per-launch secret, never exposed to the renderer. `npm run dev` is for isolated frontend design; the complete app uses the compiled renderer with `npm start`.

Close the application before rebuilding, because Windows locks the running JAR. If using Command Prompt, start with `cd /d C:\Ammu\EastOfTown\EastOfTownPOS-App`. For subsequent daily launches, run only `npm.cmd start` in the repository root. First launch of Stage 2 asks you to create an administrator (username 3–40 permitted characters, password at least 12 characters) and displays a one-time recovery code. Save that code securely. Existing Stage 1 inventory/barcodes are preserved by V002; V003 adds recovery-code storage for existing installations.

## Data

Electron stores the production database under `app.getPath('userData')\data\eastoftown.db` (normally `%APPDATA%\east-of-town-pos\data`). `backend.log` is in the same folder. Upgrades must preserve this folder. Direct Java execution defaults to `%LOCALAPPDATA%\EastOfTown\data`; use the launcher consistently to avoid separate databases. An `EOT_DATA_DIR` override is supported for standalone backend tests. Never copy a live WAL database by copying only its `.db` file.

The automated integration suite uses a unique temporary SQLite test database. No production database is opened by tests.

## Windows packaging

Build the backend and renderer first. Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-runtime.ps1` to download and SHA-256 verify Temurin 21 into `runtime/`. The exact downloaded release/checksum/source is recorded in `docs/runtime-manifest.json`; upstream legal notices remain in the runtime. Then run `npm.cmd run package:win`. Electron Builder creates the NSIS installer in `release/`. The runtime is bundled so the shop PC does not need Java installed separately. Signing, clean-machine installation, restart, restore, scanner and printer acceptance remain Stage 6 release gates.

`npm.cmd run package:dir` builds `release/win-unpacked/` without invoking NSIS's generated uninstaller. The local Stage 2 NSIS attempt failed during generated-uninstaller execution (`spawn UNKNOWN`); it is not claimed as a working installer. The directory build and source launch are available for evaluation. See STAGE_2_TEST_RESULTS.md for current limitations.

Run `mvn test` in `backend/` for the integration suite. After building the backend, run `npm.cmd run test:api` from the project root for REST tests with real process restart. Java resolution is bundled runtime → JAVA_HOME → PATH; runtime preparation is only required for self-contained packaging. Run `npm.cmd run test:desktop` after `mvn package` and `npm run build` for the Electron import/stock/label and billing/payment/receipt/recovery workflows. Both smoke tests use unique temporary databases and retain them for inspection. Desktop tests generate screenshots in `docs/screenshots/` and mock only native file-chooser destinations, not the backend or import behavior. A development desktop can use a separate profile by setting `EOT_TEST_PROFILE` before `npm start`; the packaged app ignores this override.

Read [operating steps](docs/USER_MANUAL.md), [architecture](docs/ARCHITECTURE.md), [dependencies](docs/DEPENDENCIES.md), and [milestones](docs/ROADMAP.md).
