# Operating manual — version 0.2.0 (Stages 1 and 2)

For 80 mm POS thermal barcode printing, see [thermal printing instructions](THERMAL_PRINTING.md). Select the thermal roll layout in **Barcode labels** for automatic paper height.

## Launch and sign in

Close any running older copy before rebuilding or upgrading. In Command Prompt, use the repository root:

```cmd
cd /d C:\Ammu\EastOfTown\EastOfTownPOS-App
npm.cmd ci
npm.cmd run build
cd backend
mvn.cmd package
cd ..
npm.cmd start
```

The install/build commands are needed after cloning or receiving changes. Normal later launches require only `cd /d ...` and `npm.cmd start`. Java must be installed, or prepare the bundled runtime using the README. `test:api` is a verification command, not a launch requirement.

On first Stage 2 launch, create a local administrator. Choose a username with 3–40 letters/digits/dot/underscore/hyphen, and a password of at least 12 characters (maximum 72 UTF-8 bytes). The app displays a recovery code once after setup; save it securely. Existing Stage 1 products remain available after automatic migration. Sign in with this account; sign out when handing the computer to another operator. Sessions expire after eight hours or backend restart. After repeated failed logins or recovery attempts, wait a minute before trying again.

If the administrator forgets the password, select **Forgot administrator password?** on the sign-in screen. Enter the administrator username, the saved recovery code and a new password of at least 12 characters. The code is single-use and is cleared after a successful reset. After signing in, open **Users → Administrator recovery → Generate new recovery code** to replace a code that may have been exposed or to provision recovery for an existing Stage 1/early Stage 2 account. Store the newly displayed code securely; it is never stored in readable form and cannot be recovered from the database.

Administrators can create billing users in **Users**. Billing users can view catalog/inventory history and print labels. Product/category edits, Excel import, stock adjustments, user creation and label-preference changes require an administrator. These restrictions are checked by the backend as well as the interface.

## Products and categories — Stage 1

1. **Categories** shows the eight default categories and allows administrators to add names.
2. In **Products → Add variant**, enter name, category, unique SKU, size, colour and price. Cost is optional. Enter opening quantity and low-stock threshold.
3. Leave barcode blank to allocate a permanent internal value. Each size/colour has its own SKU/barcode/quantity. To add another variant to one product, select the existing product in the form.
4. Save, then search by product name, SKU or barcode and press Enter. Barcode appears below the SKU. A keyboard scanner can fill this search field; POS scan-to-bill starts in Stage 3.
5. **Edit** changes details/status. Product name/category are shared by sibling variants. Existing barcodes and stock cannot be changed here. Stale edits require refreshing.

## Excel stock import — Stage 2

1. Sign in as administrator and select **Excel import**.
2. **Download template** and choose a save location. Fill the Stock sheet with one variant per row; Instructions and Categories are reference sheets. Required columns: name, category, SKU, size, colour and price. Use text cells for SKU/barcode, plain decimal prices (no currency symbols or separators), whole quantities, and true/false for active. Leave barcode blank if missing. Do not use formulas.
3. **Choose Excel file**, select your .xlsx/.xls workbook, then review the suggested column mappings. Correct mappings as needed. Optionally assign one category to every row. New category names will be created only during confirmed import.
4. Choose the stock policy carefully:

| Policy | Meaning |
|---|---|
| Product details only | Ignore quantity. Existing stock stays unchanged; new variants start at zero. |
| Opening stock for NEW variants only | Use quantity for new variants. Existing quantities are ignored with a warning. |
| Authorized stock delta | Existing variants: +5 adds five, -2 removes two. New variants: non-negative opening quantity. Enter the reason. |

