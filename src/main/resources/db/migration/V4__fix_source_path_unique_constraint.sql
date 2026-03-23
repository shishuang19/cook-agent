UPDATE recipe
SET source_path = COALESCE(NULLIF(BTRIM(source_path), ''), COALESCE(slug, 'recipe-' || id::text))
WHERE source_path IS NULL OR BTRIM(source_path) = '';

ALTER TABLE recipe
    ALTER COLUMN source_path SET NOT NULL;

DROP INDEX IF EXISTS ux_recipe_source_path;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_recipe_source_path'
    ) THEN
        ALTER TABLE recipe
            ADD CONSTRAINT uk_recipe_source_path UNIQUE (source_path);
    END IF;
END
$$;
