package com.opencode.teachingplatform.exam.service;

import com.opencode.teachingplatform.grading.enums.QuestionType;
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
        QuestionType type = QuestionType.fromAlias(questionType);
        return type != null && type.isObjective();
    }
}
