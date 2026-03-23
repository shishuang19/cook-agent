package cn.ss.cookagent.recipe.repository;

import cn.ss.cookagent.recipe.domain.Recipe;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository {

    List<Recipe> findAll();

    Optional<Recipe> findById(long recipeId);
}
