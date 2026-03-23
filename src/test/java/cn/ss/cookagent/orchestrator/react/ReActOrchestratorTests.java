package cn.ss.cookagent.orchestrator.react;

import cn.ss.cookagent.orchestrator.tool.IntentTool;
import cn.ss.cookagent.orchestrator.tool.ToolExecutionResult;
import cn.ss.cookagent.rag.service.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReActOrchestratorTests {

    @Test
    void execute_shouldStopAfterFirstToolWhenHitsFound() {
        IntentTool queryTool = new StubTool(
                "query-tool",
                List.of("qa", "search"),
                List.of(new SearchService.SearchHit(null, "chunk_1", "summary", 0.9D, "test"))
        );
        IntentTool recommendTool = new StubTool("recommend-tool", List.of("recommend"), List.of());

        ReActOrchestrator orchestrator = new ReActOrchestrator(List.of(queryTool, recommendTool), 2);
        ReActOrchestrator.ReActResult result = orchestrator.execute("qa", "番茄炒蛋怎么做");

        assertThat(result.primaryToolName()).isEqualTo("query-tool");
        assertThat(result.executedToolNames()).containsExactly("query-tool");
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void execute_shouldFallbackToSecondToolWhenFirstReturnsEmpty() {
        IntentTool queryTool = new StubTool(
            "query-tool",
            List.of("qa", "search"),
            List.of(new SearchService.SearchHit(null, "chunk_2", "summary", 0.7D, "test"))
        );
        IntentTool recommendTool = new StubTool(
                "recommend-tool",
                List.of("recommend"),
            List.of()
        );

        ReActOrchestrator orchestrator = new ReActOrchestrator(List.of(queryTool, recommendTool), 2);
        ReActOrchestrator.ReActResult result = orchestrator.execute("recommend", "随便推荐一个");

        assertThat(result.primaryToolName()).isEqualTo("recommend-tool");
        assertThat(result.executedToolNames()).containsExactly("recommend-tool", "query-tool");
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void execute_shouldRespectMaxStepsLimit() {
        IntentTool queryTool = new StubTool(
            "query-tool",
            List.of("qa", "search"),
            List.of(new SearchService.SearchHit(null, "chunk_3", "summary", 0.7D, "test"))
        );
        IntentTool recommendTool = new StubTool(
                "recommend-tool",
                List.of("recommend"),
            List.of()
        );

        ReActOrchestrator orchestrator = new ReActOrchestrator(List.of(queryTool, recommendTool), 1);
        ReActOrchestrator.ReActResult result = orchestrator.execute("recommend", "随便推荐一个");

        assertThat(result.executedToolNames()).containsExactly("recommend-tool");
        assertThat(result.hits()).isEmpty();
    }

    private record StubTool(String name, List<String> intents, List<SearchService.SearchHit> hits) implements IntentTool {

        @Override
        public boolean supports(String intent) {
            return intents.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(intent));
        }

        @Override
        public ToolExecutionResult execute(String message) {
            return new ToolExecutionResult(name, hits);
        }
    }
}
