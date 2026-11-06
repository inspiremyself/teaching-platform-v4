package com.opencode.teachingplatform.grading.strategy;

import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MultipleChoiceScoringStrategy extends AbstractObjectiveScoringSupport implements QuestionScoringStrategy {

    @Override
    public boolean supports(QuestionType type) {
        return type == QuestionType.MULTIPLE_CHOICE;
    }

    @Override
    public ScoringResult evaluate(ScoringContext context) {
        String mode = normalizeToken(context.scoringConfig().getOrDefault("mode", "FULL_MATCH"));
        Set<String> expected = normalizeSet(context.scoringConfig().get("correctAnswers"));
        Set<String> actual = normalizeSet(context.studentAnswer());
        // 预期答案缺失时直接判 0，避免 FULL_MATCH 下“空集等于空集”得分
        double score = expected.isEmpty() ? 0D : switch (mode) {
            case "average_partial" -> averagePartialScore(context, expected, actual);
            default -> expected.equals(actual) ? scoreOf(context) : 0D;
        };
        return new ScoringResult(score, null, score, ScoreSource.AUTO,
                "multiple choice mode=" + mode + ", expected=" + expected + ", actual=" + actual,
                false, true);
    }

    private double averagePartialScore(ScoringContext context, Set<String> expected, Set<String> actual) {
        if (expected.isEmpty()) {
            return 0D;
        }
        long matched = actual.stream().filter(expected::contains).count();
        long wrong = actual.stream().filter(item -> !expected.contains(item)).count();
        if (wrong > 0 || matched == 0) {
            return 0D;
        }
        return round((scoreOf(context) / expected.size()) * matched);
    }
}
