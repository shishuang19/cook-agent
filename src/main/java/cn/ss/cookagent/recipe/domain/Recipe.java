package cn.ss.cookagent.recipe.domain;

import java.util.List;

public record Recipe(
        long recipeId,
        String name,
        String category,
        String difficulty,
        int cookTimeMinutes,
        String summary,
        List<String> tags,
        List<RecipeIngredient> ingredients,
        List<RecipeStep> steps,
        List<String> tips,
        String sourcePath
) {
}
