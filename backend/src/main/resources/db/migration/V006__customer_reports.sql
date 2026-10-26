ALTER TABLE customers ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN updated_at TEXT;
UPDATE customers SET updated_at=created_at;
CREATE TABLE customer_changes (id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id), before_snapshot TEXT NOT NULL, after_snapshot TEXT NOT NULL, actor TEXT NOT NULL REFERENCES users(id), created_at TEXT NOT NULL);
CREATE INDEX customer_changes_customer ON customer_changes(customer_id,created_at);
CREATE INDEX customer_name ON customers(name);
CREATE INDEX movement_created ON stock_movements(created_at);
CREATE INDEX payment_created ON payments(created_at);
