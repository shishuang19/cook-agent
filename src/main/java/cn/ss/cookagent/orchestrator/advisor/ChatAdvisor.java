package cn.ss.cookagent.orchestrator.advisor;

public interface ChatAdvisor {

    default AdvisorInput beforeCall(AdvisorInput input) {
        return input;
    }

    default void afterCall(AdvisorInput input, String response, long elapsedMs) {
    }

    default void onError(AdvisorInput input, Exception ex, long elapsedMs) {
    }
}
