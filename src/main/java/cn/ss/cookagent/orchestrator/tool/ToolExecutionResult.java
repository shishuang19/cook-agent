package cn.ss.cookagent.orchestrator.tool;

import cn.ss.cookagent.rag.service.SearchService;

import java.util.List;

public record ToolExecutionResult(String toolName, List<SearchService.SearchHit> hits) {
}
