package cn.ss.cookagent.orchestrator.tool;

import cn.ss.cookagent.api.request.SearchRequest;
import cn.ss.cookagent.rag.service.SearchService;
import cn.ss.cookagent.recipe.domain.Recipe;
import cn.ss.cookagent.recipe.service.RecipeService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RecommendIntentTool implements IntentTool {

    private final SearchService searchService;
    private final RecipeService recipeService;

    public RecommendIntentTool(SearchService searchService, RecipeService recipeService) {
        this.searchService = searchService;
        this.recipeService = recipeService;
    }

    @Override
    public String name() {
        return "recommend-tool";
    }

    @Override
    public boolean supports(String intent) {
        return "recommend".equalsIgnoreCase(intent);
    }

    @Override
    public ToolExecutionResult execute(String message) {
        String keyword = extractRecommendKeyword(message);
        List<Recipe> candidates = searchService.search(new SearchRequest(
                keyword,
                Map.of(),
                1,
                6,
                "relevance"
        ));

        if (candidates.isEmpty()) {
            candidates = recipeService.listRecipes().stream()
                    .sorted(Comparator.comparingInt(Recipe::cookTimeMinutes).thenComparingLong(Recipe::recipeId))
                    .limit(6)
                    .toList();
        }

        List<SearchService.SearchHit> hits = candidates.stream()
                .limit(6)
                .map(recipe -> new SearchService.SearchHit(
                        recipe,
                        "recipe_" + recipe.recipeId() + "_recommend",
                        "recommend",
                        Math.min(searchService.score(recipe, keyword), 0.95),
                        "recommendation"
                ))
                .toList();

        return new ToolExecutionResult(name(), hits);
    }

    private String extractRecommendKeyword(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("早餐")) {
            return "早餐";
        }
        if (lower.contains("午饭") || lower.contains("中午")) {
            return "肉菜";
        }
        if (lower.contains("晚饭") || lower.contains("晚上")) {
            return "家常";
        }
        if (lower.contains("减脂") || lower.contains("低脂")) {
            return "轻食";
        }
        return "家常";
    }
}
