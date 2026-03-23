package cn.ss.cookagent.memory.service;

import cn.ss.cookagent.common.exception.BizException;
import cn.ss.cookagent.memory.model.SessionMessage;
import cn.ss.cookagent.memory.model.UserSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, UserSession> sessions;
    private final MemoryStore memoryStore;

    public SessionService(ObjectProvider<MemoryStore> memoryStoreProvider) {
        this.memoryStore = memoryStoreProvider.getIfAvailable();
        Map<String, UserSession> restored = memoryStore == null ? Map.of() : memoryStore.loadSessions();
        this.sessions = new ConcurrentHashMap<>(restored);
    }

    public UserSession createSession(String userId) {
        String sessionId = "s_" + UUID.randomUUID();
        UserSession session = new UserSession(sessionId, userId);
        sessions.put(sessionId, session);
        persistSnapshot();
        return session;
    }

    public UserSession getSession(String sessionId) {
        UserSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BizException("SESSION_NOT_FOUND", "会话不存在");
        }
        return session;
    }

    public UserSession getOrCreateSession(String sessionId, String userId) {
        UserSession session = sessions.get(sessionId);
        if (session != null) {
            return session;
        }
        UserSession created = new UserSession(sessionId, userId);
        sessions.put(sessionId, created);
        persistSnapshot();
        return created;
    }

    public void appendUserMessage(String sessionId, String content) {
        UserSession session = getSession(sessionId);
        session.getMessages().add(new SessionMessage("user", content));
        session.setRollingSummary(buildSummary(session.getMessages()));
        persistSnapshot();
    }

    public void appendAssistantMessage(String sessionId, String content) {
        UserSession session = getSession(sessionId);
        session.getMessages().add(new SessionMessage("assistant", content));
        session.setRollingSummary(buildSummary(session.getMessages()));
        persistSnapshot();
    }

    private void persistSnapshot() {
        if (memoryStore == null) {
            return;
        }
        memoryStore.saveSessions(sessions);
    }

    private String buildSummary(List<SessionMessage> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        int from = Math.max(0, messages.size() - 4);
        List<SessionMessage> recent = new ArrayList<>(messages.subList(from, messages.size()));
        return recent.stream()
                .map(message -> message.role() + ":" + message.content())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }
}
