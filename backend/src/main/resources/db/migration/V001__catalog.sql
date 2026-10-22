CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at TEXT NOT NULL);
CREATE TABLE products (id TEXT PRIMARY KEY, name TEXT NOT NULL, category_id TEXT NOT NULL REFERENCES categories(id), created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE product_variants (
 id TEXT PRIMARY KEY, product_id TEXT NOT NULL REFERENCES products(id), sku TEXT NOT NULL COLLATE NOCASE UNIQUE,
 size TEXT NOT NULL, colour TEXT NOT NULL, barcode TEXT NOT NULL UNIQUE,
 cost_paise INTEGER CHECK(cost_paise >= 0), selling_paise INTEGER NOT NULL CHECK(selling_paise >= 0),
 reorder_level INTEGER NOT NULL CHECK(reorder_level >= 0), active INTEGER NOT NULL CHECK(active IN (0,1)),
 version INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 UNIQUE(product_id, size, colour)
);
CREATE TABLE stock_movements (
 id TEXT PRIMARY KEY, variant_id TEXT NOT NULL REFERENCES product_variants(id), quantity INTEGER NOT NULL,
 kind TEXT NOT NULL CHECK(kind IN ('OPENING','ADJUSTMENT','SALE','RETURN','CANCELLATION')),
 reason TEXT NOT NULL, reference_id TEXT, actor TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE INDEX stock_variant ON stock_movements(variant_id);
CREATE INDEX product_category ON products(category_id);
CREATE TABLE audit_logs (id TEXT PRIMARY KEY, action TEXT NOT NULL, entity_id TEXT NOT NULL, actor TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE TABLE application_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
INSERT INTO application_settings VALUES ('currency','INR');
INSERT INTO categories VALUES ('shirts','Shirts',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('tshirts','T-shirts',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('pants','Pants',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('trousers','Trousers',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('jeans','Jeans',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('oversized','Oversized T-shirts',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('jackets','Jackets',CURRENT_TIMESTAMP);
INSERT INTO categories VALUES ('other','Other',CURRENT_TIMESTAMP);
