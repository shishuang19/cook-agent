package cn.ss.cookagent.rag.ingest.service;

import cn.ss.cookagent.rag.ingest.model.IngestReport;
import cn.ss.cookagent.rag.ingest.model.ParsedRecipeDocument;
import cn.ss.cookagent.rag.ingest.model.RecipeChunk;
import cn.ss.cookagent.rag.ingest.parser.MarkdownRecipeParser;
import cn.ss.cookagent.rag.ingest.repository.RecipeIngestStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RecipeIngestService {

    private final MarkdownRecipeParser parser;
    private final RecipeIngestStore ingestStore;

    @Value("${app.data.cook-root:docs/cook}")
    private String cookRoot;

    public RecipeIngestService(MarkdownRecipeParser parser, RecipeIngestStore ingestStore) {
        this.parser = parser;
        this.ingestStore = ingestStore;
    }

    public IngestReport ingest(int limit, boolean dryRun) {
        Path root = Path.of(cookRoot);
        if (!Files.exists(root)) {
            return new IngestReport(0, 0, 0, 0, 0, 0, dryRun, List.of("cook root not found: " + cookRoot));
        }

        List<Path> files;
        try {
            files = Files.walk(root)
                    .filter(path -> path.toString().endsWith(".md"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException ex) {
            return new IngestReport(0, 0, 0, 0, 0, 0, dryRun, List.of("scan failed: " + ex.getMessage()));
        }

        int max = limit > 0 ? Math.min(limit, files.size()) : files.size();
        int parsed = 0;
        int persisted = 0;
        int sectionCount = 0;
        int chunkCount = 0;
        List<String> errors = new ArrayList<>();
        int skippedUnchanged = 0;

        for (int i = 0; i < max; i++) {
            Path file = files.get(i);
            try {
                ParsedRecipeDocument document = parser.parse(file, root);
                parsed++;
                List<RecipeChunk> chunks = buildChunks(document);
                if (!dryRun) {
                    RecipeIngestStore.PersistedCounts counts = ingestStore.persist(document, chunks);
                    if (counts.changed()) {
                        persisted++;
                        sectionCount += counts.sectionCount();
                        chunkCount += counts.chunkCount();
                    } else {
                        skippedUnchanged++;
                    }
                }
            } catch (Exception ex) {
                errors.add(file.toString() + " => " + ex.getMessage());
            }
        }

        return new IngestReport(files.size(), parsed, persisted, skippedUnchanged, sectionCount, chunkCount, dryRun, errors);
    }

    private List<RecipeChunk> buildChunks(ParsedRecipeDocument document) {
        List<RecipeChunk> chunks = new ArrayList<>();
        for (ParsedRecipeDocument.Section section : document.sections()) {
            List<String> parts = splitByLength(section.content(), 420);
            for (int i = 0; i < parts.size(); i++) {
                String chunkId = document.slug()
                        + "-"
                        + section.sectionType()
                        + "-"
                        + section.sortOrder()
                        + "-"
                        + (i + 1);
                String metadataJson = "{" +
                        "\"recipeName\":\"" + escapeJson(document.name()) + "\"," +
                        "\"category\":\"" + escapeJson(document.category()) + "\"," +
                        "\"sourcePath\":\"" + escapeJson(document.sourcePath()) + "\"" +
                        "}";
                chunks.add(new RecipeChunk(chunkId, section.sectionType(), parts.get(i), metadataJson));
            }
        }
        return chunks;
    }

    private List<String> splitByLength(String content, int maxLen) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return List.of("无内容");
        }
        if (normalized.length() <= maxLen) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int from = 0;
        while (from < normalized.length()) {
            int to = Math.min(from + maxLen, normalized.length());
            chunks.add(normalized.substring(from, to));
            from = to;
        }
        return chunks;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ");
    }
}
