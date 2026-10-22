# Stage 1 verification — 22 October 2026

## Passed

- `mvn -B package`: successful build; 7 tests, 0 failures, 0 errors. Checks category/migration idempotence, persistent variant/opening ledger, duplicate-SKU rollback, barcode and stock preservation on edit, stale edit rejection, exact money conversion, literal wildcard search and foreign-key rollback.
- `npm.cmd run build`: TypeScript check and Vite production bundle successful.
- `npm.cmd run test:api`: REST requests against bundled Temurin Java 21 and a separate temporary SQLite database. Checks 401 for missing token; 400 for blank name, excess decimal places and negative opening quantity; 409 for duplicate SKU; preserved record count after duplicate; exact prices; stale edit rejection; original barcode and stock after stopping and restarting Java; SQLite quick_check=ok.
- Desktop launch: Electron started Java, loaded the compiled renderer, and showed the empty catalog/inventory without errors. Computer Use visually inspected catalog and inventory. No business records were added to production. Full UI entry/edit automation was not completed; operator input was detected during inspection.
- `npm.cmd run package:win`: NSIS x64 installer built with Java runtime, backend JAR and renderer included. Output `release/East of Town Setup 0.1.0.exe` (176,681,175 bytes). Portable directory `release/win-unpacked/` also produced.
- Runtime archive verified against SHA-256 from official Adoptium asset metadata; source manifest retained.

## Pending

Installer Authenticode status: **NotSigned**. Clean-machine installation/uninstallation, packaged application launch, computer reboot and hardware printing/scanning acceptance have not been completed. Verified desktop launch used the development shell and isolated `.test-desktop-profile` database; REST restart tests used a unique temporary database. Process restart persistence is verified; Windows reboot is not.

Eight moderate npm development packaging dependency advisories remain; see DEPENDENCIES.md.

Stages 2–6 and billing/financial/return tests remain pending. This milestone is for catalog evaluation, not live billing. Checkout, printing, returns, customer reporting and backup/restore are not implemented.
