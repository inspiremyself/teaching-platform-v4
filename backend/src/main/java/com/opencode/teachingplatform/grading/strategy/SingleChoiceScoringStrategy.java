package com.opencode.teachingplatform.grading.strategy;

import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import org.springframework.stereotype.Component;

@Component
public class SingleChoiceScoringStrategy extends AbstractObjectiveScoringSupport implements QuestionScoringStrategy {

    @Override
    public boolean supports(QuestionType type) {
        return type == QuestionType.SINGLE_CHOICE;
    }

    @Override
    public ScoringResult evaluate(ScoringContext context) {
        String expected = normalizeToken(context.scoringConfig().get("correctAnswer"));
        String actual = normalizeToken(context.studentAnswer());
        // 预期答案缺失时必须判 0：否则空预期与空作答会被误判为“相等”而得分
        double score = !expected.isBlank() && expected.equals(actual) ? scoreOf(context) : 0D;
        return new ScoringResult(score, null, score, ScoreSource.AUTO,
                score > 0 ? "single choice matched" : "single choice mismatch", false, true);
    }
}
