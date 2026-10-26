# Stage 6 — release candidate 0.6.0

Stage 6 implements the requested customer-phone, live-billing and Windows sharing changes. Release qualification is recorded separately in STAGE_6_TEST_RESULTS.md. A working evaluation package is not a claim of trusted signing, clean-machine installation or physical hardware acceptance.

## Approved changes cross-check

| Request | Implementation |
|---|---|
| Windows Share menu with receipt already attached | Saved invoice → Send via Windows Share. Actual 80 mm PDF or PNG supplied to Windows, then the operator selects an installed target, customer and Send. |
| Country selector when creating/correcting customers | Customers and checkout customer creation use the same country list and national-number entry. India (+91) is the default. |
| Reject incomplete mobile numbers at customer creation | Offline backend libphonenumber metadata validates the selected country and mobile type. India additionally requires exactly 10 digits starting 6–9. |
| Correct existing invalid customer numbers | Customers flags invalid saved contacts; administrator correction retains customer ID, purchases and audit history. New billing with an invalid saved contact is blocked until correction or guest selection. |
| Quantity changes update line totals | New Bill displays unit price separately and a backend-priced line total. Quantities/scans, either discount mode, bill-discount allocation and per-variant tax update the lines and bill totals automatically. |
| Keep vertical barcode labels | No label-size or orientation change in Stage 6. |
| Prior approved Stage 5 additions | Administrator billing-password reset, INR/% discounts and expense-based monthly profit/loss remain implemented and covered by regression tests. |

No paid service, WhatsApp API, automated message sending or delivery tracking was added. The user explicitly requested operator-controlled native sharing.

## Customer phone contract (V008)

All supported ISO phone regions come from Google's offline libphonenumber 9.0.41 metadata. The API country list is `GET /api/customers/countries`. Create/customer-correction requests carry `country` (ISO region such as IN or GB) and `mobile` (national digits, without country code). For old local clients that omit country, IN is the compatibility default. Direct API requests receive the same validation as the forms.

New or corrected records store `mobile` and `phone_e164` as the same canonical international value, for example `+919876543210`, and `phone_region` as IN. A unique partial index prevents duplicate canonical numbers. Services also compare validated legacy formats to avoid adding the same customer with a different spelling. Phone validation checks the numbering plan; it does not verify ownership, reachability or WhatsApp registration.

V008 adds nullable fields and an index without rewriting existing mobile values. Existing IDs, invoice snapshots and purchase history are preserved. Valid legacy 10-digit Indian contacts are interpreted as Indian for display/sharing and remain flagged as unconfirmed until a correction is saved. Other legacy numbers are interpreted only when their complete international form validates; ambiguous/invalid contacts require operator review. No digits or country codes are guessed for incomplete contacts.

Customer API results include `phone_valid`, `phone_confirmed`, `phone_country` and `phone_national`. Invalid saved contacts are visibly flagged in the Customers table. An administrator opens Purchase history → Correct customer details, chooses the country, enters the complete national mobile and saves. The optimistic version guard and audit snapshot remain enforced.

Invoice detail adds `sharePhone` and `sharePhoneNeedsCorrection`, derived from the current customer contact when linked. This is separate from `invoice.customer_phone`: old receipts continue to show the exact phone recorded at sale. Correcting a customer updates future sharing contact selection without rewriting the historical receipt.

## Live billing totals

The renderer requests a debounced quote from the existing billing service after cart, quantity, discount or selected-customer changes. Old responses are ignored; stale totals are cleared and the line shows Updating while a fresh quote is pending. Invalid input/stock/permission errors prevent a stale total from appearing as current. The renderer does not independently duplicate the financial arithmetic.

Unit price remains per item. Each line shows quantity × unit price, combined line/bill discount allocation, rounded tax and line total. Formula: gross − line discount − allocated bill discount + tax. The subtotal, discount, tax and payable summary use that same quote. Review and final checkout still revalidate prices, stock and permissions in the backend. No additional inventory movement is created by a quote.

## Windows receipt sharing

1. Open an invoice from checkout, Invoice history or Customer purchase history.
2. Select PDF or PNG and click **Send via Windows Share**.
3. Confirm sharing. The main process obtains the saved receipt bytes and writes a unique file under `%APPDATA%/east-of-town-pos/shared-receipts`.
4. The native helper opens Windows Share with `StorageItems` containing the actual receipt file. PNG also supplies a Bitmap representation. The receipt uses the existing 80 mm layout.
5. Choose WhatsApp if installed and registered for that file type. Choose the customer, inspect the attachment and press Send in WhatsApp.
6. Close the small receipt-sharing window after finishing. It otherwise expires after five minutes.

Windows controls available targets. A WhatsApp installation may not expose a suitable target, and a target may handle PDF and PNG differently. The share panel cannot preselect the phone number. The operator must verify the recipient. It does not report message delivery. Long receipts that exceed the existing one-page PNG limit must use PDF.

If the target is missing or the helper fails/Windows security blocks it, **Save and open WhatsApp** remains available: supply a validated full international mobile, save the file and attach/send manually. The fallback message no longer asserts that a file has already been attached. No protection is disabled and no browser automation clicks Send.

Sharing files remain locally available for handoff and are removed once older than seven days at the next app startup/share. Only uniquely named files created in this dedicated folder are cleaned; user-saved Downloads files are not removed. The native helper receives only the generated file path. React receives no Node access, executable path or arbitrary-file sharing capability. Backend authentication and saved-invoice lookup remain required. Audit actions record preparation, never SENT/delivery. The success notice waits for both panel opening and Windows requesting the actual attachment. Closing/failing before the file request or a 30-second timeout shows an error and manual fallback rather than claiming the file was included.

The original C# helper compiles with Windows' .NET Framework compiler and OS WinRT metadata; no separate SDK or paid service is required at runtime. Main-process IPC is strictly restricted to known invoice IDs and PDF/PNG. A second simultaneous sharing window is rejected, and helper processes are stopped when the POS closes. See RELEASE.md for build/signing details.

Official references: [Microsoft desktop Windows sharing](https://learn.microsoft.com/en-us/windows/apps/develop/windows-integration/integrate-sharesheet-send), [per-window DataTransferManager interop](https://learn.microsoft.com/en-us/windows/apps/develop/ui/display-ui-objects), [Google libphonenumber](https://github.com/google/libphonenumber).

## Release safety

Source and packaged launches now explicitly use the existing `%APPDATA%/east-of-town-pos` profile so upgrades preserve the same business data. An explicit `--acceptance-profile=<absolute eot-acceptance-UUID folder>` is supported for isolated packaged acceptance. The normal app never selects it automatically. Tests retain their databases separately.

The packaged app includes the Java runtime, backend JAR and native share helper. Uninstall is configured to preserve application data. Production signing is a separate, fail-closed build/check; no trusted certificate was invented or purchased. Back up before upgrade. Do not downgrade a database upgraded to V008 in place; restore a verified pre-upgrade backup using a compatible release.
