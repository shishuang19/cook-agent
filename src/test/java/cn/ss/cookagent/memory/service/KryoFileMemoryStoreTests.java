package cn.ss.cookagent.memory.service;

import cn.ss.cookagent.memory.model.SessionMessage;
import cn.ss.cookagent.memory.model.UserSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KryoFileMemoryStoreTests {

    @Test
    void shouldSaveAndLoadSessionSnapshots() throws Exception {
        Path tempDir = Files.createTempDirectory("kryo-store-test");
        Path file = tempDir.resolve("sessions.kryo");

        KryoFileMemoryStore store = new KryoFileMemoryStore(true, file.toString());

        UserSession session = new UserSession("s_test", "u_test");
        session.getMessages().add(new SessionMessage("user", "我想吃鸡肉"));
        session.getMessages().add(new SessionMessage("assistant", "可以试试黄焖鸡"));
        session.setRollingSummary("user:我想吃鸡肉 | assistant:可以试试黄焖鸡");

        store.saveSessions(Map.of(session.getSessionId(), session));
        Map<String, UserSession> restored = store.loadSessions();

        assertThat(restored).containsKey("s_test");
        UserSession loaded = restored.get("s_test");
        assertThat(loaded.getUserId()).isEqualTo("u_test");
        assertThat(loaded.getMessages()).hasSize(2);
        assertThat(loaded.getMessages().get(0).content()).contains("鸡肉");
        assertThat(loaded.getRollingSummary()).contains("黄焖鸡");
    }

    @Test
    void shouldNotPersistWhenDisabled() throws Exception {
        Path tempDir = Files.createTempDirectory("kryo-store-disabled-test");
        Path file = tempDir.resolve("sessions-disabled.kryo");

        KryoFileMemoryStore store = new KryoFileMemoryStore(false, file.toString());

        UserSession session = new UserSession("s_disabled", "u_disabled");
        session.getMessages().add(new SessionMessage("user", "test"));

        store.saveSessions(Map.of(session.getSessionId(), session));

        assertThat(Files.exists(file)).isFalse();
        assertThat(store.loadSessions()).isEmpty();
    }
}
