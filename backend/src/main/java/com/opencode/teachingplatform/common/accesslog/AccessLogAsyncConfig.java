package com.opencode.teachingplatform.common.accesslog;

import com.opencode.teachingplatform.common.config.AppHttpAccessLogProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AppHttpAccessLogProperties.class)
public class AccessLogAsyncConfig {

    @Bean(name = "httpAccessLogExecutor")
    @Profile("test")
    public Executor httpAccessLogExecutorTest() {
        return new SyncTaskExecutor();
    }

    @Bean
    public HttpAccessLogFilter httpAccessLogFilter(
            AppHttpAccessLogProperties properties,
            HttpAccessLogService httpAccessLogService
    ) {
        return new HttpAccessLogFilter(properties, httpAccessLogService);
    }

    @Bean(name = "httpAccessLogExecutor")
    @Profile("!test")
    public Executor httpAccessLogExecutorProd() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("http-access-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
