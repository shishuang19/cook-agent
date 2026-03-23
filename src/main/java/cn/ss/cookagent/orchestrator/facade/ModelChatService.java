package cn.ss.cookagent.orchestrator.facade;

import cn.ss.cookagent.common.exception.BizException;
import cn.ss.cookagent.orchestrator.advisor.AdvisorInput;
import cn.ss.cookagent.orchestrator.advisor.ChatAdvisor;
import cn.ss.cookagent.orchestrator.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class ModelChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final List<ChatAdvisor> chatAdvisors;

    @Value("${app.llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${app.llm.api-key:}")
    private String apiKey;

    @Value("${app.llm.model:gpt-4o-mini}")
    private String model;

    @Value("${app.llm.temperature:0.3}")
    private double temperature;

    @Value("${app.llm.mock-enabled:false}")
    private boolean mockEnabled;

    public ModelChatService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService,
            List<ChatAdvisor> chatAdvisors
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
        this.chatAdvisors = chatAdvisors;
    }

    public ModelOutput generateAnswer(String userMessage, String context) {
        if (!llmEnabled) {
            throw new BizException("LLM_DISABLED", "当前环境未启用大模型能力");
        }

        if (mockEnabled) {
            return new ModelOutput(
                    "[mock-model] 已基于当前上下文生成建议：" + (userMessage == null ? "" : userMessage),
                    List.of()
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException("LLM_CONFIG_ERROR", "模型 API Key 未配置，请检查 app.llm.api-key 或环境变量");
        }

        String systemPrompt = promptTemplateService.buildSystemPrompt();
        String prompt = promptTemplateService.buildUserPrompt(safe(userMessage), safe(context));
        AdvisorInput advisorInput = new AdvisorInput(safe(userMessage), safe(context), systemPrompt, prompt, model);

        for (ChatAdvisor chatAdvisor : chatAdvisors) {
            advisorInput = chatAdvisor.beforeCall(advisorInput);
        }
        long start = System.currentTimeMillis();

        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(temperature)
                    .build();

            String content = chatClientBuilder.build()
                    .prompt()
                    .system(Objects.requireNonNull(advisorInput.systemPrompt(), "systemPrompt"))
                    .user(Objects.requireNonNull(advisorInput.userPrompt(), "userPrompt"))
                    .options(Objects.requireNonNull(options, "options"))
                    .call()
                    .content();

                if (content == null || content.isBlank()) {
                throw new BizException("LLM_EMPTY_RESPONSE", "模型未返回有效内容");
            }
            long elapsedMs = System.currentTimeMillis() - start;
            for (ChatAdvisor chatAdvisor : chatAdvisors) {
                chatAdvisor.afterCall(advisorInput, content, elapsedMs);
            }
                return parseModelOutput(content);
        } catch (BizException ex) {
            long elapsedMs = System.currentTimeMillis() - start;
            for (ChatAdvisor chatAdvisor : chatAdvisors) {
                chatAdvisor.onError(advisorInput, ex, elapsedMs);
            }
            throw ex;
        } catch (Exception ex) {
            long elapsedMs = System.currentTimeMillis() - start;
            for (ChatAdvisor chatAdvisor : chatAdvisors) {
                chatAdvisor.onError(advisorInput, ex, elapsedMs);
            }
            throw new BizException("LLM_CALL_EXCEPTION", "模型调用异常: " + ex.getMessage());
        }
    }

    public Stream<String> streamMarkdownAnswer(String userMessage, String context) {
        if (!llmEnabled) {
            throw new BizException("LLM_DISABLED", "当前环境未启用大模型能力");
        }
        if (mockEnabled) {
            String mock = "## 快速方案\n- 当前为 mock 模式\n- 请切换到真实模型以获取正式回答\n\n## 下一步\n1. 检查 API Key\n2. 重新请求";
            return List.of(mock).stream();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException("LLM_CONFIG_ERROR", "模型 API Key 未配置，请检查 app.llm.api-key 或环境变量");
        }

        String systemPrompt = promptTemplateService.buildSystemPrompt() + "\n\n"
                + "流式模式补充要求：直接输出 Markdown，不要输出 JSON，不要输出代码块。";
        String prompt = promptTemplateService.buildUserPrompt(safe(userMessage), safe(context));
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();

        try {
            return chatClientBuilder.build()
                    .prompt()
                    .system(Objects.requireNonNull(systemPrompt, "systemPrompt"))
                    .user(Objects.requireNonNull(prompt, "userPrompt"))
                    .options(Objects.requireNonNull(options, "options"))
                    .stream()
                    .content()
                    .toStream();
        } catch (Exception ex) {
            throw new BizException("LLM_CALL_EXCEPTION", "模型调用异常: " + ex.getMessage());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    ModelOutput parseModelOutput(String rawContent) {
        String normalized = stripCodeFence(rawContent);
        if (normalized.isBlank()) {
            return new ModelOutput("", List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String answer = root.path("answer").asText("").trim();
            List<String> followups = new ArrayList<>();
            JsonNode followupsNode = root.path("followups");
            if (followupsNode.isArray()) {
                followupsNode.forEach(node -> {
                    String val = node.asText("").trim();
                    if (!val.isBlank()) {
                        followups.add(val);
                    }
                });
            }
            if (answer.isBlank()) {
                answer = normalized;
            }
            return new ModelOutput(answer, followups);
        } catch (Exception ex) {
            return new ModelOutput(rawContent.trim(), List.of());
        }
    }

    private static String stripCodeFence(String content) {
        String text = safe(content).trim();
        if (text.startsWith("```") && text.endsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            if (firstLineBreak > 0 && firstLineBreak < text.length() - 3) {
                text = text.substring(firstLineBreak + 1, text.length() - 3).trim();
            }
        }
        return text;
    }

    public record ModelOutput(String answer, List<String> followups) {
    }
}