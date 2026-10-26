# Stage 4 startup correction — 26 October 2026

Version 0.4.0 could stop during startup with `Migration checksum mismatch` when upgrading the actual released Stage 3 database. Restarting alone does not fix this condition.

The previously built 0.3.0 JAR contains V005__checkout_safety.sql with a final CRLF. The committed migration has a final LF. Every SQL statement is otherwise identical. V001–V004 match the installed database. Database quick_check returned ok. Fresh-database CI passed because it never exercised the released V005 byte sequence; this upgrade path now has dedicated coverage.

0.4.1 accepts only the verified version-5 stored/current SHA-256 pair: released `11a29fe62f4b0dc74be40cc87075c75064eaae011f9e3c498a551ff13a0eb710` and committed `5e991ab6b0ddf97a31afbeddb7ff62a25008957ba6e23b989332861c17a8684c`. It preserves the saved checksum and never reruns the already applied migration. All other mismatches remain fatal; errors identify the migration file. No applied SQL file or business record is repaired or deleted. V006 applies normally.

MigrationUpgradeTest uses an isolated Stage 3 database built with the exact released checksum, customer/invoice/item/payment/stock/checkout records. It verifies repeated startup, V006 application, business record preservation, saved checksum preservation, foreign keys and database integrity. Additional tests verify the canonical hash upgrades and arbitrary checksum changes still fail.

The regression run also exposed an existing random-barcode test where a multi-format reader guessed an alternate format. The test now specifically decodes Code 128, as required by the product. Barcode values, sizing and orientation are unchanged.

Optional existing-database validation (Node.js 24, built backend and Java runtime required):

```cmd
node scripts/verify-existing-upgrade.cjs --source "%APPDATA%\east-of-town-pos\data\eastoftown.db"
```

This diagnostic opens the source read-only, uses SQLite's backup API for a consistent original backup plus a separate test copy, upgrades only the test copy through a real backend process, and compares every pre-existing table row/value and saved migration hash. It validates health/foreign keys and retains both copies under a unique temporary folder. It logs record counts, not customer details or passwords. It does not replace the source. Do not reset/delete the production database or manually replace migration hashes to clear this error.

Local verification: all 63 backend tests pass, renderer build passes, API/restart checks and all three Electron suites pass, Windows 0.4.1 directory packaging succeeds, and a separate copy of the affected installed database starts successfully with every pre-existing record/value and saved checksum preserved. No reset or data deletion was necessary. The original Stage 4 fresh-database CI success is retained as historical evidence; the corrected release adds the missing released-database upgrade regression. The PR Windows check is the merge gate.

Close the older application, build backend/frontend if using another checkout, and launch the corrected app with `npm.cmd start`. The existing production data folder is retained. Windows directory packaging and CI remain required before merge.
