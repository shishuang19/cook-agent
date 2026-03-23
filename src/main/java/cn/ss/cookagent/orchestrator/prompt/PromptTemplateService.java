package cn.ss.cookagent.orchestrator.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptTemplateService {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是 Kitchen Orbit 的专业烹饪助手。请基于给定证据回答，优先给出可执行建议，语言简洁清晰。";
    private static final String DEFAULT_USER_PROMPT = "用户问题:\n{{userMessage}}\n\n上下文证据:\n{{context}}";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String buildSystemPrompt() {
        String ragPrompt = loadOrDefault("prompts/rag/rag-answer.st", DEFAULT_SYSTEM_PROMPT);
        String styleGuide = loadOrDefault("prompts/common/style-guide.st", "");
        if (styleGuide.isBlank()) {
            return ragPrompt;
        }
        return ragPrompt + "\n" + styleGuide;
    }

    public String buildUserPrompt(String userMessage, String context) {
        String template = loadOrDefault("prompts/rag/rag-user-input.st", DEFAULT_USER_PROMPT);
        return template
                .replace("{{userMessage}}", userMessage == null ? "" : userMessage)
                .replace("{{context}}", context == null ? "" : context);
    }

    private String loadOrDefault(String classPath, String fallback) {
        String normalizedPath = Objects.requireNonNull(classPath, "classPath");
        String cached = cache.get(normalizedPath);
        if (cached != null) {
            return cached;
        }

        ClassPathResource resource = new ClassPathResource(normalizedPath);
        String resolved = fallback;
        if (resource.exists()) {
            try {
                byte[] bytes = resource.getInputStream().readAllBytes();
                resolved = new String(bytes, StandardCharsets.UTF_8).trim();
            } catch (IOException ex) {
                resolved = fallback;
            }
        }

        cache.put(normalizedPath, resolved);
        return resolved;
    }
}
