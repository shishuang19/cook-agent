package cn.ss.cookagent.orchestrator.tool;

public interface IntentTool {

    String name();

    boolean supports(String intent);

    ToolExecutionResult execute(String message);
}
