package cn.ss.cookagent.rag.ingest.repository;

import cn.ss.cookagent.rag.ingest.model.ParsedRecipeDocument;
import cn.ss.cookagent.rag.ingest.model.RecipeChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryRecipeIngestStore implements RecipeIngestStore {

    @Override
    public PersistedCounts persist(ParsedRecipeDocument document, List<RecipeChunk> chunks) {
        return new PersistedCounts(document.sections().size(), chunks.size(), true);
    }
}
