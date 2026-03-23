package cn.ss.cookagent.api.response;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String intent,
        String answer,
        List<CitationItem> citations,
        List<String> followups,
        DebugInfo debug
) {
    public record CitationItem(
            long recipeId,
            String recipeName,
            String chunkId,
            String sectionType,
            double score,
            String hitSource
    ) {
    }

    public record DebugInfo(String route, int retrievalCount) {
    }
}
