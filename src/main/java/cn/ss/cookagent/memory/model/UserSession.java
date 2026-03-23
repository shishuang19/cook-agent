package cn.ss.cookagent.memory.model;

import java.util.ArrayList;
import java.util.List;

public class UserSession {

    private final String sessionId;
    private final String userId;
    private String rollingSummary;
    private final List<SessionMessage> messages;

    public UserSession(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.rollingSummary = "";
        this.messages = new ArrayList<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRollingSummary() {
        return rollingSummary;
    }

    public void setRollingSummary(String rollingSummary) {
        this.rollingSummary = rollingSummary;
    }

    public List<SessionMessage> getMessages() {
        return messages;
    }
}