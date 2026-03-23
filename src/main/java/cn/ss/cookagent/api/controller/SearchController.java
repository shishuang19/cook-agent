package cn.ss.cookagent.api.controller;

import cn.ss.cookagent.api.request.SearchRequest;
import cn.ss.cookagent.api.response.SearchResponse;
import cn.ss.cookagent.common.response.ApiResponse;
import cn.ss.cookagent.rag.service.SearchService;
import cn.ss.cookagent.recipe.domain.Recipe;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ApiResponse<SearchResponse> search(@RequestBody SearchRequest request) {
        int pageNo = request.pageNo() == null || request.pageNo() < 1 ? 1 : request.pageNo();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 12 : request.pageSize();

        String query = request.query() == null ? "" : request.query();
        List<Recipe> all = new ArrayList<>(searchService.search(request));

        String sortBy = request.sortBy();
        if ("time_asc".equals(sortBy)) {
            all.sort(Comparator.comparingInt(recipe -> safeCookTime(recipe.cookTimeMinutes())));
        } else if ("time_desc".equals(sortBy)) {
            all.sort(Comparator.comparingInt((Recipe recipe) -> safeCookTime(recipe.cookTimeMinutes())).reversed());
        } else if ("score_desc".equals(sortBy)) {
            all.sort(Comparator.comparingDouble((Recipe recipe) -> searchService.score(recipe, query)).reversed());
        }

        int fromIndex = Math.min((pageNo - 1) * pageSize, all.size());
        int toIndex = Math.min(fromIndex + pageSize, all.size());
        List<SearchResponse.SearchItem> items = all.subList(fromIndex, toIndex).stream()
                .map(recipe -> new SearchResponse.SearchItem(
                        recipe.recipeId(),
                        recipe.name(),
                        recipe.category(),
                        recipe.difficulty(),
                        recipe.cookTimeMinutes(),
                        recipe.summary(),
                        recipe.tags(),
                        searchService.score(recipe, query)
                ))
                .toList();

        return ApiResponse.success(new SearchResponse(all.size(), pageNo, pageSize, items));
    }

    private static int safeCookTime(Integer cookTimeMinutes) {
        return cookTimeMinutes == null ? Integer.MAX_VALUE : cookTimeMinutes;
    }
}
