# Windows Share attachment timeout — 27 October 2026

The reported error occurred after the receipt had been saved: the native source window opened, but Windows did not raise DataRequested within the bridge's 30-second timeout. That timeout killed the source, removing any opportunity to retry. Local reproduction showed intermittent file requests and a source window behind the foreground application. This differs from a helper blocked from starting by Windows signing policy.

## Changes

- Register the per-window DataTransferManager during Load. Attempt automatic sharing only after the owner is active and a 250 ms message-loop delay has elapsed. Do not reactivate the source over the chooser.
- Populate the request's DataPackage during DataRequested, including the stored PDF/PNG file and PNG bitmap where applicable.
- Provide **Open Windows Share** and **Close sharing** buttons. Keep the native source alive for five minutes so the operator can retry from an active window. Explain busy Share flows and the manual fallback.
- After three seconds without a file request, return an explicit `awaiting-share` result to the POS, releasing its busy control while preserving the native window. This result says the attachment has not been requested. Only `share-opened` plus `attachment-provided` produces attachment confirmation. Neither result confirms delivery. A helper that never becomes ready still fails after 30 seconds.
- Preserve the one-source-at-a-time guard, saved invoice, generated receipt, and seven-day cache policy. No database, barcode, pricing, or payment changes.

## Verification

- Native helper compilation and real PDF/PNG attachment preparation: passed.
- Six isolated Node bridge tests: passed. Includes split retry markers, retained retry process, refusal of a second source, no early attachment confirmation, attachment precedence, early close, policy failure/manual fallback and filename/type validation.
- Stage 6 Electron regression: passed using an isolated profile, including real receipt bytes, customer validation, billing totals, invoice preservation and restart. The chooser is mocked in that suite. A further assertion covers the pending-share message and re-enabled controls.
- Native Windows UI, generated test-only PDF: actual DataRequested observed on initial opening and again using **Open Windows Share**. Windows Share showed `test-only-receipt.pdf` and WhatsApp among available targets. No recipient selected and nothing sent.
- Background `--self-test` did not receive a request within its 15-second lifetime. Background processes are not guaranteed foreground activation; this is why the explicit retry window is retained. Do not count attachment preparation or the POS pending message as chooser success.
- Generated PNG: attachment preparation passed. An initial chooser attempt returned Windows' busy-share-flow error, and retry/fallback controls stayed usable. After the earlier test sharing windows were closed, a fresh attempt raised DataRequested and Windows Share displayed `test-only-receipt.png` with WhatsApp available. The minimal PNG fixture's IDAT checksum was then corrected; the smoke script verifies every chunk checksum and successful pixel inflation before using it. Actual transfer into WhatsApp and delivery remain operator acceptance checks. No message was sent.

## Operator check

Restart the source application to load the changed Electron bridge. From a saved test invoice, select PDF and **Send via Windows Share**. If necessary, switch to the source window and click **Open Windows Share**. Confirm the file preview before choosing WhatsApp and the intended test recipient. Repeat with PNG, cancel and reopen, then close the source before sharing another invoice. Billing must remain saved in every case.

The prior Stage 6 release gates still apply: trusted signing, actual WhatsApp target compatibility/delivery, printers and clean-machine installer acceptance. This patch does not disable Windows protection or automatically send receipts. See USER_MANUAL.md and STAGE_6_TEST_RESULTS.md.
