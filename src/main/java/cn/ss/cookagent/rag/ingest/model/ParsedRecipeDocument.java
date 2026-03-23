package cn.ss.cookagent.rag.ingest.model;

import java.util.List;

public record ParsedRecipeDocument(
        String slug,
        String name,
        String category,
        String difficulty,
        int cookTimeMinutes,
        String summary,
        String sourcePath,
    String sourceVersion,
        List<String> tags,
        List<Section> sections
) {
    public record Section(String sectionType, String title, String content, int sortOrder) {
    }
}
