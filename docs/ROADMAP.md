# Delivery milestones

## Stage 1 — delivered
Architecture, checksummed migration, persistent Windows shell, category/product/variant management, opening stock ledger and catalog tests. Historical verification is retained in TEST_RESULTS.md. The original unsigned installer was blocked by Smart App Control; trusted signing and clean-machine acceptance remain release gates.

## Stage 2 — implemented, hardware acceptance pending
POI template/upload/mapping/preview, row errors and correction export, atomic confirmed imports, explicit stock policies, import history, authorized stock adjustments/history, local owner/billing authentication, permanent Code 128 values, PDF-rendered label previews/PDF export and Windows printer/test-job submission. Tests cover malformed files, duplicates, a late database failure with full rollback, repeat confirmation, stale previews, overdraw prevention, permissions, barcode decoding and restart persistence. Actual printer calibration/scanner checks require the selected hardware. See STAGE_2.md and STAGE_2_TEST_RESULTS.md for evidence and operating limits.

## Stage 3 — implemented in 0.3.0; hardware acceptance pending
POS scan/keyboard workflow; BigDecimal discount/tax policy; split payments; unique checkout keys; transactional stock guard; invoice sequence/snapshots; thermal PDF and print/reprint error handling. Verified simultaneous/repeated checkout, insufficient stock, payment reconciliation, transaction rollback and interrupted confirmation recovery. Basic customer matching/creation is included for billing; full customer history remains Stage 4. See STAGE_3.md and STAGE_3_TEST_RESULTS.md.

## Stage 4 — planned
Customer matching/corrections and history; date/timezone-aware dashboard and reports using committed records; POI/PDF report export; distinguish collections, revenue and estimated profit. Reconcile every aggregate to fixtures in isolated test databases.

## Stage 5 — planned
Extend Stage 2 BCrypt users/permissions with account lifecycle/recovery and finer permissions; partial returns/refunds/cancellation ledger; verified consistent backups, retention, test restore and export utility. Test repeated/remainder returns, account changes and restored counts/money totals. The requested administrator reset of billing-user passwords and WhatsApp purchase receipts remain deferred until explicit approval in this stage.

## Stage 6 — planned
Full acceptance scenarios from project brief, signed Windows distribution with bundled runtime, clean-machine installation/restart/offline tests, real scanner/58 mm/80 mm/label printer checks, failure handling and final manual/screenshots. Hardware behavior must be demonstrated on the selected devices. Production release is gated on all essential features and acceptance evidence; a catalog-only build is not a completed POS.
