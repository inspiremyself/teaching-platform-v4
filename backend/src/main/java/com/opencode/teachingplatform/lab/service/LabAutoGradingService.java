package com.opencode.teachingplatform.lab.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.lab.entity.LabStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LabAutoGradingService {

    private final ObjectMapper objectMapper;
    private final ScoringEngine scoringEngine;
    private final LabAssessmentAdapter labAssessmentAdapter;

    public LabAutoGradingService(ObjectMapper objectMapper, ScoringEngine scoringEngine) {
        this.objectMapper = objectMapper;
        this.scoringEngine = scoringEngine;
        this.labAssessmentAdapter = new LabAssessmentAdapter(scoringEngine);
    }

    public AutoGradeResult evaluate(LabStep step, String answerText) {
        Map<String, Object> config = resolveScoringConfig(step);
        if (config == null || config.isEmpty()) {
            return new AutoGradeResult(null, null, "TEACHER", "missing rule config", null);
        }

        QuestionType questionType = toQuestionType(step == null ? null : step.getQuestionType());
        if (questionType == null || scoringEngine == null) {
            return fallbackEvaluate(step, answerText, config);
        }

        ScoringResult result = labAssessmentAdapter.evaluate(new ScoringContext(
                questionType,
                scoreOf(step),
                config,
                resolveStudentAnswer(questionType, answerText),
                questionType != QuestionType.SUBJECTIVE
        ));
        String scoreSource = result.scoreSource() == ScoreSource.RECOMMENDED
                ? "SUGGESTED"
                : result.scoreSource().name();
        return new AutoGradeResult(
                result.autoScore(),
                result.recommendedScore(),
                scoreSource,
                result.judgeDetail(),
                asString(config.get("commentTemplate"))
        );
    }

    private AutoGradeResult fallbackEvaluate(LabStep step, String answerText, Map<String, Object> config) {
        String questionType = normalizeQuestionType(step == null ? null : step.getQuestionType());
        return switch (questionType) {
            case "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE" -> evaluateObjective(step, answerText, config);
            case "TEXT" -> evaluateText(step, answerText, config);
            default -> new AutoGradeResult(null, null, "TEACHER", "unsupported question type", null);
        };
    }

    private Map<String, Object> resolveScoringConfig(LabStep step) {
        Map<String, Object> config = readConfig(step == null ? null : step.getScoringConfigJson());
        if (config != null && !config.isEmpty()) {
            normalizeObjectiveConfig(config, toQuestionType(step == null ? null : step.getQuestionType()));
            return config;
        }
        Map<String, Object> legacyConfig = readConfig(step == null ? null : step.getAnswerConfigJson());
        normalizeObjectiveConfig(legacyConfig, toQuestionType(step == null ? null : step.getQuestionType()));
        return legacyConfig;
    }

    private void normalizeObjectiveConfig(Map<String, Object> config, QuestionType questionType) {
        if (config == null || config.isEmpty() || questionType == null) {
            return;
        }
        if ((questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.TRUE_FALSE)
                && !config.containsKey("correctAnswer")
                && config.containsKey("correctAnswers")) {
            Object correctAnswers = config.get("correctAnswers");
            if (correctAnswers instanceof List<?> answers && !answers.isEmpty()) {
                config.put("correctAnswer", answers.getFirst());
            }
        }
        if ((questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.TRUE_FALSE)
                && config.containsKey("correctAnswer")) {
            Object correctAnswer = config.get("correctAnswer");
            if (correctAnswer instanceof Collection<?> collection && !collection.isEmpty()) {
                config.put("correctAnswer", collection.iterator().next());
            }
        }
        if ((questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.TRUE_FALSE)
                && !config.containsKey("correctAnswer")
                && config.containsKey("answer")) {
            Object answer = config.get("answer");
            if (answer instanceof Map<?, ?> answerMap && answerMap.containsKey("correctAnswer")) {
                config.put("correctAnswer", answerMap.get("correctAnswer"));
            }
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE
                && !config.containsKey("correctAnswers")
                && config.containsKey("correctAnswer")) {
            Object correctAnswer = config.get("correctAnswer");
            if (correctAnswer instanceof Collection<?> collection) {
                config.put("correctAnswers", new ArrayList<>(collection));
            } else if (correctAnswer != null) {
                config.put("correctAnswers", List.of(correctAnswer));
            }
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE
                && !config.containsKey("correctAnswers")
                && config.containsKey("answer")) {
            Object answer = config.get("answer");
            if (answer instanceof Map<?, ?> answerMap) {
                if (answerMap.containsKey("correctAnswers")) {
                    config.put("correctAnswers", answerMap.get("correctAnswers"));
                } else if (answerMap.containsKey("correctAnswer")) {
                    config.put("correctAnswers", answerMap.get("correctAnswer"));
                }
            }
        }
    }

    private QuestionType toQuestionType(String value) {
        String normalized = normalizeQuestionType(value);
        return switch (normalized) {
            case "SINGLE_CHOICE" -> QuestionType.SINGLE_CHOICE;
            case "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE" -> QuestionType.TRUE_FALSE;
            case "TEXT", "SUBJECTIVE" -> QuestionType.SUBJECTIVE;
            default -> null;
        };
    }

    private Object resolveStudentAnswer(QuestionType questionType, String answerText) {
        if (isBlank(answerText)) {
            return null;
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE) {
            return new ArrayList<>(parseAnswerTokens(answerText));
        }
        if (questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.TRUE_FALSE) {
            Set<String> tokens = parseAnswerTokens(answerText);
            return tokens.isEmpty() ? null : tokens.iterator().next();
        }
        return answerText;
    }

    private AutoGradeResult evaluateObjective(LabStep step, String answerText, Map<String, Object> config) {
        Set<String> expectedAnswers = normalizedSet(config.get("correctAnswer"));
        if (expectedAnswers.isEmpty()) {
            return new AutoGradeResult(null, null, "TEACHER", "missing correctAnswer config", null);
        }

        Set<String> actualAnswers = parseAnswerTokens(answerText);
        boolean matched = actualAnswers.equals(expectedAnswers);
        double autoScore = matched ? scoreOf(step) : 0D;
        String detail = matched
                ? "exact matched answers: " + String.join(",", expectedAnswers)
                : "exact mismatch, expected=" + String.join(",", expectedAnswers) + ", actual=" + String.join(",", actualAnswers);
        return new AutoGradeResult(autoScore, null, "AUTO", detail, null);
    }

    private AutoGradeResult evaluateText(LabStep step, String answerText, Map<String, Object> config) {
        List<Map<String, Object>> keywords = readKeywords(config.get("keywords"));
        Integer minLength = asInteger(config.get("minLength"));
        String commentTemplate = asString(config.get("commentTemplate"));
        if (keywords.isEmpty() && minLength == null && isBlank(commentTemplate)) {
            return new AutoGradeResult(null, null, "TEACHER", "missing text grading config", null);
        }

        String normalizedAnswer = normalize(answerText);
        List<String> matchedTerms = new ArrayList<>();
        double suggestedScore = 0D;
        for (Map<String, Object> keyword : keywords) {
            String term = normalize(asString(keyword.get("term")));
            Integer weight = asInteger(keyword.get("weight"));
            if (!isBlank(term) && normalizedAnswer.contains(term)) {
                matchedTerms.add(term);
                suggestedScore += weight == null ? 0D : weight;
            }
        }

        int answerLength = normalizedAnswer.length();
        boolean belowMinLength = minLength != null && answerLength < minLength;
        double maxScore = scoreOf(step);
        if (belowMinLength) {
            suggestedScore = Math.min(suggestedScore, maxScore / 2D);
        }
        suggestedScore = Math.min(suggestedScore, maxScore);

        String detail = matchedTerms.isEmpty()
                ? "matched keywords: none"
                : "matched keywords: " + String.join(", ", matchedTerms);
        if (minLength != null) {
            detail += "; length=" + answerLength + "/" + minLength;
        }
        if (belowMinLength) {
            detail += "; minLength limit applied";
        }
        return new AutoGradeResult(null, suggestedScore, "SUGGESTED", detail, isBlank(commentTemplate) ? null : commentTemplate);
    }

    private Map<String, Object> readConfig(String answerConfigJson) {
        if (isBlank(answerConfigJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(answerConfigJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Map<String, Object>> readKeywords(Object rawKeywords) {
        if (!(rawKeywords instanceof Collection<?> collection)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> keywords = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                keywords.add((Map<String, Object>) map);
            }
        }
        return keywords;
    }

    private Set<String> parseAnswerTokens(String answerText) {
        if (isBlank(answerText)) {
            return Collections.emptySet();
        }
        String normalized = answerText.replace('，', ',');
        String[] tokens = normalized.split("\\s*,\\s*|\\s+");
        Set<String> values = new LinkedHashSet<>();
        for (String token : tokens) {
            String item = normalize(token);
            if (!isBlank(item)) {
                values.add(item);
            }
        }
        return values;
    }

    private Set<String> normalizedSet(Object value) {
        if (value instanceof Collection<?> collection) {
            Set<String> result = new LinkedHashSet<>();
            for (Object item : collection) {
                String normalized = normalize(asString(item));
                if (!isBlank(normalized)) {
                    result.add(normalized);
                }
            }
            return result;
        }
        String singleValue = normalize(asString(value));
        return isBlank(singleValue) ? Collections.emptySet() : Set.of(singleValue);
    }

    private double scoreOf(LabStep step) {
        return step == null || step.getStepScore() == null ? 0D : step.getStepScore().doubleValue();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeQuestionType(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record AutoGradeResult(
            Double autoScore,
            Double suggestedScore,
            String scoreSource,
            String autoJudgeDetail,
            String suggestedTeacherComment
    ) {
    }
}
