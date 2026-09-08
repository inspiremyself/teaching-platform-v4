package com.opencode.teachingplatform.lab.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.lab.entity.ExperimentBlankAnswerOverride;
import com.opencode.teachingplatform.lab.entity.LabStep;
import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import com.opencode.teachingplatform.lab.repository.ExperimentBlankAnswerOverrideRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
/**
 * 实验模块的评分适配层。
 *
 * <p>它不直接暴露 HTTP 接口，而是负责把实验步骤与学生答案转换成评分引擎可识别的统一上下文，
 * 相当于实验域对通用评分模块的一层“桥接/适配”。</p>
 */
public class LabScoringSupport {

    private final ObjectMapper objectMapper;
    private final ScoringEngine scoringEngine;
    private final ExperimentBlankAnswerOverrideRepository overrideRepository;

    public LabScoringSupport(ObjectMapper objectMapper,
                             ScoringEngine scoringEngine,
                             ExperimentBlankAnswerOverrideRepository overrideRepository) {
        this.objectMapper = objectMapper;
        this.scoringEngine = scoringEngine;
        this.overrideRepository = overrideRepository;
    }

    /**
     * 对单个实验步骤答案执行评分。
     *
     * <p>执行顺序：读取题目快照与评分配置 → 判断题型 → 解析学生答案 →
     * 对填空题合并教师追加的可接受答案 → 委托 {@link ScoringEngine} 计算结果。</p>
     */
    public ScoringResult score(LabStep step, LabStepAnswer answer) {
        Map<String, Object> snapshot = readJson(firstNonBlank(answer.getQuestionSnapshotJson(), step.getQuestionSnapshotJson()));
        Map<String, Object> scoringConfig = resolveScoringConfig(step, snapshot);
        if (scoringConfig.isEmpty()) {
            return new ScoringResult(null, null, null, ScoreSource.TEACHER, "missing rule config", true, false);
        }

        QuestionType questionType = resolveQuestionType(step, snapshot);
        if (questionType == null) {
            return new ScoringResult(null, null, null, ScoreSource.TEACHER, "unsupported question type", true, false);
        }

        Map<String, List<String>> extraAcceptedAnswers = questionType == QuestionType.FILL_BLANK
                ? loadExtraAcceptedAnswers(step, scoringConfig)
                : Map.of();

        return scoringEngine.evaluate(new ScoringContext(
                questionType,
                resolveQuestionScore(step, snapshot),
                scoringConfig,
                resolveStudentAnswer(questionType, answer.getAnswerText()),
                false,
                extraAcceptedAnswers
        ));
    }

    private Map<String, Object> resolveScoringConfig(LabStep step, Map<String, Object> snapshot) {
        Map<String, Object> directConfig = readJson(step.getScoringConfigJson());
        if (!directConfig.isEmpty()) {
            return directConfig;
        }
        Object snapshotConfig = snapshot.get("scoringConfig");
        if (snapshotConfig instanceof Map<?, ?> map) {
            return castMap(map);
        }
        Map<String, Object> legacyConfig = readJson(step.getAnswerConfigJson());
        return legacyConfig == null ? Map.of() : legacyConfig;
    }

    private QuestionType resolveQuestionType(LabStep step, Map<String, Object> snapshot) {
        String rawType = firstNonBlank(
                asString(snapshot.get("questionType")),
                asString(snapshot.get("legacyQuestionType")),
                step.getQuestionType()
        );
        String normalized = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SINGLE_CHOICE" -> QuestionType.SINGLE_CHOICE;
            case "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE" -> QuestionType.TRUE_FALSE;
            case "FILL_BLANK" -> QuestionType.FILL_BLANK;
            case "TEXT", "SUBJECTIVE", "CODE", "SHORT_ANSWER" -> QuestionType.SUBJECTIVE;
            default -> null;
        };
    }

    private Double resolveQuestionScore(LabStep step, Map<String, Object> snapshot) {
        Double snapshotScore = asDouble(snapshot.get("score"));
        if (snapshotScore != null) {
            return snapshotScore;
        }
        return step.getStepScore() == null ? 0D : step.getStepScore().doubleValue();
    }

    private Object resolveStudentAnswer(QuestionType questionType, String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        return switch (questionType) {
            case MULTIPLE_CHOICE -> new ArrayList<>(parseAnswerTokens(answerText));
            case FILL_BLANK -> parseAnswerList(answerText);
            case SINGLE_CHOICE, TRUE_FALSE -> {
                Set<String> tokens = parseAnswerTokens(answerText);
                yield tokens.isEmpty() ? null : tokens.iterator().next();
            }
            case SUBJECTIVE -> answerText;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> loadExtraAcceptedAnswers(LabStep step, Map<String, Object> scoringConfig) {
        if (step == null || step.getLabId() == null || step.getId() == null) {
            return Map.of();
        }
        Object blanks = scoringConfig.get("blanks");
        int blankCount = blanks instanceof List<?> list ? list.size() : 0;
        if (blankCount == 0) {
            return Map.of();
        }
        Map<String, List<String>> acceptedAnswers = new LinkedHashMap<>();
        for (ExperimentBlankAnswerOverride override : overrideRepository.findByExperimentIdAndExperimentItemIdOrderByIdAsc(step.getLabId(), step.getId())) {
            List<String> blankAnswers = readBlankAnswers(override.getBlankAnswersJson());
            if (blankAnswers.size() != blankCount) {
                continue;
            }
            for (int i = 0; i < blankAnswers.size(); i++) {
                String blankAnswer = blankAnswers.get(i);
                if (blankAnswer == null || blankAnswer.isBlank()) {
                    continue;
                }
                acceptedAnswers.computeIfAbsent("blank-" + (i + 1), ignored -> new ArrayList<>())
                        .add(blankAnswer);
            }
        }
        return acceptedAnswers;
    }

    private List<String> readBlankAnswers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Set<String> parseAnswerTokens(String answerText) {
        Set<String> values = new LinkedHashSet<>();
        for (String token : splitAnswerTokens(answerText)) {
            String item = token == null ? "" : token.trim();
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }

    private List<String> parseAnswerList(String answerText) {
        List<String> values = new ArrayList<>();
        for (String token : splitAnswerTokens(answerText)) {
            String item = token == null ? "" : token.trim();
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }

    private String[] splitAnswerTokens(String answerText) {
        String normalized = answerText.replace('，', ',');
        return normalized.split("\\s*,\\s*|\\s+");
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
