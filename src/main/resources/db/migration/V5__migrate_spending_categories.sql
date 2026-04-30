-- Migrate receipt_items and budget_limits from old SpendingCategory values
-- to the new refined category set.
--
-- Old → New mapping:
--   FOOD_HEALTHY  → FOOD_OTHER  (too broad to auto-split into produce/dairy/protein)
--   FOOD_SWEETS   → FOOD_SNACKS (sweets + salty snacks merged)
--   FOOD_FASTFOOD → FOOD_OTHER  (removed category)
--   EDUCATION     → OTHER       (removed category)
--
-- Categories that stay unchanged:
--   FOOD_ALCOHOL, FOOD_DRINKS, FOOD_OTHER, MEDICINE, SUPPLEMENTS,
--   HYGIENE, HOUSEHOLD, CLEANING, CLOTHING, ENTERTAINMENT, ELECTRONICS,
--   PETS, CHILDREN, TRANSPORT, OTHER

UPDATE receipt_items SET category = 'FOOD_OTHER'  WHERE category = 'FOOD_HEALTHY';
UPDATE receipt_items SET category = 'FOOD_SNACKS' WHERE category = 'FOOD_SWEETS';
UPDATE receipt_items SET category = 'FOOD_OTHER'  WHERE category = 'FOOD_FASTFOOD';
UPDATE receipt_items SET category = 'OTHER'       WHERE category = 'EDUCATION';

-- budget_limits has a unique constraint on (family_id, category), so we must merge
-- overlapping rows before renaming categories.
WITH category_map(old_category, new_category) AS (
    VALUES
        ('FOOD_HEALTHY', 'FOOD_OTHER'),
        ('FOOD_SWEETS', 'FOOD_SNACKS'),
        ('FOOD_FASTFOOD', 'FOOD_OTHER'),
        ('EDUCATION', 'OTHER')
),
affected_categories AS (
    SELECT old_category AS category FROM category_map
    UNION
    SELECT new_category AS category FROM category_map
),
merged_limits AS (
    SELECT bl.family_id,
           COALESCE(cm.new_category, bl.category) AS category,
           SUM(bl.monthly_limit)                  AS monthly_limit
    FROM budget_limits bl
    LEFT JOIN category_map cm
           ON bl.category = cm.old_category
    WHERE bl.category IN (SELECT category FROM affected_categories)
    GROUP BY bl.family_id, COALESCE(cm.new_category, bl.category)
),
deleted_limits AS (
    DELETE FROM budget_limits
    WHERE category IN (SELECT category FROM affected_categories)
    RETURNING id
)
INSERT INTO budget_limits (family_id, category, monthly_limit)
SELECT family_id, category, monthly_limit
FROM merged_limits;
