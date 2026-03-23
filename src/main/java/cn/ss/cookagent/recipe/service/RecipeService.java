package cn.ss.cookagent.recipe.service;

import cn.ss.cookagent.common.exception.BizException;
import cn.ss.cookagent.recipe.domain.Recipe;
import cn.ss.cookagent.recipe.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public RecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public List<Recipe> listRecipes() {
        return recipeRepository.findAll();
    }

    public Recipe getRecipe(long recipeId) {
        return recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BizException("RECIPE_NOT_FOUND", "食谱不存在"));
    }
}
