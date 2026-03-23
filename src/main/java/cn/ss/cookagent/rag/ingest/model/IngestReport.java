package cn.ss.cookagent.rag.ingest.model;

import java.util.List;

public record IngestReport(
        int scannedFiles,
        int parsedRecipes,
        int persistedRecipes,
        int skippedUnchanged,
        int persistedSections,
        int persistedChunks,
        boolean dryRun,
        List<String> errors
) {
}
