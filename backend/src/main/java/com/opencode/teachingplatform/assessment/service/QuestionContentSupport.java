package com.opencode.teachingplatform.assessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 题目内容契约的共享解析工具。
 *
 * <p>题库、作业、实验三条链路在写入 {@code optionsJson} 时统一使用
 * {@code [{"key":"A","content":"..."}]} 结构，但历史数据可能是纯字符串数组
 * （例如 V2 种子中的 {@code ["A.降低复杂度","B.增加偶然性"]}）或使用
 * {@code label} / {@code text} 字段。本类提供唯一的归一入口，避免各处自行反序列化。</p>
 */
public final class QuestionContentSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String OPTION_KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private QuestionContentSupport() {
    }

    /**
     * 把任意形态的选项 JSON 归一为 {@code [{key, content}]} 结构。
     *
     * <p>空串、非法 JSON、非数组结构统一返回空列表，由调用方决定是否视为缺选项。</p>
     */
    public static List<Map<String, Object>> normalizeOptions(String optionsJson) {
        List<Object> rawOptions = parseOptionsArray(optionsJson);
        List<Map<String, Object>> normalized = new ArrayList<>(rawOptions.size());
        for (int index = 0; index < rawOptions.size(); index++) {
            Object item = rawOptions.get(index);
            String fallbackKey = optionKeyAt(index);
            if (item instanceof Map<?, ?> map) {
                String key = firstNonBlank(
                        stringValue(map.get("key")),
                        stringValue(map.get("label")),
                        fallbackKey
                );
                String content = firstNonBlank(
                        stringValue(map.get("content")),
                        stringValue(map.get("label")),
                        stringValue(map.get("text")),
                        stringValue(map.get("value")),
                        ""
                );
                normalized.add(optionMap(key, content));
                continue;
            }
            if (item != null) {
                normalized.add(optionMap(fallbackKey, String.valueOf(item).trim()));
            }
        }
        return normalized;
    }

    /**
     * 按位置生成选项键：A、B ... Z、AA、AB ...，用于历史字符串数组缺少 key 的场景。
     */
    public static String optionKeyAt(int index) {
        if (index < 0) {
            return OPTION_KEY_ALPHABET.substring(0, 1);
        }
        int alphabetSize = OPTION_KEY_ALPHABET.length();
        StringBuilder builder = new StringBuilder();
        int current = index;
        do {
            builder.insert(0, OPTION_KEY_ALPHABET.charAt(current % alphabetSize));
            current = current / alphabetSize - 1;
        } while (current >= 0);
        return builder.toString();
    }

    /**
     * 选项 JSON 中出现的全部选项键（大写），用于答案合法性校验。
     */
    public static List<String> optionKeys(String optionsJson) {
        return normalizeOptions(optionsJson).stream()
                .map(option -> String.valueOf(option.get("key")).trim().toUpperCase(Locale.ROOT))
                .filter(key -> !key.isBlank())
                .toList();
    }

    private static List<Object> parseOptionsArray(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(optionsJson, new TypeReference<Object>() { });
            return parsed instanceof List<?> list ? new ArrayList<>(list) : List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static Map<String, Object> optionMap(String key, String content) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("key", key);
        option.put("content", content == null ? "" : content);
        return option;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
