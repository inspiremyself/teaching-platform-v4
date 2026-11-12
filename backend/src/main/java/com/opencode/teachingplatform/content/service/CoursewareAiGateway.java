package com.opencode.teachingplatform.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.config.CoursewareAiProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容网关封装：只用 JDK 21 HttpClient，不引入第三方 SDK。
 *
 * <p>四项参数绑定自 {@code app.courseware.ai.*} 配置键，值经 ${COURSEWARE_AI_*:}
 * 占位符由环境变量注入；缺少 base-url / api-key / model 时 {@link #isConfigured()} 为 false，
 * AI 生成入口直接拒绝并保证不落库。</p>
 */
@Service
public class CoursewareAiGateway {

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public CoursewareAiGateway(ObjectMapper objectMapper, CoursewareAiProperties properties) {
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        this.apiKey = properties.getApiKey() == null ? "" : properties.getApiKey().trim();
        this.model = properties.getModel() == null ? "" : properties.getModel().trim();
        this.timeoutSeconds = properties.getTimeoutSeconds() <= 0 ? 120 : properties.getTimeoutSeconds();
    }

    /** 网关是否具备可用配置；空串即未配置，不提供任何可用密钥。 */
    public boolean isConfigured() {
        return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    /** 同步调用 chat/completions，返回模型输出的原始文本。 */
    public String complete(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new BusinessException(40000, "AI 生成服务未配置，请联系管理员");
        }
        String body = requestBody(systemPrompt, userPrompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(40000, "AI 生成服务返回异常状态");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BusinessException(40000, "AI 生成结果为空");
            }
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(40000, "AI 生成请求被中断");
        } catch (Exception ex) {
            throw new BusinessException(40000, "AI 生成请求失败");
        }
    }

    private String requestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new BusinessException(40000, "AI 生成请求体构造失败");
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        return trimmed;
    }
}
