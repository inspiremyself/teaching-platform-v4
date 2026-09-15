package com.opencode.teachingplatform.common.accesslog;

import com.opencode.teachingplatform.common.config.AppHttpAccessLogProperties;
import org.springframework.stereotype.Service;

@Service
public class HttpAccessLogService {

    private final AppHttpAccessLogProperties properties;
    private final HttpAccessLogAsyncWriter asyncWriter;

    public HttpAccessLogService(AppHttpAccessLogProperties properties, HttpAccessLogAsyncWriter asyncWriter) {
        this.properties = properties;
        this.asyncWriter = asyncWriter;
    }

    public void submit(HttpAccessLogRecord record) {
        if (!properties.isEnabled()) {
            return;
        }
        asyncWriter.writeAsync(record);
    }
}
