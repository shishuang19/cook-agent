package cn.ss.cookagent.recipe.repository;

import cn.ss.cookagent.recipe.domain.Recipe;
import cn.ss.cookagent.recipe.domain.RecipeIngredient;
import cn.ss.cookagent.recipe.domain.RecipeStep;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@ConditionalOnProperty(prefix = "app.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryRecipeRepository implements RecipeRepository, InitializingBean {

    @Value("${app.data.cook-root:docs/cook}")
    private String cookRoot;

    private final List<Recipe> recipes = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        Path root = Path.of(cookRoot);
        if (!Files.exists(root)) {
            return;
        }

        AtomicLong idGenerator = new AtomicLong(100);
        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".md"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> recipes.add(buildRecipe(path, idGenerator.incrementAndGet(), root)));
        } catch (IOException ignored) {
            recipes.clear();
        }
    }

    @Override
    public List<Recipe> findAll() {
        return Collections.unmodifiableList(recipes);
    }

    @Override
    public Optional<Recipe> findById(long recipeId) {
        return recipes.stream().filter(recipe -> recipe.recipeId() == recipeId).findFirst();
    }

    private Recipe buildRecipe(Path file, long id, Path root) {
        String fileName = file.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 3);
        String category = extractCategory(file);
        String sourcePath = root.relativize(file).toString();

        String summary = "来自文档数据源：" + name;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            summary = lines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .findFirst()
                    .orElse(summary);
        } catch (IOException ignored) {
            // Keep default summary when source file cannot be read.
        }

        return new Recipe(
                id,
                name,
                category,
                "beginner",
                30,
                summary,
                List.of(category, "cook-doc"),
                List.of(new RecipeIngredient("食材待补充", "", "")),
                List.of(new RecipeStep(1, "来自 docs/cook 的原始文档，待结构化解析。")),
                List.of("首版返回最小可用信息。"),
                sourcePath
        );
    }

    private String extractCategory(Path file) {
        String normalized = file.toString().replace('\\', '/');
        String marker = "/dishes/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            return "unknown";
        }
        String remain = normalized.substring(markerIndex + marker.length());
        int slashIndex = remain.indexOf('/');
        if (slashIndex < 0) {
            return "unknown";
        }
        return remain.substring(0, slashIndex);
    }
}
