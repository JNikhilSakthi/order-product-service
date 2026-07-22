-- Creates one database + one dedicated user per service. Each service only
-- has access to its own schema, mirroring how two independently owned
-- microservices would never share a database in a real deployment.

CREATE DATABASE IF NOT EXISTS product_db;
CREATE USER IF NOT EXISTS 'product_user'@'%' IDENTIFIED BY 'product_pass';
GRANT ALL PRIVILEGES ON product_db.* TO 'product_user'@'%';

CREATE DATABASE IF NOT EXISTS order_db;
CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED BY 'order_pass';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_user'@'%';

FLUSH PRIVILEGES;
