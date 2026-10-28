# Stage 6 verification — 26 October 2026

Version 0.6.0. Isolated temporary databases/profiles only. The existing shop database was opened read-only to produce retained test copies; it was not migrated or restored by the tests.

## Evidence

| Check | Result |
|---|---|
| Java regression/integration | PASS: 91 tests, zero failures/errors (16 billing, 7 catalog, 18 controls, 3 migration upgrade, 10 Stage 6, 16 reports, 21 imports/stock/labels). |
| TypeScript/Vite | PASS. |
| REST/restart | PASS: original catalog/checkout assertions plus forced backend termination with in-flight checkout, restart, same-key retry, healthy database and exactly one sale movement. |
| Earlier desktop stages | PASS: Stage 2 import/stock/labels; Stage 3 billing/payment/print failure; Stage 4 customers/reports; Stage 5 controls/restore. |
| Stage 6 desktop | PASS: country/default, nine-digit form/API rejection, E.164 persistence, correction, live quantity/INR-price/%-discount/tax totals, insufficient cash, PDF/PNG payloads, invalid sharing phone, preserved invoice snapshot/current contact and restart. Native chooser mocked in this suite; no messages sent. |
| Native helper | Initial source executable PASS: Windows Share opened and raised DataRequested with the file. The freshly rebuilt unsigned helper was later blocked by this PC's Code Integrity policy; this blocks final local native-sharing acceptance. Target selection/sending was not performed. |
| Existing database upgrade | PASS: every prior row/column value and saved migration checksum preserved in the upgraded copy; V008 applied; integrity/FKs/health passed. |
| npm registry audit | PASS: zero reported advisories in locked production/development graph. This is not a complete Java/Windows security certification. |
| Directory package and bundled Java | Initial package PASS: 0.6.0 executable, bundled Java without system Java on PATH, fresh profile, renderer offline simulation, checkout/stock/customer/backup and restart. A subsequent refreshed unsigned executable was blocked by local Code Integrity; reliable local distribution acceptance remains gated on signing. |
| NSIS installer build | BLOCKED: generated uninstaller bootstrap could not execute (spawn UNKNOWN). Code Integrity events 3033/3077 confirm signing-policy rejection. The incomplete .exe was preserved as East of Town Setup 0.6.0.failed and must not be installed. |
| Release hash/signature inspection | Manifests recorded actual NotSigned state. A blocked helper caused a strict failure; one refreshed helper passed attachment preparation, while subsequent rebuilds were blocked again. Passing an attachment probe does not prove chooser delivery, trusted signing or production readiness. |
| Windows CI | PASS: run 21 (40355709379), implementation commit 36f7cff. Backend, native attachment preparation, REST/forced interruption, all five desktop suites, verified runtime download, directory packaging, packaged restart acceptance, signature inspection and evidence upload all succeeded. |

Upgrade evidence retained at `C:/Users/lokes/AppData/Local/Temp/eot-existing-upgrade-Sqzy6o/before-upgrade.db` and `eastoftown.db` in that directory. The originals contained five invoices; all original counts and full row fingerprints reconciled. New nullable phone fields do not rewrite legacy values.

REST crash-test database: `C:/Users/lokes/AppData/Local/Temp/eastoftown-api-test-hF3owt`. Native UI test used a local test image and emitted `share-opened` and `attachment-provided`; no recipient was chosen and no WhatsApp message was sent.

Stage 6 desktop final profile: `C:/Users/lokes/AppData/Local/Temp/eot-desktop-stage6-Auh4xJ`. It also verifies that a simulated Windows policy/helper failure leaves the invoice intact and exposes fallback controls. Screenshots are in docs/screenshots/stage6-*.png and were inspected for customer correction, line totals and sharing controls.

Packaged acceptance profile: `C:/Users/lokes/AppData/Local/Temp/eot-acceptance-4a297eab-6faf-416d-ae3b-8e703d3e0bb3`. Package component hashes and actual signature status are retained in release/release-manifest.json. Windows Code Integrity events at 21:26–21:27 (3033/3077) identify ReceiptShare.exe and the generated installer bootstrap as violating this PC's signing policy. No policy was disabled or changed. A legitimate trusted signing setup and acceptance under the device's policy are required.

