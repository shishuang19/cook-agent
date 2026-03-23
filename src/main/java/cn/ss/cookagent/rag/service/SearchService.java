package cn.ss.cookagent.rag.service;

import cn.ss.cookagent.api.request.SearchRequest;
import cn.ss.cookagent.recipe.domain.Recipe;
import cn.ss.cookagent.recipe.domain.RecipeIngredient;
import cn.ss.cookagent.recipe.domain.RecipeStep;
import cn.ss.cookagent.recipe.service.RecipeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchService {

    public record SearchHit(
            Recipe recipe,
            String chunkId,
            String sectionType,
            double score,
            String hitSource
    ) {
    }

    private final RecipeService recipeService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.storage.mode:memory}")
    private String storageMode;

    @Value("${app.search.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${app.search.hybrid.single-char-variant:true}")
    private boolean singleCharVariantEnabled;

    @Value("${app.search.hybrid.chunk-score-weight:0.70}")
    private double chunkScoreWeight;

    @Value("${app.search.hybrid.chunk-hit-bonus-step:0.05}")
    private double chunkHitBonusStep;

    @Value("${app.search.hybrid.chunk-hit-bonus-max-count:5}")
    private int chunkHitBonusMaxCount;

    @Value("${app.search.hybrid.metadata-score-weight:0.40}")
    private double metadataScoreWeight;

    @Value("${app.search.hybrid.candidate-limit:200}")
    private int candidateLimit;

    public SearchService(RecipeService recipeService, ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.recipeService = recipeService;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public List<Recipe> search(SearchRequest request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        Map<String, Object> filters = request.filters() == null ? Map.of() : request.filters();
        String category = String.valueOf(filters.getOrDefault("category", "")).trim();
        int maxCookMinutes = parseInt(filters.get("maxCookTimeMinutes"), Integer.MAX_VALUE);

        if (isPostgresMode() && hybridEnabled && jdbcTemplate != null && !query.isBlank()) {
            return hybridSearchInPostgres(query, category, maxCookMinutes);
        }

        return recipeService.listRecipes().stream()
                .filter(recipe -> query.isBlank() || containsQuery(recipe, query))
                .filter(recipe -> category.isBlank() || category.equalsIgnoreCase(recipe.category()))
                .filter(recipe -> recipe.cookTimeMinutes() <= maxCookMinutes)
                .sorted(Comparator.comparingDouble(recipe -> -score(recipe, query)))
                .toList();
    }

    public List<Recipe> searchForChat(String message, int topK) {
        return searchHitsForChat(message, topK).stream()
                .map(SearchHit::recipe)
                .toList();
    }

    public List<SearchHit> searchHitsForChat(String message, int topK) {
        int safeTopK = Math.max(topK, 1);
        String query = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);

        if (isPostgresMode() && hybridEnabled && jdbcTemplate != null && !query.isBlank()) {
            return hybridSearchHitsInPostgres(query, safeTopK);
        }

        SearchRequest request = new SearchRequest(message, Map.of(), 1, safeTopK, "relevance");
        List<Recipe> recipes = search(request).stream().limit(safeTopK).toList();
        return recipes.stream()
                .map(recipe -> new SearchHit(
                        recipe,
                        "recipe_" + recipe.recipeId() + "_summary",
                        "summary",
                        score(recipe, message),
                        "memory_fallback"
                ))
                .toList();
    }

    public double score(Recipe recipe, String query) {
        if (query == null || query.isBlank()) {
            return 0.6;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        double score = 0.3;
        String lowerName = recipe.name().toLowerCase(Locale.ROOT);
        String lowerSummary = recipe.summary().toLowerCase(Locale.ROOT);
        if (lowerName.contains(normalized)) {
            score += 0.5;
        }
        if (lowerSummary.contains(normalized)) {
            score += 0.2;
        }
        if (recipe.category().toLowerCase(Locale.ROOT).contains(normalized)) {
            score += 0.15;
        }
        return Math.min(score, 0.99);
    }

    private boolean containsQuery(Recipe recipe, String query) {
        return recipe.name().toLowerCase(Locale.ROOT).contains(query)
                || recipe.summary().toLowerCase(Locale.ROOT).contains(query)
                || recipe.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(query));
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean isPostgresMode() {
        return "postgres".equalsIgnoreCase(storageMode);
    }

    private List<Recipe> hybridSearchInPostgres(String query, String category, int maxCookMinutes) {
        List<String> variants = buildQueryVariants(query);
        Map<Long, Double> scoreMap = new HashMap<>();

        for (String variant : variants) {
            String pattern = "%" + variant + "%";
            mergeChunkScores(scoreMap, pattern);
            mergeRecipeMetadataScores(scoreMap, pattern);
        }

        if (scoreMap.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, name, category, COALESCE(difficulty, 'beginner') AS difficulty,
                       COALESCE(cook_time_minutes, 30) AS cook_time_minutes,
                       COALESCE(summary, '') AS summary,
                       COALESCE(source_path, '') AS source_path
                FROM recipe
                WHERE status = 'ACTIVE'
                """);

        List<Object> args = new ArrayList<>();
        List<Long> recipeIds = scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(Math.max(candidateLimit, 10))
            .map(Map.Entry::getKey)
            .toList();
        sql.append(" AND id IN (")
                .append(recipeIds.stream().map(id -> "?").collect(Collectors.joining(",")))
                .append(")");
        args.addAll(recipeIds);

        if (!category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        if (maxCookMinutes < Integer.MAX_VALUE) {
            sql.append(" AND COALESCE(cook_time_minutes, 30) <= ?");
            args.add(maxCookMinutes);
        }

        List<Recipe> candidates = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Recipe(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("difficulty"),
                rs.getInt("cook_time_minutes"),
                rs.getString("summary"),
                List.of(rs.getString("category"), "postgres", "hybrid"),
                List.of(new RecipeIngredient("食材待入库", "", "")),
                List.of(new RecipeStep(1, "步骤待从 recipe_section 构建。")),
                List.of("当前为数据库首版占位详情。"),
                rs.getString("source_path")
        ), args.toArray());

        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((Recipe recipe) -> -scoreMap.getOrDefault(recipe.recipeId(), 0.0))
                        .thenComparingLong(Recipe::recipeId))
                .toList();
    }

    private List<SearchHit> hybridSearchHitsInPostgres(String query, int topK) {
        List<String> variants = buildQueryVariants(query);
        Map<Long, Double> scoreMap = new HashMap<>();

        for (String variant : variants) {
            String pattern = "%" + variant + "%";
            mergeChunkScores(scoreMap, pattern);
            mergeRecipeMetadataScores(scoreMap, pattern);
        }

        if (scoreMap.isEmpty()) {
            return List.of();
        }

        int candidateCount = Math.max(topK * 4, topK);
        List<Long> recipeIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(candidateCount)
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, Recipe> recipeMap = fetchRecipesByIds(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::recipeId, recipe -> recipe));
        Map<Long, ChunkEvidence> evidenceMap = fetchBestChunkEvidence(recipeIds, variants);

        return recipeIds.stream()
                .filter(recipeMap::containsKey)
                .map(recipeId -> {
                    Recipe recipe = recipeMap.get(recipeId);
                    ChunkEvidence evidence = evidenceMap.get(recipeId);
                    String hitSource = evidence == null || evidence.matchScore() <= 0.0 ? "metadata" : "chunk";
                    String chunkId = evidence == null ? "recipe_" + recipeId + "_summary" : evidence.chunkId();
                    String sectionType = evidence == null ? "summary" : evidence.sectionType();
                    double score = Math.min(scoreMap.getOrDefault(recipeId, 0.0), 0.99);
                    return new SearchHit(recipe, chunkId, sectionType, score, hitSource);
                })
                .limit(topK)
                .toList();
    }

    private void mergeChunkScores(Map<Long, Double> scoreMap, String pattern) {
        String sql = """
                SELECT recipe_id,
                       MAX(CASE WHEN chunk_text ILIKE ? THEN 1.0 ELSE 0.0 END
                           + CASE WHEN chunk_summary ILIKE ? THEN 0.6 ELSE 0.0 END
                           + CASE WHEN section_type ILIKE ? THEN 0.2 ELSE 0.0 END) AS keyword_score,
                       SUM(CASE WHEN chunk_text ILIKE ? OR chunk_summary ILIKE ? THEN 1 ELSE 0 END) AS hit_count
                FROM recipe_chunk_vector
                WHERE chunk_text ILIKE ? OR chunk_summary ILIKE ? OR section_type ILIKE ?
                GROUP BY recipe_id
                """;

        jdbcTemplate.query(sql, rs -> {
                    long recipeId = rs.getLong("recipe_id");
                    double keywordScore = rs.getDouble("keyword_score");
                    int hitCount = rs.getInt("hit_count");
                    int limitedHitCount = Math.min(hitCount, Math.max(chunkHitBonusMaxCount, 1));
                    double mixed = keywordScore * chunkScoreWeight + limitedHitCount * chunkHitBonusStep;
                    scoreMap.merge(recipeId, mixed, Math::max);
                },
                pattern, pattern, pattern,
                pattern, pattern,
                pattern, pattern, pattern);
    }

    private void mergeRecipeMetadataScores(Map<Long, Double> scoreMap, String pattern) {
        String sql = """
                SELECT id,
                       (CASE WHEN name ILIKE ? THEN 1.0 ELSE 0.0 END
                        + CASE WHEN summary ILIKE ? THEN 0.6 ELSE 0.0 END
                        + CASE WHEN category ILIKE ? THEN 0.8 ELSE 0.0 END
                        + CASE WHEN source_path ILIKE ? THEN 0.3 ELSE 0.0 END) AS metadata_score
                FROM recipe
                WHERE status = 'ACTIVE'
                  AND (name ILIKE ? OR summary ILIKE ? OR category ILIKE ? OR source_path ILIKE ?)
                """;

        jdbcTemplate.query(sql, rs -> {
                    long recipeId = rs.getLong("id");
                    double metadataScore = rs.getDouble("metadata_score") * metadataScoreWeight;
                    scoreMap.merge(recipeId, metadataScore, Math::max);
                },
                pattern, pattern, pattern, pattern,
                pattern, pattern, pattern, pattern);
    }

    private List<Recipe> fetchRecipesByIds(List<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT id, name, category, COALESCE(difficulty, 'beginner') AS difficulty,
                       COALESCE(cook_time_minutes, 30) AS cook_time_minutes,
                       COALESCE(summary, '') AS summary,
                       COALESCE(source_path, '') AS source_path
                FROM recipe
                WHERE status = 'ACTIVE'
                  AND id IN (%s)
                """.formatted(recipeIds.stream().map(id -> "?").collect(Collectors.joining(",")));

        return jdbcTemplate.query(sql, (rs, rowNum) -> new Recipe(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("difficulty"),
                rs.getInt("cook_time_minutes"),
                rs.getString("summary"),
                List.of(rs.getString("category"), "postgres", "hybrid"),
                List.of(new RecipeIngredient("食材待入库", "", "")),
                List.of(new RecipeStep(1, "步骤待从 recipe_section 构建。")),
                List.of("当前为数据库首版占位详情。"),
                rs.getString("source_path")
        ), recipeIds.toArray());
    }

    private Map<Long, ChunkEvidence> fetchBestChunkEvidence(List<Long> recipeIds, List<String> variants) {
        if (recipeIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                SELECT recipe_id,
                       chunk_id,
                       COALESCE(section_type, 'summary') AS section_type,
                       COALESCE(chunk_text, '') AS chunk_text,
                       COALESCE(chunk_summary, '') AS chunk_summary
                FROM recipe_chunk_vector
                WHERE recipe_id IN (%s)
                """.formatted(recipeIds.stream().map(id -> "?").collect(Collectors.joining(",")));

        Map<Long, ChunkEvidence> bestMap = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long recipeId = rs.getLong("recipe_id");
            String chunkId = rs.getString("chunk_id");
            String sectionType = rs.getString("section_type");
            String chunkText = rs.getString("chunk_text");
            String chunkSummary = rs.getString("chunk_summary");
            double score = computeChunkEvidenceScore(chunkText, chunkSummary, sectionType, variants);

            ChunkEvidence old = bestMap.get(recipeId);
            if (old == null || score > old.matchScore()) {
                bestMap.put(recipeId, new ChunkEvidence(chunkId, sectionType, score));
            }
        }, recipeIds.toArray());

        return bestMap;
    }

    private double computeChunkEvidenceScore(String chunkText, String chunkSummary, String sectionType, List<String> variants) {
        String text = chunkText == null ? "" : chunkText.toLowerCase(Locale.ROOT);
        String summary = chunkSummary == null ? "" : chunkSummary.toLowerCase(Locale.ROOT);
        String section = sectionType == null ? "" : sectionType.toLowerCase(Locale.ROOT);

        double best = 0.0;
        for (String variant : variants) {
            if (variant == null || variant.isBlank()) {
                continue;
            }
            double current = 0.0;
            if (text.contains(variant)) {
                current += 1.0;
            }
            if (summary.contains(variant)) {
                current += 0.6;
            }
            if (section.contains(variant)) {
                current += 0.2;
            }
            if (current > best) {
                best = current;
            }
        }
        return best;
    }

    private List<String> buildQueryVariants(String query) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(query);
        variants.add(mapCategoryAlias(query));

        if (singleCharVariantEnabled && query.length() >= 2) {
            for (int i = 0; i < query.length(); i++) {
                String oneChar = query.substring(i, i + 1);
                if (!oneChar.isBlank()) {
                    variants.add(oneChar);
                }
            }
        }

        return variants.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .toList();
    }

    private String mapCategoryAlias(String query) {
        return switch (query) {
            case "早餐" -> "breakfast";
            case "甜品", "甜点" -> "dessert";
            case "饮品", "饮料" -> "drink";
            case "海鲜" -> "aquatic";
            case "肉菜", "肉" -> "meat_dish";
            case "汤" -> "soup";
            case "鸡肉" -> "鸡";
            case "清蒸" -> "蒸";
            case "减脂晚餐" -> "轻食";
            default -> query;
        };
    }

    private record ChunkEvidence(String chunkId, String sectionType, double matchScore) {
    }
}
