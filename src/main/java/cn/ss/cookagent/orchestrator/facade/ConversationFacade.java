package cn.ss.cookagent.orchestrator.facade;

import cn.ss.cookagent.api.request.ChatRequest;
import cn.ss.cookagent.api.response.ChatResponse;
import cn.ss.cookagent.common.exception.BizException;
import cn.ss.cookagent.common.response.ApiResponse;
import cn.ss.cookagent.memory.model.UserSession;
import cn.ss.cookagent.memory.service.SessionService;
import cn.ss.cookagent.orchestrator.react.ReActOrchestrator;
import cn.ss.cookagent.rag.service.SearchService;
import cn.ss.cookagent.recipe.domain.Recipe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ConversationFacade {

    private final SessionService sessionService;
    private final ReActOrchestrator reActOrchestrator;
    private final ModelChatService modelChatService;
    private final ObjectMapper objectMapper;

    @Value("${app.memory.chat-window-size:6}")
    private int chatWindowSize;

    @Value("${app.memory.summary-max-chars:500}")
    private int summaryMaxChars;

    public ConversationFacade(
            SessionService sessionService,
            ReActOrchestrator reActOrchestrator,
            ModelChatService modelChatService,
            ObjectMapper objectMapper
    ) {
        this.sessionService = sessionService;
        this.reActOrchestrator = reActOrchestrator;
        this.modelChatService = modelChatService;
        this.objectMapper = objectMapper;
    }

    public ChatResponse handleChat(ChatRequest request) {
        sessionService.getOrCreateSession(request.sessionId(), request.userId());
        sessionService.appendUserMessage(request.sessionId(), request.message());
        UserSession session = sessionService.getSession(request.sessionId());

        String intent = inferIntent(request.message());
        ReActOrchestrator.ReActResult reActResult = reActOrchestrator.execute(intent, request.message());
        List<SearchService.SearchHit> recalledHits = reActResult.hits();

        List<ChatResponse.CitationItem> citations = recalledHits.stream()
                .limit(3)
            .map(hit -> new ChatResponse.CitationItem(
                hit.recipe().recipeId(),
                hit.recipe().name(),
                hit.chunkId(),
                hit.sectionType(),
                hit.score(),
                hit.hitSource()
                ))
                .toList();

                ModelChatService.ModelOutput modelOutput = modelChatService.generateAnswer(
                    request.message(),
                    buildModelContext(session, recalledHits)
                );
                String answer = modelOutput.answer();
        sessionService.appendAssistantMessage(request.sessionId(), answer);

            List<String> followups = modelOutput.followups().isEmpty()
                ? defaultFollowupsByIntent(intent)
                : modelOutput.followups().stream().limit(3).toList();

        return new ChatResponse(
                request.sessionId(),
                intent,
                answer,
                citations,
                followups,
            new ChatResponse.DebugInfo(buildRoute(intent, reActResult.executedToolNames()), recalledHits.size())
        );
    }

    public void streamChat(ChatRequest request, OutputStream outputStream) throws IOException {
        try {
            sessionService.getOrCreateSession(request.sessionId(), request.userId());
            sessionService.appendUserMessage(request.sessionId(), request.message());
            UserSession session = sessionService.getSession(request.sessionId());

            String intent = inferIntent(request.message());
            writeSseEvent(outputStream, "status", Map.of(
                    "stage", "intent",
                    "message", "已完成意图识别"
            ));

            ReActOrchestrator.ReActResult reActResult = reActOrchestrator.execute(intent, request.message());
            List<SearchService.SearchHit> recalledHits = reActResult.hits();
            List<ChatResponse.CitationItem> citations = buildCitations(recalledHits);
            writeSseEvent(outputStream, "status", Map.of(
                    "stage", "retrieval",
                    "message", "检索完成，正在生成答案"
            ));

            StringBuilder rawOutput = new StringBuilder();
            try (Stream<String> tokenStream = modelChatService.streamMarkdownAnswer(
                    request.message(),
                    buildModelContext(session, recalledHits)
            )) {
                tokenStream.forEach(delta -> {
                    if (delta == null || delta.isBlank()) {
                        return;
                    }
                    rawOutput.append(delta);
                    try {
                        writeSseEvent(outputStream, "delta", Map.of("delta", delta));
                    } catch (IOException ex) {
                        throw new StreamWriteRuntimeException(ex);
                    }
                });
            } catch (StreamWriteRuntimeException ex) {
                throw ex.ioException;
            }

            ModelChatService.ModelOutput modelOutput = modelChatService.parseModelOutput(rawOutput.toString());
            String answer = modelOutput.answer();
            sessionService.appendAssistantMessage(request.sessionId(), answer);
            List<String> followups = modelOutput.followups().isEmpty()
                    ? defaultFollowupsByIntent(intent)
                    : modelOutput.followups().stream().limit(3).toList();

            ChatResponse response = new ChatResponse(
                    request.sessionId(),
                    intent,
                    answer,
                    citations,
                    followups,
                    new ChatResponse.DebugInfo(buildRoute(intent, reActResult.executedToolNames()), recalledHits.size())
            );
            writeSseEvent(outputStream, "done", ApiResponse.success(response));
        } catch (BizException ex) {
            writeSseEvent(outputStream, "error", ApiResponse.error(ex.getCode(), ex.getMessage()));
        } catch (Exception ex) {
            writeSseEvent(outputStream, "error", ApiResponse.error("INTERNAL_ERROR", ex.getMessage()));
        }
    }

    private List<ChatResponse.CitationItem> buildCitations(List<SearchService.SearchHit> recalledHits) {
        return recalledHits.stream()
                .limit(3)
                .map(hit -> new ChatResponse.CitationItem(
                        hit.recipe().recipeId(),
                        hit.recipe().name(),
                        hit.chunkId(),
                        hit.sectionType(),
                        hit.score(),
                        hit.hitSource()
                ))
                .toList();
    }

    private void writeSseEvent(OutputStream outputStream, String event, Object payload) throws IOException {
        String data = objectMapper.writeValueAsString(payload);
        String text = "event: " + event + "\n" + "data: " + data + "\n\n";
        outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private static final class StreamWriteRuntimeException extends RuntimeException {
        private final IOException ioException;

        private StreamWriteRuntimeException(IOException ioException) {
            this.ioException = ioException;
        }
    }

    private String inferIntent(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("推荐") || lower.contains("吃什么") || lower.contains("晚饭") || lower.contains("午饭")) {
            return "recommend";
        }
        if (lower.contains("找") || lower.contains("搜索") || lower.contains("检索")) {
            return "search";
        }
        return "qa";
    }

    private String buildRoute(String intent, List<String> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) {
            return intent + ".none";
        }
        if (executedTools.size() == 1) {
            return intent + "." + executedTools.get(0);
        }
        return intent + ".react(" + String.join(">", executedTools) + ")";
    }

    private List<String> defaultFollowupsByIntent(String intent) {
        if ("recommend".equalsIgnoreCase(intent)) {
            return List.of("可以按预算再推荐吗", "能推荐一个20分钟内完成的吗");
        }
        if ("search".equalsIgnoreCase(intent)) {
            return List.of("可以按类别筛选吗", "能再给我更详细的步骤吗");
        }
        return List.of("有没有更快一点的做法", "可以按现有食材再推荐吗");
    }

    private String buildModelContext(UserSession session, List<SearchService.SearchHit> hits) {
        String summary = normalizeSummary(session.getRollingSummary());
        String recentMessages = buildRecentMessagesContext(session);
        if (hits.isEmpty()) {
            return "历史摘要: " + summary + "\n最近对话:\n" + recentMessages + "\n无命中证据";
        }

        String evidence = hits.stream()
                .limit(6)
            .map(hit -> {
                Recipe detailedRecipe = hit.recipe();
                String ingredients = detailedRecipe.ingredients().stream()
                    .limit(8)
                    .map(item -> item.name() + (item.amount() == null || item.amount().isBlank() ? "" : " " + item.amount() + item.unit()))
                    .reduce((a, b) -> a + "、" + b)
                    .orElse("未提供");

                String steps = detailedRecipe.steps().stream()
                    .limit(4)
                    .map(step -> step.stepNo() + "." + step.content())
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("未提供");

                String tips = detailedRecipe.tips().stream()
                    .limit(3)
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("无");

                return "- " + hit.recipe().name()
                    + " | section=" + hit.sectionType()
                    + " | chunkId=" + hit.chunkId()
                    + " | source=" + hit.hitSource()
                    + " | score=" + String.format("%.2f", hit.score())
                    + "\n  食材: " + ingredients
                    + "\n  步骤: " + steps
                    + "\n  提示: " + tips;
            })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return "历史摘要: " + summary + "\n最近对话:\n" + recentMessages + "\n证据列表:\n" + evidence;
    }

    private String normalizeSummary(String rollingSummary) {
        String summary = rollingSummary == null ? "" : rollingSummary;
        int maxChars = Math.max(summaryMaxChars, 80);
        if (summary.length() <= maxChars) {
            return summary;
        }
        return summary.substring(summary.length() - maxChars);
    }

    private String buildRecentMessagesContext(UserSession session) {
        List<cn.ss.cookagent.memory.model.SessionMessage> messages = session.getMessages();
        if (messages.isEmpty()) {
            return "无";
        }
        int window = Math.max(chatWindowSize, 2);
        int from = Math.max(0, messages.size() - window);
        return messages.subList(from, messages.size()).stream()
                .map(message -> message.role() + ": " + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无");
    }

}
