package cn.ss.cookagent.api.response;

import java.util.List;

public record RecipeDetailResponse(
        long recipeId,
        String name,
        String category,
        String difficulty,
        int cookTimeMinutes,
        String summary,
        List<IngredientItem> ingredients,
        List<StepItem> steps,
        List<String> tips
) {
    public record IngredientItem(String name, String amount, String unit) {
    }

    public record StepItem(int stepNo, String content) {
    }
}
