package cn.ss.cookagent.rag.ingest.repository;

import cn.ss.cookagent.rag.ingest.model.ParsedRecipeDocument;
import cn.ss.cookagent.rag.ingest.model.RecipeChunk;

import java.util.List;

public interface RecipeIngestStore {

    PersistedCounts persist(ParsedRecipeDocument document, List<RecipeChunk> chunks);

    record PersistedCounts(int sectionCount, int chunkCount, boolean changed) {
    }
}
