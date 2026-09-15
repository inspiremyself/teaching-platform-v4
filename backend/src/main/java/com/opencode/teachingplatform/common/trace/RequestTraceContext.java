package com.opencode.teachingplatform.common.trace;

public final class RequestTraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();

    private RequestTraceContext() {
    }

    public static void set(String traceId, String clientIp) {
        TRACE_ID.set(traceId);
        CLIENT_IP.set(clientIp);
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static String getClientIp() {
        return CLIENT_IP.get();
    }

    public static void clear() {
        TRACE_ID.remove();
        CLIENT_IP.remove();
    }
}
