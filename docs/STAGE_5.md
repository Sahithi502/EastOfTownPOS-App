# Stage 5 — business controls, recovery and profit/loss (0.5.0)

All four additions approved by the owner are in this stage: administrator recovery/reset of billing-user passwords, WhatsApp receipt preparation, percentage/fixed-INR discounts, and operating expenses with monthly profit/loss. Also includes returns/cancellations/refunds, user access controls, verified backups/restore and portable database export. Barcode values, sizing and vertical orientation are unchanged.

## Billing discounts

Each line and the whole bill have an INR/% selector. Percentage accepts up to two decimal places (integer basis points, 0–100%). The backend calculates line percentages on quantity × price; bill percentages on the amount after line discounts. Round HALF_UP to one paise; distribute the bill discount by the existing largest-remainder policy. Existing fixed-INR clients remain supported. All discount modes share the configured cashier authorization cap. Save the original cart intent in invoices.discount_input_json and the calculated paise in historical item snapshots. No currency calculations use floating-point arithmetic in Java.

## Reversal ledger

Administrator only: choose an original invoice, items, quantities, restock choice and reason. Review refund, then record the actual cash/UPI/card refund. Backend supports split refunds with exact total validation. The screen records one method per reversal. A cancellation must include every original item in full, restock all units and have no prior return. Otherwise use partial returns.

The original invoice/items/payments and completed status remain immutable. returns, return_items and refunds describe the reversal; restocked units receive a RETURN/CANCELLATION movement referencing their return-item ID. Non-restocked goods receive no positive stock movement. A shared writer transaction enforces cumulative quantity limits and commits ledger/refunds/stock/audit together. UUID request key + payload hash + actor makes retries durable and prevents duplicate refunds or restocking. Pending desktop requests survive restart; retry or check status before making another reversal. Concurrent submissions and injected refund failures are tested.

Refund rounding uses cumulative portions of the original line total and tax, with each new return receiving the difference between cumulative allocations before/after its quantity. This ensures repeated partial returns reconcile exactly to the original total, including fully discounted lines. Original receipts remain available; reprints annotate recorded reversals/refunds below the original sale totals. Invoice status alone is not the reversal source of truth.

## Reports and customer totals

Return events reduce revenue and refund events reduce collections on their own recording dates in Asia/Kolkata. Original bill counts are retained; they count sale events, even when later returned. Lifetime customer purchase amounts subtract recorded refunds; original invoice history remains. Date-scoped customer/product/history reports include signed reversal activity. Dashboard gross sales/discounts describe original sales, with returns shown separately; net revenue/tax/collections include reversals. Exported daily/monthly/yearly gross/discount columns are signed after reversal activity, as explained in their report note.

Reports add Returns and cancellations, Operating expenses and Monthly net profit / loss. Expenses have category, amount in paise, effective date, method, description and actor. Administrators can create categories. Expenses are immutable except an audited void with reason/time/actor; voided records remain visible and are excluded from profit. Correction means void + replacement. Stock purchases are not operating expenses: recording them again would double-count costs already included as cost of sales.

Monthly net profit/loss = net revenue excluding tax − historical cost of sales − active operating expenses. Restocked returns reverse their original cost; non-restocked returns retain cost. Positive is profit, negative is loss. Missing sale costs produce a blank profit result and explicit coverage information rather than an invented full profit. Select a whole month with custom dates for a full-month result; This month is month-to-date. This is management reporting based on recorded costs/expenses, excluding unrecorded costs, depreciation and income-tax accounting.

## Account administration

Business controls → User access lists local users. Administrators reset billing-user passwords (same BCrypt policy as account creation: minimum 12 characters, maximum 72 UTF-8 bytes). Existing sessions for that user are revoked. No plaintext password is logged or audited. The operator communicates the new password directly to the user. Administrator accounts retain their existing one-time recovery-code workflow.

Administrators can activate/deactivate users and change roles. These actions revoke affected sessions and write audit events. You cannot disable/demote your own administrator account or remove the last active administrator. Authorization is enforced in controllers and current-session checks, independent of sidebar visibility. No user deletion removes historical actor references.

