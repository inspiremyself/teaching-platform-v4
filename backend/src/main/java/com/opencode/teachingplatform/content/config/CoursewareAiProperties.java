package com.opencode.teachingplatform.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 网关配置对象：键声明在 {@code application.yml} 的 {@code app.courseware.ai}，
 * 值由 ${COURSEWARE_AI_*:} 占位符经环境变量注入，仓库内不提供密钥。
 * 四个空串即「未配置」态，{@code isConfigured()} 由网关侧判定。
 */
@ConfigurationProperties(prefix = "app.courseware.ai")
public class CoursewareAiProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int timeoutSeconds = 120;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
