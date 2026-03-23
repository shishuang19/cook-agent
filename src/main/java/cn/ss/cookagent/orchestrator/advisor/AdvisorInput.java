package cn.ss.cookagent.orchestrator.advisor;

public record AdvisorInput(
        String userMessage,
        String context,
        String systemPrompt,
        String userPrompt,
        String model
) {
    public AdvisorInput withUserPrompt(String nextUserPrompt) {
        return new AdvisorInput(userMessage, context, systemPrompt, nextUserPrompt, model);
    }
}
