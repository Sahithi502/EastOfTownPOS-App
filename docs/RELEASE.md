# Windows releases and handover

## Build prerequisites

Use Windows 10/11 x64, Node 24 LTS, Maven 3.9+, JDK 21 (25 also tested locally), and Windows .NET Framework 4.8. Dependencies are locked in package-lock.json and backend/pom.xml. No production database belongs in the checkout or release archive. Close the POS before rebuilding its JAR.

From Command Prompt:

```cmd
cd /d C:\Ammu\EastOfTown\EastOfTownPOS-App
git pull --ff-only
npm.cmd ci
cd backend
mvn.cmd -B package
cd ..
npm.cmd run build
npm.cmd run build:share
npm.cmd run test:api
npm.cmd run test:desktop
npm.cmd run test:share
```

`npm.cmd run test:share -- --show` briefly opens native Windows Share with a test file and automatically closes it; it selects no app and sends nothing. Normal `test:share` prepares an attachment without opening the chooser. Source daily launch is simply `npm.cmd start` after a successful build.

Prepare runtime only if runtime/ does not already exist:

```cmd
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-runtime.ps1
```

This verifies the upstream archive SHA-256 and retains runtime licenses and provenance. ExecutionPolicy here applies only to this command and does not change the computer's policy settings. An existing runtime is never silently overwritten.

## Evaluation package and checks

```cmd
npm.cmd run package:dir
npm.cmd run test:packaged
npm.cmd run release:check
npm.cmd run package:win
```

The directory package is `release/win-unpacked/East of Town.exe`; keep the complete folder together. NSIS output, if built successfully, is `release/East of Town Setup 0.6.0.exe`. `release:check` records package SHA-256 and actual signature status in release/release-manifest.json. Repeat it after generating/signing the installer. Binaries, runtime and test databases remain excluded from Git.

Local 26 October result: directory packaging/bundled-runtime acceptance passed. NSIS generation and the freshly rebuilt unsigned share helper were blocked by this PC's Code Integrity signing policy. The incomplete installer is retained with a .failed extension. The strict release check records the failure and exits nonzero; it must not be waived for production. Complete legitimate signing and device-policy acceptance on the secured release machine, then repeat the checks. Do not use the incomplete bootstrap as an installer.

Packaged tests create `eot-acceptance-UUID` temporary profiles and launch with system Java removed from PATH. They verify the packaged executable, bundled Java, real checkout/stock/customer/backup and application restart. Browser offline simulation verifies local renderer operation; it does not prove physical network disconnection or Windows reboot. These tests are not a clean-machine installer acceptance claim.

## Trusted production signing

A legitimate organization signing certificate/private key or separately approved signing service is required. No usable code-signing certificate was available in this task's CurrentUser certificate store. Evaluation binaries are not trusted signed releases. Do not disable Smart App Control to distribute them. Signing does not by itself guarantee reputation or acceptance on every Windows configuration.

On a secured release machine, provision a trusted code-signing certificate with private key in CurrentUser/My and configure `EOT_SIGN_CERT_THUMBPRINT` to its thumbprint. build:share then signs and timestamps the freshly compiled helper and rejects an invalid signature. Configure Electron Builder's protected `CSC_LINK`/`CSC_KEY_PASSWORD` inputs (or the approved signing configuration) for the application and installer. Do not commit certificates, passwords or private keys; do not print secrets in build logs.

```cmd
npm.cmd run package:win:signed
npm.cmd run release:check -- --require-signed
```

The signed packaging command requires signing rather than silently publishing an unsigned application. The release check requires valid application/helper/installer signatures and the installer file. Verify the expected business publisher using Windows file properties and a trusted release machine before distribution. Store the release manifest with the exact installer; recheck SHA-256 after transfer.

## Microsoft Store package (AppX/MSIX family)

The stable electron-builder version locked by this project supports the Store-compatible AppX package format. Partner Center accepts `.appx` as well as `.msix` packages; Microsoft signs Store packages after certification. The app is a full-trust Electron desktop app and requires the `runFullTrust` capability. Store package creation does not use a publisher certificate from this PC.

