CREATE TABLE IF NOT EXISTS test_value_object_holder (
    id BIGSERIAL PRIMARY KEY,
    price_amount NUMERIC(12, 2),
    price_currency VARCHAR(3),
    discount_rate NUMERIC(7, 6),
    contact_email VARCHAR(255),
    adjustment JSONB,
    period_start DATE,
    period_end DATE
);
