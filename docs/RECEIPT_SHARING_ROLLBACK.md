# Restore the previous receipt-sharing implementation — 29 October 2026

The operator requested restoration of the sharing behavior that preceded the streamed-file candidate. Candidate commits bf8777a and 6184ae2 were reverted without resetting history. The application source, sharing helper, package scripts and existing operating documentation are identical to main commit 6f7432e. The receipt reference design remains in place. Customer records, invoices, the database, barcode orientation and the operator's unrelated CSS edits were not changed.

The candidate was not merged into main before this request. At the end of this branch, the only net change relative to that main commit is this rollback record. Earlier candidate test results remain visible in the pull-request history; they are not proof of WhatsApp delivery.

## Local verification

- Restored bridge suite: 13 tests passed, including repeat clicks, session replacement, cancellation, failure and concurrency handling.
- Main-process and sharing-bridge JavaScript syntax checks passed.
- The restored C# helper compiled normally to the original native/bin/ReceiptShare.exe location.
- Native PDF and PNG attachment preparation passed.
- Real managed-helper startup, graceful close and parent-pipe EOF passed without opening the Share chooser.
- A test-only Electron main process successfully started the restored helper and prepared both PDF and PNG attachments. No chooser opened, recipient was selected or message was sent.
- No Windows protection, signing policy, certificate store or trust setting was changed. The restored helper was permitted in these checks; that does not establish trust for future rebuilt binaries or production distribution.

The ignored helper executable has been rebuilt locally from the restored source. Git stores the source, not the executable. A different checkout needs its usual native helper build.

## Operator check

Close and reopen East of Town so the main process loads the restored sharing bridge. From a saved invoice, choose Send via Windows Share, select WhatsApp, and confirm the actual PDF/PNG filename and preview before sending to the intended customer. If the old 0.text/plain attachment appears, cancel that attachment. The previous implementation's intermittent receiving-app error remains unresolved; use Save receipt PDF and manually attach the file as needed.

The tests above verify helper startup and source attachment preparation. They do not verify an actual transfer to WhatsApp or message delivery. Use a TEST ONLY receipt and your own test chat for further acceptance. The shop's saved invoice and payment records do not depend on sharing success.
