package com.opencode.teachingplatform.grading.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评分链路共享的答案归一化工具。
 *
 * <p>只收编各调用方语义完全一致的重复实现：题型策略（{@code AbstractObjectiveScoringSupport}）、
 * 考试/作业适配（{@code GradingAdapter}）与实验评分（{@code LabScoringSupport}）。
 * 方法体逐字符迁自现网实现，判分行为不变；语义存在差异的同名方法（如按 null 缺省的
 * double 解析）不收编进本类，见技术方案 2026-11-17 TD5。</p>
 */
public final class AnswerNormalizationSupport {

    private AnswerNormalizationSupport() {
    }

    public static String normalizeToken(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeText(Object value) {
        return normalizeToken(value).replace('，', ',');
    }

    public static Set<String> normalizeSet(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                String normalized = normalizeToken(item);
                if (!normalized.isBlank()) {
                    result.add(normalized);
                }
            }
            return result;
        }
        String normalized = normalizeToken(value);
        if (!normalized.isBlank()) {
            result.add(normalized);
        }
        return result;
    }

    public static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return 0;
    }

    public static double asDoubleOrZero(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return 0D;
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String[] splitAnswerTokens(String answerText) {
        String normalized = answerText.replace('，', ',');
        return normalized.split("\\s*,\\s*|\\s+");
    }

    public static Set<String> parseTextTokenSet(String answerText) {
        Set<String> values = new LinkedHashSet<>();
        for (String token : splitAnswerTokens(answerText)) {
            String item = token == null ? "" : token.trim();
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }

    public static List<String> parseTextTokenList(String answerText) {
        List<String> values = new ArrayList<>();
        for (String token : splitAnswerTokens(answerText)) {
            String item = token == null ? "" : token.trim();
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }
}
