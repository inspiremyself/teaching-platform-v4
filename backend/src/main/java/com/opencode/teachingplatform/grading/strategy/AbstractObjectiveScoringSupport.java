package com.opencode.teachingplatform.grading.strategy;

import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.support.AnswerNormalizationSupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客观题策略公共基座。
 *
 * <p>答案归一化逻辑统一委托 {@link AnswerNormalizationSupport}（与 GradingAdapter、
 * LabScoringSupport 共用同一实现）；本类只保留策略侧的 {@code protected} 调用形态，
 * 5 个题型策略的调用点因此零改动。</p>
 */
abstract class AbstractObjectiveScoringSupport {

    protected double scoreOf(ScoringContext context) {
        return context.questionScore() == null ? 0D : context.questionScore();
    }

    protected String normalizeToken(Object value) {
        return AnswerNormalizationSupport.normalizeToken(value);
    }

    protected String normalizeText(Object value) {
        return AnswerNormalizationSupport.normalizeText(value);
    }

    protected Set<String> normalizeSet(Object value) {
        return AnswerNormalizationSupport.normalizeSet(value);
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> castKeywordConfigs(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> castBlankConfigs(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    protected List<String> castAnswerList(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    protected int asInt(Object value) {
        return AnswerNormalizationSupport.asInt(value);
    }

    protected double asDouble(Object value) {
        return AnswerNormalizationSupport.asDoubleOrZero(value);
    }

    protected double round(double value) {
        return AnswerNormalizationSupport.round(value);
    }
}
