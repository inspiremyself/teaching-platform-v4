package com.opencode.teachingplatform.assessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 题目内容语义校验器。
 *
 * <p>题库、作业内联题、实验题项复制到题库三条写入链路共用同一套内容契约，
 * 因此把题型、题干、选项、答案、评分参数的校验收敛到这里。
 * 分值归属组卷（快照），题库内容不含分值，故本类不做分值校验；
 * 本类只做内容语义校验，不处理权限、状态与编码唯一性。</p>
 */
@Component
public class QuestionContentValidator {

    private static final String SINGLE = "SINGLE";
    private static final String MULTI = "MULTI";
    private static final String JUDGE = "JUDGE";
    private static final String FILL = "FILL";
    private static final String SHORT = "SHORT";

    private final ObjectMapper objectMapper;

    public QuestionContentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验一条题目的内容是否完整、是否与题型匹配。
     *
     * @param type              题型，标准值为 SINGLE / MULTI / JUDGE / FILL / SHORT
     * @param stem              题干
     * @param optionsJson       选项 JSON，允许为空（非选择题）
     * @param answerJson        答案 JSON，统一为数组形态
     * @param scoringConfigJson 评分参数 JSON，简答题可用 keywords 代替参考答案
     */
    public void validate(String type,
                         String stem,
                         String optionsJson,
                         String answerJson,
                         String scoringConfigJson) {
        String normalizedType = normalizeType(type);
        validateCommon(normalizedType, type, stem);
        validateOptionsSyntax(optionsJson);
        Object answer = readAnswer(answerJson);
        Map<String, Object> scoringConfig = readScoringConfig(scoringConfigJson);
        switch (normalizedType) {
            case SINGLE -> validateSingle(optionsJson, answer);
            case MULTI -> validateMultiple(optionsJson, answer);
            case JUDGE -> validateJudge(answer);
            case FILL -> validateFill(answer);
            case SHORT -> validateShort(answer, scoringConfig);
            default -> {
                // normalizeType 已拦截非法题型，这里不可达
            }
        }
    }

    private void validateCommon(String normalizedType, String rawType, String stem) {
        if (normalizedType == null) {
            throw new BusinessException(40000, "不支持的题型：" + (rawType == null ? "" : rawType));
        }
        if (stem == null || stem.trim().isEmpty()) {
            throw new BusinessException(40000, "题干不能为空");
        }
    }

    /**
     * 选项 JSON 允许为空（非选择题），但一旦提供就必须是合法 JSON 数组。
     */
    private void validateOptionsSyntax(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return;
        }
        try {
            Object parsed = objectMapper.readValue(optionsJson, new TypeReference<Object>() { });
            if (!(parsed instanceof List<?>)) {
                throw new BusinessException(40000, "题目选项 JSON 格式不正确");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40000, "题目选项 JSON 格式不正确");
        }
    }

    private void validateSingle(String optionsJson, Object answer) {
        List<String> optionKeys = QuestionContentSupport.optionKeys(optionsJson);
        if (optionKeys.size() < 2) {
            throw new BusinessException(40000, "单选题至少需要 2 个选项");
        }
        // §4.3：单选答案数组长度必须为 1，多答案同样视为未正确设置
        List<String> answers = answerList(answer).stream().filter(item -> !item.isBlank()).toList();
        if (answers.size() != 1) {
            throw new BusinessException(40000, "请设置单选题正确答案");
        }
        String answerKey = answers.getFirst();
        if (!optionKeys.contains(answerKey.toUpperCase(Locale.ROOT))) {
            throw new BusinessException(40000, "单选答案必须是选项之一");
        }
    }

    private void validateMultiple(String optionsJson, Object answer) {
        List<String> optionKeys = QuestionContentSupport.optionKeys(optionsJson);
        if (optionKeys.size() < 2) {
            throw new BusinessException(40000, "多选题至少需要 2 个选项");
        }
        List<String> answers = answerList(answer).stream().filter(item -> !item.isBlank()).toList();
        if (answers.isEmpty()) {
            throw new BusinessException(40000, "请至少选择一个多选题正确答案");
        }
        boolean allMatch = answers.stream()
                .allMatch(item -> optionKeys.contains(item.toUpperCase(Locale.ROOT)));
        if (!allMatch) {
            throw new BusinessException(40000, "多选答案必须全部来自选项");
        }
    }

    private void validateJudge(Object answer) {
        String answerKey = answerList(answer).stream()
                .map(this::normalizeJudge)
                .filter(item -> !item.isBlank())
                .findFirst()
                .orElse(null);
        if (answerKey == null || (!"t".equals(answerKey) && !"f".equals(answerKey))) {
            throw new BusinessException(40000, "判断题答案必须为 T 或 F");
        }
    }

    private void validateFill(Object answer) {
        if (!(answer instanceof List<?> blanks) || blanks.isEmpty()) {
            throw new BusinessException(40000, "填空题至少需要 1 个空");
        }
        int position = 1;
        for (Object item : blanks) {
            if (!(item instanceof Map<?, ?> blank)) {
                throw new BusinessException(40000, "题目答案 JSON 格式不正确");
            }
            Object index = blank.get("index");
            if (!(index instanceof Number number) || number.intValue() <= 0) {
                throw new BusinessException(40000, "题目答案 JSON 格式不正确");
            }
            List<String> answers = answerList(blank.get("answers")).stream()
                    .filter(value -> !value.isBlank())
                    .toList();
            if (answers.isEmpty()) {
                throw new BusinessException(40000, "第 " + position + " 个空至少需要 1 个答案");
            }
            position++;
        }
    }

    private void validateShort(Object answer, Map<String, Object> scoringConfig) {
        boolean hasReferenceAnswer = answerList(answer).stream().anyMatch(item -> !item.isBlank());
        boolean hasKeywords = scoringConfig.get("keywords") instanceof List<?> keywords && !keywords.isEmpty();
        if (!hasReferenceAnswer && !hasKeywords) {
            throw new BusinessException(40000, "简答题参考答案或评分要点不能为空");
        }
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case SINGLE, "SINGLE_CHOICE" -> SINGLE;
            case MULTI, "MULTIPLE", "MULTIPLE_CHOICE" -> MULTI;
            case JUDGE, "TRUE_FALSE" -> JUDGE;
            case FILL, "FILL_BLANK" -> FILL;
            case SHORT, "TEXT", "SUBJECTIVE" -> SHORT;
            default -> null;
        };
    }

    private Object readAnswer(String answerJson) {
        if (answerJson == null || answerJson.isBlank()) {
            throw new BusinessException(40000, "题目答案 JSON 格式不正确");
        }
        try {
            return objectMapper.readValue(answerJson, new TypeReference<Object>() { });
        } catch (Exception ex) {
            throw new BusinessException(40000, "题目答案 JSON 格式不正确");
        }
    }

    private Map<String, Object> readScoringConfig(String scoringConfigJson) {
        if (scoringConfigJson == null || scoringConfigJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    scoringConfigJson,
                    new TypeReference<LinkedHashMap<String, Object>>() { }
            );
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> answerList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(item -> String.valueOf(item).trim())
                    .toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value).trim());
    }

    private String normalizeJudge(String value) {
        String token = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (token) {
            case "t", "true", "1", "正确" -> "t";
            case "f", "false", "0", "错误" -> "f";
            default -> token;
        };
    }
}
