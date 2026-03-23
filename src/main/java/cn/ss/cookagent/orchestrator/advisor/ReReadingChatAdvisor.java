package cn.ss.cookagent.orchestrator.advisor;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class ReReadingChatAdvisor implements ChatAdvisor {

    private static final String REREAD_BLOCK = """

            重新阅读要求:
            1. 先列出用户明确约束（食材、口味、时长、设备、忌口）。
            2. 回答必须逐条满足约束，证据不足时要明确指出。
            3. 不要编造未在上下文证据中出现的信息。
            """;

    @Override
    public AdvisorInput beforeCall(AdvisorInput input) {
        String userMessage = input.userMessage();
        if (!shouldEnhance(userMessage)) {
            return input;
        }
        String enhancedPrompt = input.userPrompt()
                + "\n\n用户原问题（再次确认）:\n"
                + (userMessage == null ? "" : userMessage)
                + REREAD_BLOCK;
        return input.withUserPrompt(enhancedPrompt);
    }

    private boolean shouldEnhance(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.trim();
        if (text.length() >= 20) {
            return true;
        }
        return text.contains("并且")
                || text.contains("同时")
                || text.contains("不要")
                || text.contains("不吃")
                || text.contains("少油")
                || text.contains("少盐")
                || text.contains("预算")
                || text.contains("分钟");
    }
}
