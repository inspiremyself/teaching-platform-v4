package com.opencode.teachingplatform.common.accesslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HttpAccessLogAsyncWriter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogAsyncWriter.class);

    private final HttpAccessLogRepository httpAccessLogRepository;

    public HttpAccessLogAsyncWriter(HttpAccessLogRepository httpAccessLogRepository) {
        this.httpAccessLogRepository = httpAccessLogRepository;
    }

    @Async("httpAccessLogExecutor")
    @Transactional
    public void writeAsync(HttpAccessLogRecord record) {
        try {
            HttpAccessLog entity = new HttpAccessLog();
            entity.setTraceId(record.traceId());
            entity.setUserId(record.userId());
            entity.setSchoolId(record.schoolId());
            entity.setUserRole(record.userRole());
            entity.setHttpMethod(record.httpMethod());
            entity.setRequestPath(record.requestPath());
            entity.setQueryString(record.queryString());
            entity.setStatusCode(record.statusCode());
            entity.setDurationMs(record.durationMs());
            entity.setClientIp(record.clientIp());
            entity.setUserAgent(record.userAgent());
            httpAccessLogRepository.save(entity);
        } catch (Exception ex) {
            log.error("http access log persist failed, traceId={}", record.traceId(), ex);
        }
    }
}
