-- PostgreSQL does not index foreign keys on its own. Loading an order looks up
-- its items by order_id, which scans the whole order_items table without this.
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- Order list filtered by status: WHERE status = ? ORDER BY created_at DESC.
-- With both columns in the index, PostgreSQL reads one page of rows already in
-- order and stops, instead of sorting every order that has that status.
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at DESC);

-- Order list without a filter: ORDER BY created_at DESC.
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
