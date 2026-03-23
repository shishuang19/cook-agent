package cn.ss.cookagent.api.controller;

import cn.ss.cookagent.api.response.IngestResponse;
import cn.ss.cookagent.common.response.ApiResponse;
import cn.ss.cookagent.rag.ingest.model.IngestReport;
import cn.ss.cookagent.rag.ingest.service.RecipeIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminIngestController {

    private final RecipeIngestService recipeIngestService;

    public AdminIngestController(RecipeIngestService recipeIngestService) {
        this.recipeIngestService = recipeIngestService;
    }

    @PostMapping("/ingest/recipes")
    public ApiResponse<IngestResponse> ingestRecipes(
            @RequestParam(defaultValue = "0") int limit,
            @RequestParam(defaultValue = "false") boolean dryRun
    ) {
        IngestReport report = recipeIngestService.ingest(limit, dryRun);
        IngestResponse response = new IngestResponse(
                report.scannedFiles(),
                report.parsedRecipes(),
                report.persistedRecipes(),
            report.skippedUnchanged(),
                report.persistedSections(),
                report.persistedChunks(),
                report.dryRun(),
                report.errors()
        );
        return ApiResponse.success(response);
    }
}