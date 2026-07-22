INSERT INTO products (sku, name, description, price, stock_quantity, version, created_at, updated_at)
SELECT * FROM (SELECT 'SKU-LAPTOP-14' AS sku, 'ThinkPro 14" Laptop' AS name, 'Lightweight developer laptop, 16GB RAM, 512GB SSD' AS description, 1299.99 AS price, 25 AS stock_quantity, 0 AS version, NOW() AS created_at, NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SKU-LAPTOP-14');

INSERT INTO products (sku, name, description, price, stock_quantity, version, created_at, updated_at)
SELECT * FROM (SELECT 'SKU-MOUSE-01', 'Wireless Ergo Mouse', 'Bluetooth mouse with silent clicks', 29.99, 200, 0, NOW(), NOW()) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SKU-MOUSE-01');

INSERT INTO products (sku, name, description, price, stock_quantity, version, created_at, updated_at)
SELECT * FROM (SELECT 'SKU-KEYBOARD-01', 'Mechanical Keyboard 87-key', 'Hot-swappable mechanical keyboard', 89.50, 60, 0, NOW(), NOW()) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SKU-KEYBOARD-01');

INSERT INTO products (sku, name, description, price, stock_quantity, version, created_at, updated_at)
SELECT * FROM (SELECT 'SKU-MONITOR-27', '27" QHD Monitor', '27-inch IPS QHD monitor, 144Hz', 349.00, 15, 0, NOW(), NOW()) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SKU-MONITOR-27');

INSERT INTO products (sku, name, description, price, stock_quantity, version, created_at, updated_at)
SELECT * FROM (SELECT 'SKU-DOCK-USBC', 'USB-C Docking Station', '10-in-1 USB-C dock with dual HDMI', 129.00, 3, 0, NOW(), NOW()) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = 'SKU-DOCK-USBC');
