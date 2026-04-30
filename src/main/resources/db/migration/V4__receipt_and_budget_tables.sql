-- V4: Receipt scanning, spending categories and budget limits

CREATE TABLE receipts
(
    id            BIGSERIAL PRIMARY KEY,
    family_id     BIGINT         NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    uploaded_by   BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vendor_name   VARCHAR(200),
    purchase_date DATE,
    total_amount  NUMERIC(10, 2),
    -- PROCESSING → DONE or FAILED after Gemini processing
    status        VARCHAR(20)    NOT NULL DEFAULT 'PROCESSING',
    created_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_receipts_family_id ON receipts (family_id);
CREATE INDEX idx_receipts_family_status ON receipts (family_id, status);

CREATE TABLE receipt_items
(
    id           BIGSERIAL PRIMARY KEY,
    receipt_id   BIGINT         NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    product_name VARCHAR(300)   NOT NULL,
    quantity     NUMERIC(10, 3) NOT NULL DEFAULT 1,
    unit_price   NUMERIC(10, 2) NOT NULL,
    -- AI-assigned spending category (e.g. FOOD_HEALTHY, MEDICINE, CLOTHING …)
    category     VARCHAR(30)    NOT NULL DEFAULT 'OTHER'
);

CREATE INDEX idx_receipt_items_receipt_id ON receipt_items (receipt_id);

-- One row per family per spending category — PARENT sets the monthly limit
CREATE TABLE budget_limits
(
    id            BIGSERIAL PRIMARY KEY,
    family_id     BIGINT         NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    category      VARCHAR(30)    NOT NULL,
    monthly_limit NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_budget_limit UNIQUE (family_id, category)
);

CREATE INDEX idx_budget_limits_family_id ON budget_limits (family_id);
