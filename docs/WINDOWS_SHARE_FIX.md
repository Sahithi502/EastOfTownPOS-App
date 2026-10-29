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

## Follow-up: repeated-click session lifecycle — 28 October 2026

The first fix retained a source for retry but still rejected every later POS click while any source process existed. The operator therefore saw **Close the existing receipt sharing window before opening another** even when retrying the same receipt. The previous six-test suite explicitly expected that refusal; it did not verify the desired repeated-click workflow.

The bridge now serializes requests, reuses a live source for the same invoice/format, and closes a previous source before switching invoice/format. Managed helpers accept only `open` and `close` through a private parent-owned stdin pipe. Closing the pipe also closes the helper. Replacement waits for process exit, uses a bounded termination fallback, and refuses overlap if the previous process cannot be stopped. Late exit events cannot clear a newer session. No arbitrary file/recipient command is accepted.

The helper registers cancellation and target-completion handlers on the request package and closes when Windows signals either event. It can bring an existing owned Share popup forward; a rejected foreground request is reported honestly and does not claim focus or another file request. Every retry waits for fresh file-request signals before confirming attachment. Neither Windows target completion nor attachment preparation confirms WhatsApp delivery. The receipt view clears old sharing notices at the start of a new action.

Verification:

- Thirteen bridge tests pass: readiness, actual-request confirmation, repeat/resume, existing modal, invoice/format replacement, cancellation, concurrent clicks, stale exit, policy and pipe failure, input validation, delayed close, stuck close and fresh retry signals.
- Renderer build and Stage 6 Electron regression passed with isolated data. That suite mocks the chooser and verifies real receipt bytes and invoice persistence.
- Real native `share-session-test.cjs --show` passed on 28 October: managed readiness/close/parent EOF; first PDF returned `attachment-provided`; the second request reused that helper and returned `awaiting-share` because Windows rejected foreground activation of its already-open panel; switching to PNG waited for the old source to exit, then returned `attachment-provided`. Exactly two helpers were spawned for three requests. No target selected and nothing sent.
- CI now tests real managed readiness, graceful close and parent EOF in addition to both format probes. Run `node scripts/share-session-test.cjs` without `--show` to test lifecycle without opening the chooser. `--show` opens the native chooser with generated receipts; it never chooses a target or presses Send.

Operator regression: share a saved test invoice; click again for the same invoice; cancel and retry; switch PDF to PNG; share a different invoice. The old close-existing-window error must not appear during these ordinary operations. Verify the attachment in WhatsApp before sending to an approved test recipient. Existing shop data and invoice/stock records are unaffected by native sharing-session changes.

Reference: [Microsoft's Windows Share source integration](https://learn.microsoft.com/en-us/windows/apps/develop/windows-integration/integrate-sharesheet-send) describes per-window transfer managers, file packages, and source cancellation/completion events. The demo in the chat **Bill Delivery Options** was not accessible as code through thread retrieval (it returned content references); its implementation and host environment have not been verified or assumed equivalent to Electron.

## Receiving-app error attachment follow-up

The later report of a 21-byte `0.text/plain` attachment is not resolved by the earlier window/session tests. [WHATSAPP_HANDOFF_CANDIDATE.md](WHATSAPP_HANDOFF_CANDIDATE.md) documents a streamed-file/lifetime candidate, source validation, technical diagnostics and explicit receiving-app acceptance. It supersedes immediate helper shutdown on completion/cancellation in the candidate branch. Local unsigned-helper execution is blocked by Code Integrity; native and WhatsApp target verification must not be claimed from mocked tests.
