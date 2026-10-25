# Stage 3 — Billing and checkout

Stage 3 adds the first complete offline checkout path. A billing user or administrator can search or scan an active product variant, add it to a cart, change quantities, apply a bill discount, select cash, UPI, or card, and save a completed invoice.

The backend validates stock again during checkout and saves the invoice, invoice lines, payment records, stock sale movements, and audit record in one transaction. A failed checkout does not leave a partial sale or deduct stock. Money is stored as integer paise and the invoice keeps product and price snapshots.

Database migrations add `invoices`, `invoice_items`, and `payments`. Invoice numbers use the `EOT-YYYYMMDD-00001` format and remain unique.

Customer selection, split payments, invoice history UI, and receipt reprint integration remain to be completed before Stage 3 is marked final.
