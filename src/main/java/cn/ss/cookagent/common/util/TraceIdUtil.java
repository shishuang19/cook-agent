package cn.ss.cookagent.common.util;

import java.util.UUID;

public final class TraceIdUtil {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdUtil() {
    }

    public static String ensureTraceId() {
        String existing = TRACE_ID.get();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String created = "t_" + UUID.randomUUID();
        TRACE_ID.set(created);
        return created;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
