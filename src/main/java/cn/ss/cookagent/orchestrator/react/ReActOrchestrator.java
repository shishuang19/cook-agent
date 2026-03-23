package cn.ss.cookagent.orchestrator.react;

import cn.ss.cookagent.orchestrator.tool.IntentTool;
import cn.ss.cookagent.orchestrator.tool.ToolExecutionResult;
import cn.ss.cookagent.rag.service.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class ReActOrchestrator {

    private final List<IntentTool> intentTools;
    private final int maxSteps;

    public ReActOrchestrator(
            List<IntentTool> intentTools,
            @Value("${app.agent.react.max-steps:2}") int maxSteps
    ) {
        this.intentTools = intentTools;
        this.maxSteps = Math.max(1, maxSteps);
    }

    public ReActResult execute(String intent, String message) {
        List<String> executedTools = new ArrayList<>();
        List<SearchService.SearchHit> selectedHits = List.of();
        List<SearchService.SearchHit> lastHits = List.of();
        Set<String> triedTools = new HashSet<>();
        String currentIntent = normalizeIntent(intent);

        for (int step = 0; step < maxSteps; step++) {
            Optional<IntentTool> toolOptional = selectTool(currentIntent, triedTools);
            if (toolOptional.isEmpty()) {
                break;
            }

            IntentTool tool = toolOptional.get();
            triedTools.add(tool.name());

            ToolExecutionResult execution = tool.execute(message);
            executedTools.add(execution.toolName());
            lastHits = execution.hits() == null ? List.of() : execution.hits();

            if (!lastHits.isEmpty()) {
                selectedHits = lastHits;
                break;
            }

            Optional<String> fallbackIntent = nextFallbackIntent(currentIntent, triedTools);
            if (fallbackIntent.isEmpty()) {
                break;
            }
            currentIntent = fallbackIntent.get();
        }

        if (selectedHits.isEmpty()) {
            selectedHits = lastHits;
        }
        String primaryToolName = executedTools.isEmpty() ? "none" : executedTools.get(0);
        return new ReActResult(primaryToolName, selectedHits, executedTools);
    }

    private Optional<IntentTool> selectTool(String intent, Set<String> triedTools) {
        Optional<IntentTool> directMatch = intentTools.stream()
                .filter(tool -> !triedTools.contains(tool.name()))
                .filter(tool -> tool.supports(intent))
                .findFirst();
        if (directMatch.isPresent()) {
            return directMatch;
        }

        // Ensure a deterministic fallback path for unknown intents.
        return intentTools.stream()
                .filter(tool -> !triedTools.contains(tool.name()))
                .filter(tool -> "query-tool".equals(tool.name()))
                .findFirst();
    }

    private Optional<String> nextFallbackIntent(String currentIntent, Set<String> triedTools) {
        if (triedTools.size() >= intentTools.size()) {
            return Optional.empty();
        }
        if ("recommend".equals(currentIntent)) {
            return Optional.of("qa");
        }
        return Optional.empty();
    }

    private String normalizeIntent(String intent) {
        return intent == null ? "qa" : intent.toLowerCase(Locale.ROOT).trim();
    }

    public record ReActResult(
            String primaryToolName,
            List<SearchService.SearchHit> hits,
            List<String> executedToolNames
    ) {
    }
}
