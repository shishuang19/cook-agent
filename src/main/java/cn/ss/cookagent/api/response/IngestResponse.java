package cn.ss.cookagent.api.response;

import java.util.List;

public record IngestResponse(
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
