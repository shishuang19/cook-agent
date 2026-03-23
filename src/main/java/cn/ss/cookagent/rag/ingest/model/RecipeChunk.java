package cn.ss.cookagent.rag.ingest.model;

public record RecipeChunk(
        String chunkId,
        String sectionType,
        String chunkText,
        String metadataJson
) {
}