## WhatsApp receipt preparation

On any saved receipt, enter the full international recipient number (digits including country code), choose PDF or PNG, then Prepare WhatsApp receipt. A native confirmation asks the operator to verify customer agreement and recipient. The app saves an 80 mm receipt file and opens the recipient chat with a draft message. Attach the saved file, review and send in WhatsApp. Cancellation never sends a message. The app never reports delivery or successful sending.

PNG is rendered from the same PDF layout at 150 DPI. Long multi-page receipts require PDF. WhatsApp needs internet and the operator's WhatsApp login; all billing, printing and file preparation stay local. No paid API, hosted receipt, WhatsApp credentials, automated send or external purchase-data upload is introduced. Share preparation writes a RECEIPT_SHARE_PREPARED audit event, which does not mean sent. The native bridge permits only a validated invoice ID, PDF/PNG format and a fixed wa.me destination generated from validated metadata. See the official [click-to-chat documentation](https://faq.whatsapp.com/5913398998672934) and [link format](https://faq.whatsapp.com/425247423114725).

## Backups and restore

Business controls → Backup and restore displays database location, schedule, last success/error and pending restore state. Choose an absolute folder (including an external drive), interval hours (0 disables; max 720) and retention (2–100 verified copies). Scheduler checks once per minute while the app is open and catches up when the app is next open. This is not a Windows background service.

SQLite-specific operations are isolated in BackupFiles. A separate read-only connection uses VACUUM INTO for a consistent snapshot including committed WAL records. Validate integrity_check, foreign_key_check and all known migration checksums before publishing a unique backup. Store SHA-256 and table counts in its JSON manifest. Retention deletes only older, verified files with this app's generated backup naming pattern, after a new verified copy exists. Invalid/unrelated files and pre-restore rescue files are not pruned. Never replace the only good copy.

Restore procedure:

1. Back up current records and securely retain the backup. Choose the older .db using Select backup and prepare restore.
2. Confirm that all current records will be replaced. The service creates another backup, snapshots and validates the selected source into a staged database, and writes an atomic pending marker with its SHA-256.
3. Business writes are blocked while pending. Cancel pending restore if you changed your mind.
4. Close and restart the app. Before opening the datasource, revalidate staged hash/schema/integrity/FKs; preserve a unique rescue copy of the current database, checkpoint its WAL and atomically replace it. Retain restore-completed.txt and rescue-*.db in the data folder.
5. Sign in using credentials from the restored database. Reconcile invoice counts/totals, refunds, expenses, customer balances and stock. Sessions never survive the restart. Store rescue copies securely; they are not automatically deleted.

If validation, a busy database or atomic replacement fails, startup stops with diagnostic logs; never delete eastoftown.db or edit migration hashes to force startup. Keep the original/staged/rescue files and investigate. Restoration is tested in isolated folders and through an actual Electron restart, never by replacing showroom data during automated tests.

Database JSON export creates a consistent snapshot and exports all table rows/columns under eastoftown-portable-v1. It preserves IDs, barcodes, ledger rows, invoice snapshots, account password hashes and audit records. Treat backups and exports as sensitive business data. It is a migration input, not an automatic PostgreSQL deployment. See CLOUD_MIGRATION.md for reconciliation and loading guidance.

## API additions

GET /auth/users; POST /auth/users/{id}/reset and /state. GET /controls/returns/{invoice}, /return/{id}, /return-status/{requestKey}; POST /controls/returns/preview and /returns. GET/POST /controls/expenses and /expense-categories; POST /controls/expenses/{id}/void. GET/POST /backups; POST /backups/settings, /restore and /cancel-restore; GET /backups/export. New report types expenses, returns and net-profit reuse existing Excel/PDF export. Receipt /share prepares metadata; /png exports a single-page image. The Electron bridge restricts routes and native file dialogs.

No new third-party dependencies or licenses are added. Final installer signing, clean-machine/offline/reboot and physical printing/scanning acceptance remain Stage 6.
