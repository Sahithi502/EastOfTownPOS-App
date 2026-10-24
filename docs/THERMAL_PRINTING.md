# 80 mm thermal barcode printing

In **Barcode labels**, select **Print layout → Thermal POS roll — 80 mm × automatic height**. Select products and copies, then **Preview labels**. Width is fixed at 80 mm; the manual label dimensions do not affect thermal mode. The height grows with product names, variants, SKUs and copies. Each job has one continuous page with 4 mm side margins, the shop heading, product details, selling price and permanent Code 128 barcodes. Copies are repeated on the roll.

Select a Windows thermal printer and use **Test print** to check alignment. Use the matching 80 mm roll/custom paper size in the Windows driver and actual-size (100%) scaling. **Print labels** submits the same document shown in the preview. Windows spooler acceptance is reported as submission, not proof that paper printed. Driver support for custom page lengths varies; verify the printer's output on the actual device. Physical printer calibration remains a release acceptance step.

**Save PDF** provides the identical layout when a printer is unavailable. Automatic cutting and raw ESC/POS commands are not configured. WhatsApp and PNG export are deferred at the user's request. Sales/payment receipts remain in Stage 3.

**Save preferences** stores the selected layout and printer for administrators. Individual label mode remains available with its existing fixed dimensions. Existing installations default to individual labels until thermal mode is selected. Stock, prices and barcode values are read without modification.

For resource safety, thermal jobs are limited to 2000 mm in height; split longer jobs into fewer copies. Preview is rendered at 203 DPI and has a 16-million-pixel ceiling. Barcodes that cannot fit at the minimum supported module width are rejected rather than shrunk below the readability limit.

Implementation: the request and settings use an optional `thermal` boolean (default false). The server builds one PDF with content-based height. Preview rasterizes this PDF; printing takes its actual page size. No schema migration or new dependency is required.

Verification covers 80 mm page width, growing height, repeated content, pixel-identical PDF/preview rendering, authorized PDF access, excessive-height rejection, and the Electron layout selector/preview/PDF save workflow. See the PR and automated check results for execution evidence.

Local checks on 24 October 2026: 27 Java tests passed, TypeScript/Vite build passed, API/restart smoke passed, and the Electron acceptance test saved a two-copy thermal PDF and verified restart persistence in an isolated profile. The thermal preview screenshot is in `docs/screenshots/stage2-thermal-roll.png`. No physical printer was used in these checks.
