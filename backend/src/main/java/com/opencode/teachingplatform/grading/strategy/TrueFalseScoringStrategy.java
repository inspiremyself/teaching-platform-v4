package com.opencode.teachingplatform.grading.strategy;

import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import org.springframework.stereotype.Component;

@Component
public class TrueFalseScoringStrategy extends AbstractObjectiveScoringSupport implements QuestionScoringStrategy {

    @Override
    public boolean supports(QuestionType type) {
        return type == QuestionType.TRUE_FALSE;
    }

    @Override
    public ScoringResult evaluate(ScoringContext context) {
        // 期望答案与学生答案都必须先做别名归一：T/TRUE/1/正确 视为同一答案
        String expected = normalizeJudge(context.scoringConfig().get("correctAnswer"));
        String actual = normalizeJudge(context.studentAnswer());
        double score = !expected.isBlank() && expected.equals(actual) ? scoreOf(context) : 0D;
        return new ScoringResult(score, null, score, ScoreSource.AUTO,
                score > 0 ? "true/false matched" : "true/false mismatch", false, true);
    }

    /**
     * 判断题答案归一：T/TRUE/1/正确 → t，F/FALSE/0/错误 → f。
     */
    private String normalizeJudge(Object value) {
        String token = normalizeToken(value);
        return switch (token) {
            case "t", "true", "1", "正确" -> "t";
            case "f", "false", "0", "错误" -> "f";
            default -> token;
        };
    }
}
