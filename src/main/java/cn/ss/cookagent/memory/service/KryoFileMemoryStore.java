package cn.ss.cookagent.memory.service;

import cn.ss.cookagent.memory.model.SessionMessage;
import cn.ss.cookagent.memory.model.UserSession;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KryoFileMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(KryoFileMemoryStore.class);

    private final boolean enabled;
    private final Path storeFilePath;
    private final Object ioLock = new Object();

    public KryoFileMemoryStore(
            @Value("${app.memory.persistence.enabled:true}") boolean enabled,
            @Value("${app.memory.persistence.file-path:target/session-memory/sessions.kryo}") String filePath
    ) {
        this.enabled = enabled;
        this.storeFilePath = Paths.get(filePath).toAbsolutePath();
    }

    @Override
    public Map<String, UserSession> loadSessions() {
        if (!enabled || !Files.exists(storeFilePath)) {
            return Map.of();
        }
        synchronized (ioLock) {
            try (Input input = new Input(Files.newInputStream(storeFilePath))) {
                Kryo kryo = createKryo();
                SessionStoreSnapshot snapshot = kryo.readObject(input, SessionStoreSnapshot.class);
                return toUserSessionMap(snapshot);
            } catch (Exception ex) {
                log.warn("加载 Kryo 会话快照失败，将降级为空会话。path={} msg={}", storeFilePath, ex.getMessage());
                return Map.of();
            }
        }
    }

    @Override
    public void saveSessions(Map<String, UserSession> sessions) {
        if (!enabled) {
            return;
        }
        synchronized (ioLock) {
            try {
                Path parent = storeFilePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                SessionStoreSnapshot snapshot = fromUserSessionMap(sessions);
                try (Output output = new Output(Files.newOutputStream(storeFilePath))) {
                    Kryo kryo = createKryo();
                    kryo.writeObject(output, snapshot);
                }
            } catch (IOException ex) {
                log.warn("写入 Kryo 会话快照失败。path={} msg={}", storeFilePath, ex.getMessage());
            }
        }
    }

    private Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.register(SessionStoreSnapshot.class);
        kryo.register(SessionSnapshot.class);
        kryo.register(MessageSnapshot.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        return kryo;
    }

    private SessionStoreSnapshot fromUserSessionMap(Map<String, UserSession> sessions) {
        SessionStoreSnapshot snapshot = new SessionStoreSnapshot();
        snapshot.sessions = new ArrayList<>();
        for (UserSession session : sessions.values()) {
            SessionSnapshot item = new SessionSnapshot();
            item.sessionId = session.getSessionId();
            item.userId = session.getUserId();
            item.rollingSummary = session.getRollingSummary();
            item.messages = new ArrayList<>();
            for (SessionMessage message : session.getMessages()) {
                MessageSnapshot msg = new MessageSnapshot();
                msg.role = message.role();
                msg.content = message.content();
                item.messages.add(msg);
            }
            snapshot.sessions.add(item);
        }
        return snapshot;
    }

    private Map<String, UserSession> toUserSessionMap(SessionStoreSnapshot snapshot) {
        if (snapshot == null || snapshot.sessions == null || snapshot.sessions.isEmpty()) {
            return Map.of();
        }
        Map<String, UserSession> restored = new HashMap<>();
        for (SessionSnapshot item : snapshot.sessions) {
            if (item == null || item.sessionId == null || item.sessionId.isBlank()) {
                continue;
            }
            UserSession session = new UserSession(item.sessionId, item.userId == null ? "" : item.userId);
            session.setRollingSummary(item.rollingSummary == null ? "" : item.rollingSummary);
            if (item.messages != null) {
                for (MessageSnapshot msg : item.messages) {
                    if (msg == null) {
                        continue;
                    }
                    session.getMessages().add(new SessionMessage(
                            msg.role == null ? "assistant" : msg.role,
                            msg.content == null ? "" : msg.content
                    ));
                }
            }
            restored.put(session.getSessionId(), session);
        }
        return restored;
    }

    static class SessionStoreSnapshot {
        public List<SessionSnapshot> sessions = new ArrayList<>();
    }

    static class SessionSnapshot {
        public String sessionId;
        public String userId;
        public String rollingSummary;
        public List<MessageSnapshot> messages = new ArrayList<>();
    }

    static class MessageSnapshot {
        public String role;
        public String content;
    }
}
