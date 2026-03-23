package cn.ss.cookagent.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ChatRequest(
        @NotBlank(message = "sessionId 不能为空") String sessionId,
        @NotBlank(message = "userId 不能为空") String userId,
        @NotBlank(message = "message 不能为空") String message,
        Boolean stream,
        Map<String, Object> context
) {
}
