# Delivery milestones

## Stage 1 — current
Architecture, checksummed migration, persistent Windows shell, category/product/variant management, opening stock ledger and catalog integration tests. Final milestone verification is recorded in TEST_RESULTS.md.

## Stage 2
POI template and mapping/preview; row errors and correction export; atomic confirmed imports with explicit stock policy; adjustment authorization; Code 128 generation and calibrated label preview/printing. Test duplicate rows, collision rollback, malformed files, stock policies and restart persistence.

## Stage 3
POS scan/keyboard workflow; BigDecimal discount/tax policy; split payments; unique checkout keys; transactional stock guard; invoice sequence/snapshots; thermal PDF and print/reprint error handling. Test simultaneous and repeated checkout, insufficient stock, payment reconciliation and interrupted transactions.

## Stage 4
Customer matching/corrections and history; date/timezone-aware dashboard and reports using committed records; POI/PDF report export; distinguish collections, revenue and estimated profit. Reconcile every aggregate to fixtures in isolated test databases.

## Stage 5
BCrypt local users and permission checks; partial returns/refunds/cancellation ledger; verified consistent backups, retention, test restore and export utility. Test unauthorized requests and repeated/remainder returns; validate restored counts and money totals.

## Stage 6
Full acceptance scenarios from project brief, signed Windows distribution with bundled runtime, clean-machine installation/restart/offline tests, real scanner/58 mm/80 mm/label printer checks, failure handling and final manual/screenshots. Hardware behavior must be demonstrated on the selected devices. Production release is gated on all essential features and acceptance evidence; a catalog-only build is not a completed POS.
