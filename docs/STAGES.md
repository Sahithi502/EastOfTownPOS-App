# Stage documentation index

| Stage | Status | Documents / evidence |
|---|---|---|
| 1. Catalog and persistence | Delivered; historical installer acceptance limitations retained | ARCHITECTURE.md, TEST_RESULTS.md, original Git commit `2a6fffe` |
| 2. Excel, stock, labels | Implemented in 0.2.0; hardware calibration pending | STAGE_2.md, STAGE_2_TEST_RESULTS.md, USER_MANUAL.md, screenshots/ |
| 3. POS and receipts | Implemented in 0.3.0; hardware acceptance pending | STAGE_3.md, STAGE_3_TEST_RESULTS.md, SCHEMA.md, USER_MANUAL.md, screenshots/ |
| 4. Customers and reports | Implemented in 0.4.0 | STAGE_4.md, STAGE_4_TEST_RESULTS.md, SCHEMA.md, USER_MANUAL.md; reconciliation and isolated test evidence |
| 5. Returns, security, backup | Planned; local login pulled forward to protect Stage 2 writes | ROADMAP.md; account lifecycle and tested restore remain pending |
| 6. Release and handover | Planned | ROADMAP.md; signing, offline, reboot, printer/scanner and clean-machine gates |

Each future stage must update this index, architecture/schema notes, operating manual, dependency notices and its own dated test-evidence document. Record actual failures/limitations; do not mark planned work as delivered. Business test data must stay in isolated databases.

Git workflow: use one feature branch per stage, commit source/tests/docs together, push it, open a pull request, attach concrete test results, then merge only after required checks pass. Preserve completed migration files, stable identifiers, permanent barcodes and movement history.
