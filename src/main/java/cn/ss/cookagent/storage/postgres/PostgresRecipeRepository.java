package cn.ss.cookagent.storage.postgres;

import cn.ss.cookagent.recipe.domain.Recipe;
import cn.ss.cookagent.recipe.domain.RecipeIngredient;
import cn.ss.cookagent.recipe.domain.RecipeStep;
import cn.ss.cookagent.recipe.repository.RecipeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "app.storage", name = "mode", havingValue = "postgres")
public class PostgresRecipeRepository implements RecipeRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRecipeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Recipe> findAll() {
        String sql = """
                SELECT id, name, category, COALESCE(difficulty, 'beginner') AS difficulty,
                       COALESCE(cook_time_minutes, 30) AS cook_time_minutes,
                       COALESCE(summary, '') AS summary,
                       COALESCE(source_path, '') AS source_path
                FROM recipe
                WHERE status = 'ACTIVE'
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new Recipe(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("difficulty"),
                rs.getInt("cook_time_minutes"),
                rs.getString("summary"),
                List.of(rs.getString("category"), "postgres"),
                List.of(new RecipeIngredient("食材待入库", "", "")),
                List.of(new RecipeStep(1, "步骤待从 recipe_section 构建。")),
                List.of("当前为数据库首版占位详情。"),
                rs.getString("source_path")
        ));
    }

    @Override
    public Optional<Recipe> findById(long recipeId) {
        String sql = """
                SELECT id, name, category, COALESCE(difficulty, 'beginner') AS difficulty,
                       COALESCE(cook_time_minutes, 30) AS cook_time_minutes,
                       COALESCE(summary, '') AS summary,
                       COALESCE(source_path, '') AS source_path
                FROM recipe
                WHERE id = ? AND status = 'ACTIVE'
                """;

        List<Recipe> recipes = jdbcTemplate.query(sql, (rs, rowNum) -> {
            List<SectionRow> sections = loadSections(rs.getLong("id"));
            return new Recipe(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("difficulty"),
                    rs.getInt("cook_time_minutes"),
                    rs.getString("summary"),
                    List.of(rs.getString("category"), "postgres"),
                    parseIngredients(sections),
                    parseSteps(sections),
                    parseTips(sections),
                    rs.getString("source_path")
            );
        }, recipeId);

        return recipes.stream().findFirst();
    }

    private List<SectionRow> loadSections(long recipeId) {
        String sql = """
                SELECT COALESCE(section_type, 'intro') AS section_type,
                       COALESCE(content, '') AS content,
                       COALESCE(sort_order, 0) AS sort_order
                FROM recipe_section
                WHERE recipe_id = ?
                ORDER BY sort_order, id
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SectionRow(
                        rs.getString("section_type"),
                        rs.getString("content"),
                        rs.getInt("sort_order")
                ),
                recipeId);
    }

    private List<RecipeIngredient> parseIngredients(List<SectionRow> sections) {
        List<RecipeIngredient> items = sections.stream()
                .filter(section -> "ingredients".equalsIgnoreCase(section.sectionType()))
                .flatMap(section -> section.content().lines())
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripBulletPrefix)
                .map(line -> new RecipeIngredient(line, "", ""))
                .toList();

        if (!items.isEmpty()) {
            return items;
        }
        return List.of(new RecipeIngredient("食材待入库", "", ""));
    }

    private List<RecipeStep> parseSteps(List<SectionRow> sections) {
        List<String> lines = sections.stream()
                .filter(section -> "steps".equalsIgnoreCase(section.sectionType()))
                .flatMap(section -> section.content().lines())
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripBulletPrefix)
                .toList();

        if (lines.isEmpty()) {
            return List.of(new RecipeStep(1, "步骤待从 recipe_section 构建。"));
        }

        List<RecipeStep> steps = new ArrayList<>();
        int index = 1;
        for (String line : lines) {
            steps.add(new RecipeStep(index++, line));
        }
        return steps;
    }

    private List<String> parseTips(List<SectionRow> sections) {
        List<String> tips = sections.stream()
                .filter(section -> "tips".equalsIgnoreCase(section.sectionType()) || "intro".equalsIgnoreCase(section.sectionType()))
                .flatMap(section -> section.content().lines())
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::stripBulletPrefix)
                .limit(8)
                .toList();

        if (!tips.isEmpty()) {
            return tips;
        }
        return List.of("当前为数据库首版占位详情。");
    }

    private String stripBulletPrefix(String line) {
        return line.replaceFirst("^[-*\\u2022]\\s*", "")
                .replaceFirst("^\\d+[\\.)、]\\s*", "")
                .trim();
    }

    private record SectionRow(String sectionType, String content, int sortOrder) {
    }
}