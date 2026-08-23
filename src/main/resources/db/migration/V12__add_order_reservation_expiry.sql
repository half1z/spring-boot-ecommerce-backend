ALTER TABLE orders
    ADD COLUMN reservation_expires_at TIMESTAMP(6);

CREATE INDEX idx_orders_status_reservation_expiry
    ON orders(status, reservation_expires_at);