package cn.ss.cookagent.api.response;

import java.util.List;

public record SessionDetailResponse(
        String sessionId,
        String rollingSummary,
        List<MessageItem> messages
) {
    public record MessageItem(String role, String content) {
    }
}
