package cn.ss.cookagent.api.response;

import java.util.List;

public record SearchResponse(
        long total,
        int pageNo,
        int pageSize,
        List<SearchItem> items
) {
    public record SearchItem(
            long recipeId,
            String name,
            String category,
            String difficulty,
            int cookTimeMinutes,
            String summary,
            List<String> tags,
            double score
    ) {
    }
}
