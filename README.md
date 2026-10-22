# East of Town Men's Fashion

Offline Windows retail application, implemented in the requested stages. **Current milestone: Stage 1 catalog foundation. This is not yet a production billing release.**

Implemented: Electron desktop shell, React/TypeScript catalog, category creation, product and size/colour variant creation/editing, permanent unique internal barcode values, opening-stock ledger, low-stock indicators, optimistic edit protection, local SQLite persistence, checksummed versioned migrations, audit events and exact integer-paise prices. No business demo data is seeded.

Not yet implemented: checkout, Excel import/export, rendered Code 128 labels, printers, customers, sales reports, returns, user roles, backup/restore and final installer validation. These are tracked in [the roadmap](docs/ROADMAP.md). Inventory is read-only after opening stock in this milestone. Do not enter live trading records until release acceptance is complete.

## Development on Windows

Prerequisites: Windows 10/11 x64, JDK 21 or 25, Maven 3.9+, Node.js 22.12+ (24 LTS recommended). Initial dependency downloads require internet. Running the built app uses local resources only.

```powershell
cd C:\Ammu\EastOfTown\backend
mvn clean package
cd ..
npm.cmd ci
npm.cmd run build
npm.cmd start
```

The desktop launcher starts and monitors Java automatically. It selects a loopback port and gives the backend a random per-launch secret, never exposed to the renderer. `npm run dev` is for isolated frontend design; the complete app uses the compiled renderer with `npm start`.

## Data

Electron stores the production database under `app.getPath('userData')\data\eastoftown.db` (normally `%APPDATA%\east-of-town-pos\data`). `backend.log` is in the same folder. Upgrades must preserve this folder. Direct Java execution defaults to `%LOCALAPPDATA%\EastOfTown\data`; use the launcher consistently to avoid separate databases. An `EOT_DATA_DIR` override is supported for standalone backend tests. Never copy a live WAL database by copying only its `.db` file.

The automated integration suite uses a unique temporary SQLite test database. No production database is opened by tests.

## Windows packaging

Build the backend and renderer first. Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-runtime.ps1` to download and SHA-256 verify Temurin 21 into `runtime/`. The exact downloaded release/checksum/source is recorded in `docs/runtime-manifest.json`; upstream legal notices remain in the runtime. Then run `npm.cmd run package:win`. Electron Builder creates the NSIS installer in `release/`. The runtime is bundled so the shop PC does not need Java installed separately. Signing, clean-machine installation, restart, restore, scanner and printer acceptance remain Stage 6 release gates.

Run `mvn test` in `backend/` for the integration suite. After preparing the runtime and building the backend, run `npm.cmd run test:api` from the project root for a REST test with real process restart using the bundled Java 21 runtime. Its uniquely named temporary database is retained for inspection. The development desktop can use a separate profile by setting `EOT_TEST_PROFILE` before `npm start`; the packaged app ignores this development-only override.

Read [operating steps](docs/USER_MANUAL.md), [architecture](docs/ARCHITECTURE.md), [dependencies](docs/DEPENDENCIES.md), and [milestones](docs/ROADMAP.md).
