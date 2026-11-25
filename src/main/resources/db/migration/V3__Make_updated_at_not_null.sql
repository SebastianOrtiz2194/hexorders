-- V2 added updated_at without filling it in for orders that already existed.
-- Give those rows their created_at, the only timestamp they have, so the
-- column can become NOT NULL.
UPDATE orders SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE orders ALTER COLUMN updated_at SET NOT NULL;
