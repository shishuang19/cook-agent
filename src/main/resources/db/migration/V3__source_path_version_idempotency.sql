ALTER TABLE recipe
    ADD COLUMN IF NOT EXISTS source_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_recipe_source_path
    ON recipe(source_path)
    WHERE source_path IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_recipe_source_hash
    ON recipe(source_hash);
