package com.opencode.teachingplatform.grading.support;

import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;

import java.time.OffsetDateTime;

/**
 * 三域成绩可见性判定策略。
 *
 * <p>三域共用 {@link ScoreVisibilityMode} 四模式，但 exam 要求全程 GRADED 前置门槛、
 * lab 的 IMMEDIATE 要求已有总分、homework 无前置门槛——这些差异以配置常量表达，
 * 避免三处 switch 各自演化。</p>
 */
public record ScoreVisibilityPolicy(boolean finalStateGate, boolean immediateRequiresScore) {

    public static final ScoreVisibilityPolicy LAB = new ScoreVisibilityPolicy(false, true);
    public static final ScoreVisibilityPolicy HOMEWORK = new ScoreVisibilityPolicy(false, false);
    public static final ScoreVisibilityPolicy EXAM = new ScoreVisibilityPolicy(true, false);

    public boolean isVisible(ScoreVisibilityMode mode,
                             boolean submissionPresent,
                             boolean finalState,
                             boolean scorePresent,
                             OffsetDateTime deadline,
                             boolean released) {
        if (!submissionPresent) {
            return false;
        }
        if (finalStateGate && !finalState) {
            return false;
        }
        ScoreVisibilityMode effective = mode == null ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM : mode;
        return switch (effective) {
            case IMMEDIATE -> immediateRequiresScore ? scorePresent : true;
            case AFTER_TEACHER_CONFIRM -> finalState;
            case AFTER_DEADLINE -> deadline != null && !OffsetDateTime.now().isBefore(deadline);
            case MANUAL_RELEASE -> released;
        };
    }
}