5. **Validate and preview**. Inspect action, stock change, errors and warnings. Use Previous/Next to inspect all pages. A repeated SKU within the file is an error; an existing database SKU means update. Changing its barcode is rejected. Name/category changes affect sibling variants. Blank cost clears a previous cost; blank threshold becomes 3; blank active preserves existing status.
6. **Export errors / warnings** for correction. If there are errors, correct the original workbook and upload again; the export lists problems rather than becoming a ready-to-import stock workbook. Nothing is saved while errors remain.
7. Review warnings and new categories, tick the confirmation checkbox, then **Confirm and save import**. See created/updated/skipped/failed counts and the saved history entry. If inventory changed after preview, generate a fresh preview and check it again.
8. Repeated confirmation of the same batch cannot add stock twice. Uploading the same file again creates a new batch; an intentional delta import will add its quantities again, so inspect history first.

Limits: first sheet only, 10 MB, 5,000 data rows, 64 columns. Blank rows are skipped. Invalid prices, quantities, missing fields, numeric SKU/barcode cells, duplicate identifiers and malformed workbooks are reported. Every confirmed batch is one transaction.

![Import preview](screenshots/stage2-import-preview.png)

![Validation errors](screenshots/stage2-import-errors.png)

## Adjust existing stock

1. Choose **Inventory**, search for the exact SKU, then **Adjust stock**.
2. Enter signed units, effective date and a clear reason. Negative removes units; positive adds units. Future dates and negative resulting balances are rejected.
3. Check the displayed resulting balance and confirmation checkbox, then **Confirm adjustment**. If the balance changed, close the dialog, refresh inventory and try again.
4. **History** shows the most recent 500 movement entries, including opening/imported stock, adjustments, date, reason and operator. Correct mistakes with another documented adjustment; original movements are retained.

![Stock adjustment](screenshots/stage2-stock-adjustment.png)

## Barcode labels

1. Choose **Barcode labels**, search and select active variants, then set copies.
2. Enter label width/height and choose an installed Windows printer. Administrators can **Save preferences**. Defaults are 80 × 40 mm; supported range is 35–110 mm wide and 25–100 mm tall.
3. **Preview labels** shows the PDF-rendered layout. It preserves the stored barcode. If a legacy barcode is too long for the label, increase label width. Maximum 50 variants / 200 copies per job.
4. **Save PDF** for an offline file, or **Print labels**. Use the same media dimensions in the Windows printer driver and print at actual size / 100%. **Test print** submits one marked test label to check calibration.
5. A submitted job means the Windows spooler accepted the request; check paper, alignment and physical output. If the printer is unavailable or a print call fails, save the PDF and resolve the driver/printer problem before retrying. A disconnected printer may accept a queued job; spool acceptance is not proof of a printed label.
6. Scan a physical label into the Products search box and verify the exact SKU/size/colour. Hardware calibration and barcode readability must be checked on your actual devices.

![Label preview](screenshots/stage2-barcode-labels.png)

## Persistence, tests and troubleshooting

Business data is stored in the persistent application data directory described in README.md. Reopening the app keeps records. Do not delete the database to troubleshoot, copy only a live `.db` file without WAL, or enter real sales: backup/restore and billing are not available yet.

For isolated desktop evaluation in Command Prompt:

```cmd
set EOT_TEST_PROFILE=C:\Ammu\EastOfTown\EastOfTownPOS-App\.test-desktop-profile
npm.cmd start
```

That development-only override separates test stock from the normal profile. `npm.cmd run test:api` and `npm.cmd run test:desktop` create separate temporary databases automatically. Automated tests do not require a physical printer and do not print real jobs. Check README.md and STAGE_2_TEST_RESULTS.md for verification commands.

Missing `dist/index.html`: run `npm.cmd run build`. Missing backend JAR: build `backend` with Maven. Missing Electron: run `npm.cmd ci`. Locked JAR: close the running app before rebuilding. Startup timeout/service errors: inspect `backend.log` in the data folder, retain the database and contact the developer with the error. A missing bundled Java runtime is not required for development if JAVA_HOME/PATH Java is installed. Unsigned installers may be blocked by Smart App Control; trusted signing remains a release task.

POS/receipts, customer histories, sales reports, returns and backup/restore remain Stages 3–6. See STAGES.md for the complete status index.
