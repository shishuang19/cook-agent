package cn.ss.cookagent.api.controller;

import cn.ss.cookagent.api.response.RecipeDetailResponse;
import cn.ss.cookagent.common.response.ApiResponse;
import cn.ss.cookagent.recipe.domain.Recipe;
import cn.ss.cookagent.recipe.service.RecipeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/recipes/{recipeId}")
    public ApiResponse<RecipeDetailResponse> getRecipe(@PathVariable long recipeId) {
        Recipe recipe = recipeService.getRecipe(recipeId);
        RecipeDetailResponse response = new RecipeDetailResponse(
                recipe.recipeId(),
                recipe.name(),
                recipe.category(),
                recipe.difficulty(),
                recipe.cookTimeMinutes(),
                recipe.summary(),
                recipe.ingredients().stream()
                        .map(ingredient -> new RecipeDetailResponse.IngredientItem(
                                ingredient.name(), ingredient.amount(), ingredient.unit()))
                        .toList(),
                recipe.steps().stream()
                        .map(step -> new RecipeDetailResponse.StepItem(step.stepNo(), step.content()))
                        .toList(),
                recipe.tips()
        );
        return ApiResponse.success(response);
    }
}