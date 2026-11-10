package com.opencode.teachingplatform.question.service;

import com.opencode.teachingplatform.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 题库编码硬规则（PRD H1–H4）的唯一校验入口。
 * 系统自动生成的 Q-/HW/LAB 编码不进入本类；编辑同值豁免只跳过 H2 / H4。
 */
public final class QuestionCodeRules {

    public static final int MAX_LENGTH = 64;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Z0-9_-]+$");
    private static final Pattern HOMEWORK_AUTO = Pattern.compile("^HW\\d+$");
    private static final Pattern LAB_AUTO = Pattern.compile("^LAB\\d+S\\d+$");

    private QuestionCodeRules() {
    }

    public static void validateUserSubmitted(String normalized, boolean skipFormatRules) {
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessException(40000, "题目编码不能为空");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new BusinessException(40000, "题目编码长度不能超过 64 个字符");
        }
        if (skipFormatRules) {
            return;
        }
        if (!ALLOWED.matcher(normalized).matches()) {
            throw new BusinessException(40000, "题目编码只能包含 A-Z、0-9、- 和 _");
        }
        if (HOMEWORK_AUTO.matcher(normalized).matches() || LAB_AUTO.matcher(normalized).matches()) {
            throw new BusinessException(40000, "题目编码不能使用系统自动编码格式（HW{数字} / LAB{数字}S{数字}）");
        }
    }
}
