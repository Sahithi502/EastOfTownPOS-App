# Windows Share / WhatsApp file handoff candidate — 28 October 2026

Status: **candidate, not a confirmed fix** for the operator's first-attempt `0.text/plain` attachment. The 21-byte file reportedly contains `Internal Server Error`; a later attempt produces the correct PDF. The regular WhatsApp app entry under Share using was used. Five recent cached PDFs parsed successfully and the current backend log showed no corresponding receipt-generation failure. This narrows the investigation but does not identify the exact failing component.

## Candidate changes

- Validate receipt MIME and byte framing before saving/sharing; reject empty, text/HTML/JSON, mismatched, truncated and corrupted PNG responses. PDF framing validation is not a substitute for a full PDF parser. The Java export already has PDFBox-based tests.
- Flush the saved receipt before launching the helper. Snapshot its completed bytes into an OS streamed storage file, advertise its file type and read the entire Windows storage stream back. Compare size and SHA-256 before opening the chooser. This avoids relying on the receiving app reading an app-data path directly.
- Retain the stream provider, request packages and storage-item references. Completion/cancellation notifications retain the source for five minutes rather than immediately ending its COM apartment. Explicit close, parent exit and receipt replacement remain available. No automatic retry that could send duplicate messages is introduced.
- Use a DataRequested deferral with a failure message and guaranteed completion. Reject native read-back mismatches. The POS asks the operator to confirm the PDF/PNG preview in WhatsApp before sending; a source event never confirms recipient-side attachment or delivery.
- Write bounded technical diagnostics to the application-data `shared-receipts/sharing.log`, retaining one previous copy after 256 KiB. Fields are timestamp, random session ID, event, format and byte length. No customer, phone, invoice number, filename, payload, password or authentication token is logged.

The streaming/lifetime pattern is informed by [Chromium's Windows sharing implementation](https://chromium.googlesource.com/chromium/src/+/012bf17869a5910cc3524d9a623cbbcfc2151d48/chrome/browser/webshare/win/share_operation.cc). This is a reference revision, not a claim about the current WhatsApp implementation. Windows source/target responsibilities are described in [Microsoft's source integration](https://learn.microsoft.com/en-us/windows/apps/develop/windows-integration/integrate-sharesheet-send) and [target data-retrieval lifecycle](https://learn.microsoft.com/en-us/windows/apps/develop/windows-integration/receive-shared-data).

## Evidence and limitation

Local: native compilation, four payload tests, sixteen bridge tests, and isolated Stage 6 desktop regression passed. The desktop suite mocks the native chooser; no recipient was selected and no messages were sent. The native probe now requires a fully materialized Windows storage stream whose complete hash matches the saved receipt.

Local native execution was blocked before startup. Code Integrity events 3033/3077 identify ReceiptShare.exe as not meeting Enterprise signing requirements / code-integrity policy. No usable current-user code-signing certificate was present. This is a separate release/test blocker from the earlier successful helper launches that sometimes reached WhatsApp with an error attachment. No protection was disabled or bypassed. A trusted signed build or an approved Windows test environment is required for actual WhatsApp verification.

This branch should remain a draft until native verification and the first-attempt receiving-app check succeed. Passing source read-back or CI alone does not prove that WhatsApp received the intended file.

## Acceptance

1. Use a generated TEST ONLY receipt and the operator's own test chat; verify consent and recipient before any send.
2. From a cold WhatsApp start, select the regular WhatsApp app in Windows Share. Confirm the real `.pdf`/`.png` name, size and preview before Send. Never send an error text attachment as a receipt.
3. Repeat with warm WhatsApp, cancellations, the same invoice, another invoice and PDF/PNG. Check the received file opens and matches the invoice; repeated attempts must not be needed for normal use.
4. Compare sharing the same saved PDF through File Explorer's built-in Share action. If it also produces the error, investigate Windows/WhatsApp integration separately from this helper. Save/open WhatsApp with manual attachment remains the fallback.
5. Correlate a failed attempt's time with sharing.log: stream materialization, hash verification, target selection, retained source and eventual exit. An observed receiving-app error after verified bytes still needs target-side investigation.

No paid WhatsApp API, cloud service, customer messaging automation, billing change or database migration is introduced.
