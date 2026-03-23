package cn.ss.cookagent.storage.postgres;

import cn.ss.cookagent.rag.ingest.model.ParsedRecipeDocument;
import cn.ss.cookagent.rag.ingest.model.RecipeChunk;
import cn.ss.cookagent.rag.ingest.repository.RecipeIngestStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.storage", name = "mode", havingValue = "postgres")
public class PostgresRecipeIngestStore implements RecipeIngestStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRecipeIngestStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public PersistedCounts persist(ParsedRecipeDocument document, List<RecipeChunk> chunks) {
        RecipeState state = findBySourcePath(document.sourcePath());
        if (state != null && document.sourceVersion().equals(state.sourceHash())) {
            return new PersistedCounts(0, 0, false);
        }

        long recipeId = upsertRecipe(document);
        replaceSections(recipeId, document.sections());
        upsertChunks(recipeId, chunks);
        return new PersistedCounts(document.sections().size(), chunks.size(), true);
    }

    private RecipeState findBySourcePath(String sourcePath) {
        String sql = "SELECT id, COALESCE(source_hash, '') AS source_hash FROM recipe WHERE source_path = ? LIMIT 1";
        List<RecipeState> states = jdbcTemplate.query(sql, (rs, rowNum) ->
                new RecipeState(rs.getLong("id"), rs.getString("source_hash")), sourcePath);
        return states.isEmpty() ? null : states.getFirst();
    }

    private long upsertRecipe(ParsedRecipeDocument document) {
        String sql = """
                INSERT INTO recipe(name, slug, category, difficulty, cook_time_minutes, summary, source_path, source_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                ON CONFLICT (source_path)
                DO UPDATE SET
                    name = EXCLUDED.name,
                    slug = EXCLUDED.slug,
                    category = EXCLUDED.category,
                    difficulty = EXCLUDED.difficulty,
                    cook_time_minutes = EXCLUDED.cook_time_minutes,
                    summary = EXCLUDED.summary,
                    source_path = EXCLUDED.source_path,
                    source_hash = EXCLUDED.source_hash,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """;

        return jdbcTemplate.queryForObject(sql, Long.class,
                document.name(),
                document.slug(),
                document.category(),
                document.difficulty(),
                document.cookTimeMinutes(),
                document.summary(),
                document.sourcePath(),
                document.sourceVersion());
    }

    private void replaceSections(long recipeId, List<ParsedRecipeDocument.Section> sections) {
        jdbcTemplate.update("DELETE FROM recipe_section WHERE recipe_id = ?", recipeId);
        String insertSql = """
                INSERT INTO recipe_section(recipe_id, section_type, title, content, sort_order, token_estimate)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        for (ParsedRecipeDocument.Section section : sections) {
            jdbcTemplate.update(insertSql,
                    recipeId,
                    section.sectionType(),
                    section.title(),
                    section.content(),
                    section.sortOrder(),
                    estimateTokens(section.content()));
        }
    }

    private void upsertChunks(long recipeId, List<RecipeChunk> chunks) {
        jdbcTemplate.update("DELETE FROM recipe_chunk_vector WHERE recipe_id = ?", recipeId);

        String sql = """
                INSERT INTO recipe_chunk_vector(recipe_id, chunk_id, chunk_text, chunk_summary, section_type, metadata, tsv)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), to_tsvector('simple', ?))
                ON CONFLICT (chunk_id)
                DO UPDATE SET
                    recipe_id = EXCLUDED.recipe_id,
                    chunk_text = EXCLUDED.chunk_text,
                    chunk_summary = EXCLUDED.chunk_summary,
                    section_type = EXCLUDED.section_type,
                    metadata = EXCLUDED.metadata,
                    tsv = EXCLUDED.tsv,
                    updated_at = CURRENT_TIMESTAMP
                """;

        for (RecipeChunk chunk : chunks) {
            jdbcTemplate.update(sql,
                    recipeId,
                    chunk.chunkId(),
                    chunk.chunkText(),
                    summarize(chunk.chunkText()),
                    chunk.sectionType(),
                    chunk.metadataJson(),
                    chunk.chunkText());
        }
    }

    private int estimateTokens(String content) {
        return Math.max(1, content.length() / 2);
    }

    private String summarize(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

    private record RecipeState(long id, String sourceHash) {
    }
}
