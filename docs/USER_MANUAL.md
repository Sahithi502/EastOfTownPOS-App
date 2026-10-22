# Stage 1 operating steps

1. Build and launch using the README. A new installation has eight categories and an empty product catalog.
2. Choose **Categories**, type a category name and select **Add category**. Duplicate names are rejected without changing records.
3. Choose **Products → Add variant**. Enter product name, category, unique SKU, size, colour and selling price. Cost is optional. Enter opening quantity and the low-stock threshold.
4. Leave barcode empty to allocate a permanent internal barcode value. Rendering and printing Code 128 symbols belongs to Stage 2.
5. To add another size/colour of the same product, select it in **Add size/colour to existing product**. Each variant requires its own SKU and barcode.
6. Save. Search by product name, SKU or barcode and press Enter. A keyboard scanner can populate this search field; POS scanning is Stage 3.
7. Choose **Edit** to correct details or deactivate a variant. Existing barcodes are fixed. Product name/category changes affect every variant of that product. Opening quantity cannot be edited. If another edit was saved first, reload and retry.
8. **Inventory** displays stock from recorded opening movements. It does not allow unauthenticated stock adjustments.
9. Close and reopen the app; the database stays in the application data folder.

For startup errors inspect `backend.log` in the data folder. A service failure shows an error; restart the app. Do not delete the database to troubleshoot. Keep the original database and related WAL files when investigating failures. Backup and restore UI will be added and tested before production release.
