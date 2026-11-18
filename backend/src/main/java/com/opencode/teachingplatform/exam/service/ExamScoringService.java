package com.opencode.teachingplatform.exam.service;

import org.springframework.stereotype.Service;

/**
 * 考试域判分辅助：题型客观性判定。
 *
 * <p>客观题的实际判分统一走共享评分引擎（{@code assessment/service/GradingAdapter} +
 * {@code grading/service/ScoringEngine}）；本类只保留 {@link #isObjective(String)}
 * 供批改链路区分「系统自动评分、教师不可改分」的题型。</p>
 */
@Service
public class ExamScoringService {

    public boolean isObjective(String questionType) {
        if (questionType == null) return false;
        String type = questionType.toUpperCase();
        return type.equals("SINGLE") || type.equals("SINGLE_CHOICE")
                || type.equals("MULTI") || type.equals("MULTIPLE") || type.equals("MULTIPLE_CHOICE")
                || type.equals("JUDGE") || type.equals("TRUE_FALSE")
                || type.equals("FILL") || type.equals("FILL_BLANK");
    }
}
