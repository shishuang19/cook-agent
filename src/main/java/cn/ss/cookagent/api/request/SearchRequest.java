package cn.ss.cookagent.api.request;

import java.util.Map;

public record SearchRequest(
        String query,
        Map<String, Object> filters,
        Integer pageNo,
        Integer pageSize,
        String sortBy
) {
}
