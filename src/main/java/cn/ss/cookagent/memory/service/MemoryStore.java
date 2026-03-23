package cn.ss.cookagent.memory.service;

import cn.ss.cookagent.memory.model.UserSession;

import java.util.Map;

public interface MemoryStore {

    Map<String, UserSession> loadSessions();

    void saveSessions(Map<String, UserSession> sessions);
}
