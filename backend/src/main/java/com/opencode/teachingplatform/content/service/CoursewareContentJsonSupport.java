package com.opencode.teachingplatform.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单节正文协议与容量校验：AI 生成与人工编辑保存共用同一份判定。
 *
 * <p>正文唯一真相源是 {@code courseware_section.content_json}，协议固定为 version 1 +
 * paragraphs[].sentences[]；段落样式走白名单 class，图片 / 音频只允许 courseware-assets 相对路径，
 * 单节 UTF-8 字节上限 1MB（恰好等于放行）。</p>
 */
@Component
public class CoursewareContentJsonSupport {

    /** 单节正文 UTF-8 字节上限，恰好等于放行、+1 字节拒绝。 */
    public static final int MAX_CONTENT_BYTES = 1_048_576;

    private static final Set<String> PARAGRAPH_CLASSES = Set.of("text", "tip", "example", "note");

    private final ObjectMapper objectMapper;

    public CoursewareContentJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 校验单节正文；任一条不满足即抛 40000，且调用方不得落库。 */
    public void validate(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            throw new BusinessException(40000, "小节正文不能为空");
        }
        if (contentJson.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(40000, "单节正文超过 1MB 上限");
        }
        JsonNode root = readTree(contentJson);
        if (!root.isObject() || root.path("version").asInt(-1) != 1) {
            throw new BusinessException(40000, "小节正文协议版本必须为 1");
        }
        JsonNode paragraphs = root.path("paragraphs");
        if (!paragraphs.isArray() || paragraphs.isEmpty()) {
            throw new BusinessException(40000, "小节正文至少要有一个段落");
        }
        for (JsonNode paragraph : paragraphs) {
            if (!PARAGRAPH_CLASSES.contains(paragraph.path("class").asText(""))) {
                throw new BusinessException(40000, "段落样式不在白名单内");
            }
            JsonNode sentences = paragraph.path("sentences");
            if (!sentences.isArray() || sentences.isEmpty()) {
                throw new BusinessException(40000, "段落至少要有一句话");
            }
            for (JsonNode sentence : sentences) {
                if (sentence.path("text").asText("").isBlank()) {
                    throw new BusinessException(40000, "句子正文不能为空");
                }
                checkAssetPath(sentence.path("image").asText(""), "图片");
                checkAssetPath(sentence.path("audio").asText(""), "音频");
            }
            JsonNode exercises = paragraph.path("exercises");
            if (!exercises.isArray() || !exercises.isEmpty()) {
                throw new BusinessException(40000, "本期不支持段落练习项");
            }
        }
    }

    private JsonNode readTree(String contentJson) {
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            if (node == null) {
                throw new BusinessException(40000, "小节正文不是合法 JSON");
            }
            return node;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40000, "小节正文不是合法 JSON");
        }
    }

    /** 提取正文里引用的素材相对路径，供发布门禁与 courseware_asset 比对。 */
    public Set<String> assetPaths(String contentJson) {
        Set<String> paths = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            for (JsonNode paragraph : root.path("paragraphs")) {
                for (JsonNode sentence : paragraph.path("sentences")) {
                    addPath(paths, sentence.path("image").asText(""));
                    addPath(paths, sentence.path("audio").asText(""));
                }
            }
        } catch (Exception ex) {
            throw new BusinessException(40000, "小节正文不是合法 JSON");
        }
        return paths;
    }

    private void addPath(Set<String> paths, String path) {
        if (path != null && !path.isBlank()) {
            paths.add(path);
        }
    }

    private void checkAssetPath(String path, String label) {
        if (path == null || path.isBlank()) {
            return;
        }
        if (path.contains("base64,") || path.contains("data:") || !path.startsWith("courseware-assets/")) {
            throw new BusinessException(40000, label + "必须引用 courseware-assets 下的相对路径");
        }
    }
}
