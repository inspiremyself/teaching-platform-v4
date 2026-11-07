package com.opencode.teachingplatform.assessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.model.GradingResultView;
import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
/**
 * 通用评分适配器。
 *
 * <p>考试、作业等业务不会直接操作 {@link ScoringEngine}，而是先经过本类，
 * 把题目快照和学生答案整理成统一结构后，再交给评分引擎处理。</p>
 *
 * <p>从设计模式角度看，它承担的是“适配器”角色。</p>
 */
public class GradingAdapter {

    private final ScoringEngine scoringEngine;
    private final ObjectMapper objectMapper;

    public GradingAdapter(ScoringEngine scoringEngine) {
        this(scoringEngine, new ObjectMapper());
    }

    @Autowired
    public GradingAdapter(ObjectProvider<ScoringEngine> scoringEngineProvider,
                          ObjectProvider<ObjectMapper> objectMapperProvider) {
        this(scoringEngineProvider.getIfAvailable(), objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    public GradingAdapter(ScoringEngine scoringEngine, ObjectMapper objectMapper) {
        this.scoringEngine = scoringEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * 对一次题目作答执行评分。
     *
     * <p>运行流程是：确定题型 → 解析并规范化学生答案 → 构建评分上下文 →
     * 调用评分引擎 → 把结果包装成业务层更容易消费的 {@link GradingResultView}。</p>
     */
    public GradingResultView grade(QuestionSnapshot snapshot, QuestionAnswerPayload payload) {
        QuestionType questionType = toGradingQuestionType(payload.questionType() != null ? payload.questionType() : snapshot.questionType());
        if (questionType == null) {
            throw new IllegalArgumentException("Unsupported question type: " + snapshot.questionType());
        }

        Object studentAnswer = resolveStudentAnswer(payload, questionType);
        String normalizedAnswerJson = normalizeAnswerJson(payload, questionType, studentAnswer);

        if (scoringEngine == null) {
            return fallbackResult(questionType, normalizedAnswerJson);
        }

        ScoringResult result = scoringEngine.evaluate(new ScoringContext(
                questionType,
                snapshot.score(),
                resolveScoringConfig(snapshot, questionType),
                studentAnswer,
                questionType != QuestionType.SUBJECTIVE
        ));

        return new GradingResultView(
                result.autoScore(),
                result.recommendedScore(),
                result.finalScore(),
                result.judgeDetail(),
                result.scoreSource().name(),
                toCorrectFlag(questionType, result),
                normalizedAnswerJson
        );
    }

    private GradingResultView fallbackResult(QuestionType questionType, String normalizedAnswerJson) {
        if (questionType == QuestionType.SUBJECTIVE) {
            return new GradingResultView(
                    null,
                    0D,
                    null,
                    "scoring engine unavailable",
                    ScoreSource.RECOMMENDED.name(),
                    null,
                    normalizedAnswerJson
            );
        }
        return new GradingResultView(
                0D,
                null,
                0D,
                "scoring engine unavailable",
                ScoreSource.AUTO.name(),
                false,
                normalizedAnswerJson
        );
    }

    private Boolean toCorrectFlag(QuestionType questionType, ScoringResult result) {
        if (questionType == QuestionType.SUBJECTIVE) {
            return null;
        }
        return result.finalScore() != null && result.finalScore() > 0D;
    }

    /**
     * 解析评分参数：先按 {@code answerJson} 推导答案键，再用 {@code scoringConfigJson} 覆盖评分参数。
     *
     * <p>此前实现只要 {@code scoringConfigJson} 非空就直接返回，导致前端把选项/答案塞进
     * 评分配置后，真正的答案键完全读不到，客观题一律判 0。现在改为“先推导、后覆盖”：
     * 答案键始终来自题目答案，评分参数（模式、关键词、语言等）仍以配置为准。</p>
     */
    private Map<String, Object> resolveScoringConfig(QuestionSnapshot snapshot, QuestionType questionType) {
        Map<String, Object> derived = deriveAnswerConfig(snapshot.answerJson(), questionType, snapshot.score());
        Map<String, Object> configured = readJsonMap(snapshot.scoringConfigJson());
        Map<String, Object> merged = new LinkedHashMap<>(derived);
        merged.putAll(configured);
        return merged;
    }

    /**
     * 从题目答案 JSON 推导评分策略需要的答案键。
     *
     * <p>写入侧约定 {@code answerJson} 为数组形态（单选 {@code ["A"]}、多选 {@code ["A","B"]}、
     * 判断 {@code ["T"]}、填空 {@code [{"index":1,"answers":[...]}]}），
     * 读取侧同时兼容历史对象形态，保证历史题目仍可评分。</p>
     */
    private Map<String, Object> deriveAnswerConfig(String answerJson, QuestionType questionType, Double questionScore) {
        Object answerData = readJsonObject(answerJson);
        if (answerData == null) {
            return Map.of();
        }
        Map<String, Object> derived = new LinkedHashMap<>();
        switch (questionType) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                Object correctAnswer = firstNonNull(
                        firstListValue(answerData),
                        readMapValue(answerData, "correctAnswer"),
                        readMapValue(answerData, "correct"),
                        readMapValue(answerData, "answer"),
                        answerData instanceof Map<?, ?> ? null : answerData
                );
                if (correctAnswer != null) {
                    derived.put("correctAnswer", String.valueOf(correctAnswer));
                }
            }
            case MULTIPLE_CHOICE -> {
                List<String> answers = toStringList(firstNonNull(
                        answerData instanceof List<?> ? answerData : null,
                        readMapValue(answerData, "correctAnswers"),
                        readMapValue(answerData, "correctAnswer"),
                        readMapValue(answerData, "answers")
                ));
                if (!answers.isEmpty()) {
                    derived.put("correctAnswers", answers);
                }
            }
            case FILL_BLANK -> {
                List<Map<String, Object>> blanks = deriveBlanks(answerData, questionScore);
                if (!blanks.isEmpty()) {
                    derived.put("blanks", blanks);
                }
            }
            case SUBJECTIVE -> {
            }
        }
        return derived;
    }

    /**
     * 推导填空题的每题空答案与分值。
     *
     * <p>数组形态 {@code [{"index","answers"}]} 按 index 升序排列；单空分值取
     * {@code floor(总分/空数*100)/100}，最后一空补差，保证各空分值合计等于题目分值。</p>
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deriveBlanks(Object answerData, Double questionScore) {
        if (answerData instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> ordered = list.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> {
                        Map<?, ?> blank = (Map<?, ?>) item;
                        Map<String, Object> copy = new LinkedHashMap<>();
                        copy.put("index", blank.get("index"));
                        copy.put("answers", blank.get("answers"));
                        return copy;
                    })
                    .sorted((left, right) -> Integer.compare(asInt(left.get("index")), asInt(right.get("index"))))
                    .toList();
            if (ordered.isEmpty()) {
                return List.of();
            }
            double total = questionScore == null ? 0D : questionScore;
            int count = ordered.size();
            double perBlank = Math.floor(total / count * 100D) / 100D;
            List<Map<String, Object>> blanks = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Map<String, Object> blank = new LinkedHashMap<>(ordered.get(index));
                double blankScore = index == count - 1
                        ? Math.round((total - perBlank * (count - 1)) * 100D) / 100D
                        : perBlank;
                blank.put("score", blankScore);
                blanks.add(blank);
            }
            return blanks;
        }
        Object legacyBlanks = readMapValue(answerData, "blanks");
        if (legacyBlanks instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> blanks = new java.util.ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    blanks.add(copy);
                }
            }
            return blanks;
        }
        return List.of();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }

    private Object resolveStudentAnswer(QuestionAnswerPayload payload, QuestionType questionType) {
        return switch (questionType) {
            case SINGLE_CHOICE, TRUE_FALSE -> firstNonBlank(
                    firstSelectedOption(payload.selectedOptions()),
                    firstParsedAnswer(payload.answerJson()),
                    payload.answerText()
            );
            case MULTIPLE_CHOICE -> {
                List<String> selected = payload.selectedOptions();
                if (selected != null && !selected.isEmpty()) {
                    yield selected;
                }
                yield parseAnswerList(payload.answerJson());
            }
            case FILL_BLANK -> {
                // 前端多空填空提交的是 {kind:"fill",blanks:[{index,answer}]} 对象形态，
                // 必须先按空位下标解析，否则对象会被 parseAnswerList 丢弃后退化成整串文本。
                List<String> fillAnswers = parseFillBlankAnswers(payload.answerJson());
                if (!fillAnswers.isEmpty()) {
                    yield fillAnswers;
                }
                List<String> answers = parseAnswerList(payload.answerJson());
                if (!answers.isEmpty()) {
                    yield answers;
                }
                yield payload.answerText() == null ? List.of() : List.of(payload.answerText());
            }
            case SUBJECTIVE -> firstNonBlank(payload.answerText(), firstParsedAnswer(payload.answerJson()));
        };
    }

    private String normalizeAnswerJson(QuestionAnswerPayload payload, QuestionType questionType, Object studentAnswer) {
        if (payload.answerJson() != null && !payload.answerJson().isBlank()) {
            return payload.answerJson().trim();
        }
        return switch (questionType) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                if (studentAnswer == null) {
                    yield null;
                }
                yield writeJson(List.of(String.valueOf(studentAnswer)));
            }
            case MULTIPLE_CHOICE, FILL_BLANK -> {
                if (studentAnswer instanceof Collection<?> collection && !collection.isEmpty()) {
                    yield writeJson(collection);
                }
                yield null;
            }
            case SUBJECTIVE -> null;
        };
    }

    private QuestionType toGradingQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return null;
        }
        return switch (questionType.trim().toUpperCase(Locale.ROOT)) {
            case "SINGLE", "SINGLE_CHOICE" -> QuestionType.SINGLE_CHOICE;
            case "MULTI", "MULTIPLE", "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            case "JUDGE", "TRUE_FALSE" -> QuestionType.TRUE_FALSE;
            case "FILL", "FILL_BLANK" -> QuestionType.FILL_BLANK;
            case "SHORT", "TEXT", "SUBJECTIVE" -> QuestionType.SUBJECTIVE;
            default -> null;
        };
    }

    private String firstSelectedOption(List<String> selectedOptions) {
        if (selectedOptions == null || selectedOptions.isEmpty()) {
            return null;
        }
        return selectedOptions.getFirst();
    }

    private String firstParsedAnswer(String answerJson) {
        List<String> parsed = parseAnswerList(answerJson);
        if (!parsed.isEmpty()) {
            return parsed.getFirst();
        }
        return readStringField(answerJson, "answerText");
    }

    /**
     * 解析多空填空的对象形态学生答案：{@code {"kind":"fill","blanks":[{"index":1,"answer":"..."}]}}。
     *
     * <p>解析规则：</p>
     * <ul>
     *   <li>仅当根节点是 Map、{@code kind} 忽略大小写等于 {@code fill}、且 {@code blanks} 是数组时生效；否则返回空列表；</li>
     *   <li>{@code index <= 0} 的元素忽略，{@code answer} 为 null 时按空串占位；</li>
     *   <li>按 {@code index} 升序稳定排序，<b>不按 index 数值补空洞</b>，与前端“排序后按位置对应”的口径一致；</li>
     *   <li><b>保留空串</b>，避免空白答案被过滤后后续空位左移导致错位判分。</li>
     * </ul>
     */
    private List<String> parseFillBlankAnswers(String answerJson) {
        Object parsed = readJsonObject(answerJson);
        if (!(parsed instanceof Map<?, ?> root)) {
            return List.of();
        }
        Object kind = root.get("kind");
        if (kind == null || !"fill".equalsIgnoreCase(String.valueOf(kind).trim())) {
            return List.of();
        }
        if (!(root.get("blanks") instanceof List<?> blanks)) {
            return List.of();
        }
        List<Map.Entry<Integer, String>> pairs = new ArrayList<>();
        for (Object item : blanks) {
            if (!(item instanceof Map<?, ?> blank)) {
                continue;
            }
            int index = asInt(blank.get("index"));
            if (index <= 0) {
                continue;
            }
            Object value = blank.get("answer");
            pairs.add(Map.entry(index, value == null ? "" : String.valueOf(value)));
        }
        if (pairs.isEmpty()) {
            return List.of();
        }
        pairs.sort(Map.Entry.comparingByKey());
        return pairs.stream().map(Map.Entry::getValue).toList();
    }

    private List<String> parseAnswerList(String answerJson) {
        if (answerJson == null || answerJson.isBlank()) {
            return List.of();
        }
        Object parsed = readJsonObject(answerJson);
        if (parsed instanceof List<?> list) {
            return toStringList(list);
        }
        if (parsed instanceof Map<?, ?> map) {
            return toStringList(map.get("selectedOptions"));
        }
        return List.of();
    }

    private String readStringField(String json, String field) {
        Object parsed = readJsonObject(json);
        if (parsed instanceof Map<?, ?> map) {
            Object value = map.get(field);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .toList();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    private Object readJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() { });
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Object readMapValue(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private Object firstListValue(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.getFirst();
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize normalized answer", ex);
        }
    }
}