Build a **test-identity package** and inspect its contents:

```cmd
npm.cmd run package:store:test
```

This creates `release/store-test/East of Town Store Test-0.6.0-x64.appx` and checks that Electron, the backend JAR, bundled Java and receipt helper are present and that no database is included. It uses `CN=ms` and `EastOfTownPOS.StoreTest`; this package is only for packaging checks. It is not signed for sideloading, cannot be uploaded as the production Store listing, and should not be installed on a shop computer.

For a production Store package, first create a **Company** developer account through the current Store signup flow, reserve the app in Partner Center, and copy its exact Package/Identity Name, Publisher ID, and publisher display name. Then build with those values (they are public package identity values, not private keys):

```cmd
set EOT_STORE_IDENTITY=IDENTITY_NAME_FROM_PARTNER_CENTER
set EOT_STORE_PUBLISHER=CN=PUBLISHER_ID_FROM_PARTNER_CENTER
set EOT_STORE_PUBLISHER_DISPLAY_NAME=VERIFIED_PUBLISHER_NAME
npm.cmd run package:store
```

The output is under `release/store/`. Submit that package to the reserved Partner Center app and use a private Store flight for shop testing after Microsoft certification. The Store re-signs the package; a certificate is not required for Store submission. A direct local sideload is a separate signed-package workflow and is not enabled by the test identity.

Before installing a Store flight on the shop PC, make an application backup to an external location and verify that it can be restored. Keep the existing installation/data intact until the Store version has passed checks for login, product import, sales, invoices, backup, upgrade persistence, printer/scanner, and Windows Share. Store-package data-location behavior must be confirmed on the actual flight before treating it as a production upgrade path. Store updates require internet; normal local billing remains offline.

The CI test package is deliberately unsigned and uses the test identity. Its successful build/contents check does not claim Store certification, signed installation, shop hardware compatibility, database migration, or a real-shop acceptance test.

## Clean-machine and shop acceptance

Run the following on an isolated Windows VM/test computer before installing over the shop's working installation. Do not silently replace an existing user's installation for a test.

1. Install using a normal billing-user Windows account without developer Node/Java/Maven installed. Record Windows build, publisher/signature, installer SHA and installation path. Confirm Windows protection permits the signed installer and share helper.
2. Create a test administrator and save the recovery code. Import the test workbook, generate permanent barcodes, print a sample and scan the exact size/colour.
3. Disconnect networking. Create a customer with country code, sell multiple variants, use both discount modes, split payments, reprint, return and inspect stock/history/reports. Confirm no network is needed for local billing. Reconnect before WhatsApp sending.
4. Test 58 mm and 80 mm physical receipts, 80 mm variable-height output, long names, configured label sizes/orientation, missing/offline printer, paper-out and reprint. Record driver/printer model and calibration. A submitted print job is not proof of paper output.
5. Test Windows Share separately with PDF and PNG on the installed WhatsApp version. Verify the actual attachment preview, correct selected customer and successful operator-initiated sending. Do not infer delivery from opening the Share panel. If WhatsApp is absent as a target, verify the manual fallback.
6. Save a sale and restart Windows. Confirm the same invoice, customer, payment and stock. Interrupt the app during checkout on the test profile, recover the pending request and prove no duplicate stock deduction.
7. Create a verified backup on a test external drive; restore to the isolated profile, restart and reconcile all table counts, invoice/payment/refund totals, stock sums and audit history. Test corrupt backup rejection and pre-restore rescue recovery.
8. Install a newer signed build over the test installation; confirm data survives. Uninstall/reinstall and verify the persistent application-data folder is retained. Verify the restore/rollback plan using a known-good pre-upgrade backup.

Record actual outcomes, dates and evidence in STAGE_6_TEST_RESULTS.md. Leave hardware/signing/VM items pending until demonstrated. The user manual provides operating steps and current screenshots; architecture/schema/CLOUD_MIGRATION.md cover development handover and future PostgreSQL reconciliation. No cloud account or paid messaging service is part of this release.
