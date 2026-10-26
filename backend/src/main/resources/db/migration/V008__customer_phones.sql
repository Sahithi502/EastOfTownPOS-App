ALTER TABLE customers ADD COLUMN phone_region TEXT;
ALTER TABLE customers ADD COLUMN phone_e164 TEXT;
CREATE UNIQUE INDEX customers_phone_e164_unique ON customers(phone_e164) WHERE phone_e164 IS NOT NULL;
