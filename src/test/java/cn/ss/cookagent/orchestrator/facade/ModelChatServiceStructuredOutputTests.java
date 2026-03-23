package cn.ss.cookagent.orchestrator.facade;

import cn.ss.cookagent.orchestrator.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelChatServiceStructuredOutputTests {

    private final ModelChatService service = new ModelChatService(
            null,
            new ObjectMapper(),
            new PromptTemplateService(),
            List.of()
    );

    @Test
    void parseModelOutput_shouldParseJsonAnswerAndFollowups() {
        String raw = """
                {
                  "answer": "先热锅下油，再炒番茄，最后回锅鸡蛋。",
                  "followups": ["可以少油版吗", "能用空气炸锅吗"]
                }
                """;

        ModelChatService.ModelOutput out = service.parseModelOutput(raw);
        assertThat(out.answer()).contains("先热锅下油");
        assertThat(out.followups()).containsExactly("可以少油版吗", "能用空气炸锅吗");
    }

    @Test
    void parseModelOutput_shouldSupportCodeFenceJson() {
        String raw = """
                ```json
                {
                  "answer": "蒸 8 分钟后焖 2 分钟。",
                  "followups": ["可以更嫩一点吗"]
                }
                ```
                """;

        ModelChatService.ModelOutput out = service.parseModelOutput(raw);
        assertThat(out.answer()).isEqualTo("蒸 8 分钟后焖 2 分钟。");
        assertThat(out.followups()).containsExactly("可以更嫩一点吗");
    }

    @Test
    void parseModelOutput_shouldFallbackToPlainTextWhenInvalidJson() {
        String raw = "这是一段普通文本回答，不是 JSON。";

        ModelChatService.ModelOutput out = service.parseModelOutput(raw);
        assertThat(out.answer()).isEqualTo(raw);
        assertThat(out.followups()).isEmpty();
    }
}
