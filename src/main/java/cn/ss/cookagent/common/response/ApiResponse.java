package cn.ss.cookagent.common.response;

import cn.ss.cookagent.common.util.TraceIdUtil;

public record ApiResponse<T>(String code, String message, String traceId, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", TraceIdUtil.ensureTraceId(), data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, TraceIdUtil.ensureTraceId(), null);
    }
}