CI evidence: [Windows run 21](https://github.com/Sahithi502/EastOfTownPOS-App/actions/runs/40355709379), job 121687476924, all steps successful. The stage6-verification artifact includes JUnit XML, Stage 6 screenshots, runtime provenance and package hashes/signature inspection. CI's Windows runner permitted the evaluation helper; this does not override the local PC's policy, prove trusted production signing, or complete shop-device acceptance. Final integration/merge status is recorded on [PR 10](https://github.com/Sahithi502/EastOfTownPOS-App/pull/10).

Later local checks confirmed policy events 3033/3077 at 21:45 for the refreshed app executable and 21:48 for the recompiled helper. One UI retest opened without requesting the file before its short timeout; the bridge now requires the actual attachment request before reporting success, and native UI verification uses a valid one-page PDF with a longer startup allowance. Four isolated bridge tests cover delayed file requests, closing without a request, policy failure/manual fallback and untrusted filenames/types. No protection setting was modified and no target/customer was selected or messaged.

## Failures found and corrected

The first SDK-free native compilation needed .NET's WinRT token reference and direct async-status handling instead of SDK-projected AsTask. The resulting helper was actually executed and verified. The Stage 6 test initially assumed require was global in Playwright's main-process context; its hook now uses the main module's require. Cash tendered's accessible label previously included changing change-due text; an explicit accessible label was added. International-number validation previously surfaced Select a phone country for malformed numbers; it now returns an appropriate international-mobile validation error. The signature-inspection child process inherited a PowerShell 7 module path incompatible with Windows PowerShell; it now explicitly loads the Windows security module and fails on inspection errors. No validation was weakened to make the tests pass.

## Mandatory acceptance still requiring operator/release environment

| Item | Status / reason |
|---|---|
| Trusted application/helper/installer signing | Pending organization certificate/approved signing setup. No CurrentUser code-signing certificate was available. A fail-closed signing/check workflow is provided. |
| Clean-machine NSIS installation/uninstall/upgrade | Pending isolated Windows VM or test PC with protection enabled. A packaged executable smoke test is separate evidence. |
| Windows reboot | Pending operator test; application/process restart was tested. |
| Physically disconnected network | Pending operator test; local-only operation and packaged renderer offline simulation are separate evidence. |
| 58/80 mm receipt, variable-height roll and vertical labels | Pending selected printer model/driver, actual paper and calibration. PDF generation, barcode decoding and print-error paths are automated. |
| USB scanner hardware | Pending selected scanner; keyboard/barcode scanning workflow is automated. |
| Actual WhatsApp attachment and sending | Pending installed WhatsApp share-target compatibility and operator-selected test recipient. No external message was sent by tests. |
| External-drive backups and shop restore sign-off | Pending selected drive/test installation; isolated validation/restoration/rescue and reconciliation are automated. |

Stage 6 software changes may be merged after successful CI while these release gates remain explicitly pending. Do not describe this candidate as a fully signed production release or all hardware acceptance as complete. RELEASE.md provides the exact operating checks and release procedure.

## Operator regression cases

Receipt design follow-up, 28 October: [RECEIPT_DESIGN.md](RECEIPT_DESIGN.md) documents the screenshot-based layout across preview, printing, PDF and shared PNG. The backend suite increased to 97 passing tests; six cover receipt layout and the PNG endpoint is checked against the PDF pixel for pixel. Both widths, long/large text, returns, split payments and zero-total/guest cases were verified. Billing and Stage 6 desktop tests passed in isolated profiles. Physical printer calibration remains an operator acceptance item.

The 27 October Windows Share timeout follow-up is documented in [WINDOWS_SHARE_FIX.md](WINDOWS_SHARE_FIX.md): six bridge tests and the expanded Stage 6 desktop regression pass; generated PDF and PNG files were actually requested by Windows Share. The native retry button was exercised with PDF. WhatsApp was visible as a target; no target/recipient was selected and nothing was sent. Busy-flow behavior and background activation limits are recorded separately from successful attachment preparation.

The 28 October session-lifecycle follow-up in the same document supersedes the old rule that refused every second sharing request. Thirteen bridge tests, the renderer build and isolated Stage 6 desktop regression passed. The actual managed native test confirmed PDF attachment, reuse of the PDF source on repeat, orderly replacement, PNG attachment and parent-pipe shutdown. The repeat returned an explicit pending result when Windows refused foreground activation; it did not claim another attachment or delivery. No message was sent.

1. Customers → Add customer: India (+91), nine digits must not save; ten digits beginning 6–9 normalize to +91. Reject letters, spaces, extra digits and wrong country mobile plans. Test another country with a genuine test mobile format.
2. Create customer from New Bill and verify identical validation. Attempt a duplicate saved number and confirm no new record. Correct an old invalid contact as administrator, retaining the same purchase history and an audit entry.
3. New Bill: price ₹100, quantity 2, 10% line discount, 5% bill discount, 5% tax → line total ₹179.55, unit price ₹100. Verify live summary and payment review agree. Repeat scans, lower quantity, remove a line and test multiple lines.
4. Historical invoice: correct the linked customer's phone; original receipt keeps its old value, sharing field uses the corrected current contact. A saved invalid number is flagged and must not open the manual WhatsApp route without correction.
5. Receipt → PDF/PNG → Send via Windows Share. Confirm attached file preview if WhatsApp appears, select the customer and send yourself. Cancel without sending and verify invoice/stock remain unchanged. Try missing target/helper, manual fallback and a very long receipt requiring PDF.
6. Recheck administrator password reset/session revocation, account role restrictions, partial returns/refunds, expenses/monthly net profit/loss, backup/restore and original vertical barcode behavior from Stage 5.
