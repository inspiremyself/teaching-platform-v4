package com.opencode.teachingplatform.assessment;

import com.opencode.teachingplatform.assessment.model.GradingResultView;
import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.assessment.service.GradingAdapter;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.strategy.FillBlankScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.MultipleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SingleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SubjectiveRecommendationStrategy;
import com.opencode.teachingplatform.grading.strategy.TrueFalseScoringStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingAdapterTests {

    private final GradingAdapter gradingAdapter = new GradingAdapter(
            new ScoringEngine(List.of(
                    new SingleChoiceScoringStrategy(),
                    new MultipleChoiceScoringStrategy(),
                    new TrueFalseScoringStrategy(),
                    new FillBlankScoringStrategy(),
                    new SubjectiveRecommendationStrategy()
            ))
    );

    @Test
    void shouldMapObjectiveSnapshotAndAnswerPayloadIntoGradingResultView() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                12L,
                "BANK",
                "TRUE_FALSE",
                "Java is statically typed.",
                1,
                5.0,
                null,
                "{\"correct\":true}",
                null,
                true
        );
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                21L,
                "TRUE_FALSE",
                null,
                "{\"selectedOptions\":[\"true\"]}",
                List.of("true"),
                null
        );

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(5.0, result.autoScore());
        assertNull(result.suggestedScore());
        assertEquals(5.0, result.finalScore());
        assertEquals("AUTO", result.scoreSource());
        assertTrue(result.correct());
        assertEquals("{\"selectedOptions\":[\"true\"]}", result.normalizedAnswerJson());
    }

    @Test
    void shouldScoreSingleChoiceFromArrayAnswerJson() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "SINGLE", "单选题", 1, 5D,
                "[{\"key\":\"A\",\"content\":\"正确\"},{\"key\":\"B\",\"content\":\"错误\"}]",
                "[\"A\"]", null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "SINGLE", null, null, List.of("A"), null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(5D, result.autoScore());
        assertEquals(5D, result.finalScore());
        assertTrue(result.correct());
    }

    @Test
    void shouldNormalizeJudgeAnswerAliases() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "JUDGE", "判断题", 1, 5D, null, "[\"true\"]", null, true);

        GradingResultView upperCaseAnswer = gradingAdapter.grade(snapshot,
                new QuestionAnswerPayload(1L, "JUDGE", null, null, List.of("T"), null));
        GradingResultView lowerCaseAnswer = gradingAdapter.grade(snapshot,
                new QuestionAnswerPayload(2L, "JUDGE", null, null, List.of("true"), null));

        assertEquals(5D, upperCaseAnswer.autoScore());
        assertEquals(5D, lowerCaseAnswer.autoScore());
    }

    @Test
    void shouldReadMultipleChoiceAnswersEvenWhenScoringConfigPresent() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "MULTI", "多选题", 1, 10D, null,
                "[\"A\",\"B\"]", "{\"mode\":\"FULL_MATCH\"}", true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "MULTI", null, null, List.of("A", "B"), null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(10D, result.autoScore());
        assertTrue(result.correct());
    }

    @Test
    void shouldDeriveFillBlankScoresFromArrayAnswerJson() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "FILL", "填空题", 1, 10D, null,
                "[{\"index\":1,\"answers\":[\"x\"]},{\"index\":2,\"answers\":[\"y\"]},{\"index\":3,\"answers\":[\"z\"]}]",
                null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "FILL", null, "[\"x\",\"y\",\"z\"]", null, null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(10D, result.autoScore());
        assertTrue(result.judgeDetail().contains("blank 1: matched"));
        assertTrue(result.judgeDetail().contains("blank 3: matched"));
    }

    @Test
    void shouldScoreMultiBlankFillFromObjectAnswerJson() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "FILL", "填空题", 1, 10D, null,
                "[{\"index\":1,\"answers\":[\"x\"]},{\"index\":2,\"answers\":[\"y\"]},{\"index\":3,\"answers\":[\"z\"]}]",
                null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "FILL", null,
                "{\"kind\":\"fill\",\"blanks\":[{\"index\":1,\"answer\":\"x\"},{\"index\":2,\"answer\":\"y\"},{\"index\":3,\"answer\":\"z\"}]}",
                null, null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(10D, result.autoScore());
        assertTrue(result.judgeDetail().contains("blank 1: matched"));
        assertTrue(result.judgeDetail().contains("blank 3: matched"));
    }

    @Test
    void shouldKeepBlankPlaceholderForEmptyObjectAnswer() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "FILL", "填空题", 1, 10D, null,
                "[{\"index\":1,\"answers\":[\"x\"]},{\"index\":2,\"answers\":[\"y\"]},{\"index\":3,\"answers\":[\"z\"]}]",
                null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "FILL", null,
                "{\"kind\":\"fill\",\"blanks\":[{\"index\":1,\"answer\":\"\"},{\"index\":2,\"answer\":\"y\"},{\"index\":3,\"answer\":\"z\"}]}",
                null, null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        // 空答案必须保留占位：第 2、3 空按位置匹配，不得因过滤空串而左移
        assertEquals(6.67D, result.autoScore());
        assertTrue(result.judgeDetail().contains("blank 1: mismatch"));
        assertTrue(result.judgeDetail().contains("blank 2: matched"));
        assertTrue(result.judgeDetail().contains("blank 3: matched"));
    }

    @Test
    void shouldSortObjectAnswersByIndexAndIgnoreInvalidIndex() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "FILL", "填空题", 1, 10D, null,
                "[{\"index\":1,\"answers\":[\"x\"]},{\"index\":2,\"answers\":[\"y\"]}]",
                null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "FILL", null,
                "{\"kind\":\" Fill \",\"blanks\":[{\"index\":2,\"answer\":\"y\"},{\"index\":0,\"answer\":\"ignored\"},{\"index\":1,\"answer\":\"x\"}]}",
                null, null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(10D, result.autoScore());
        assertTrue(result.judgeDetail().contains("blank 1: matched"));
        assertTrue(result.judgeDetail().contains("blank 2: matched"));
    }

    @Test
    void shouldFallbackToAnswerTextWhenObjectKindIsNotFill() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "FILL", "填空题", 1, 5D, null,
                "[{\"index\":1,\"answers\":[\"x\"]}]", null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "FILL", "x",
                "{\"kind\":\"text\",\"blanks\":[{\"index\":1,\"answer\":\"x\"}]}",
                null, null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(5D, result.autoScore());
        assertTrue(result.correct());
    }

    @Test
    void shouldReturnZeroWhenExpectedAnswerIsBlank() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null, "BANK", "SINGLE", "空答案题", 1, 5D,
                "[{\"key\":\"A\",\"content\":\"正确\"},{\"key\":\"B\",\"content\":\"错误\"}]",
                "[]", null, true);
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                1L, "SINGLE", null, null, List.of("A"), null);

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertEquals(0D, result.autoScore());
        assertEquals(false, result.correct());
    }

    @Test
    void shouldReturnRecommendationForSubjectiveSnapshotWithoutAutoFinalizing() {
        QuestionSnapshot snapshot = new QuestionSnapshot(
                null,
                "INLINE",
                "SUBJECTIVE",
                "Explain why strategy pattern improves reuse.",
                2,
                10.0,
                null,
                null,
                "{\"keywords\":[{\"term\":\"strategy\",\"weight\":6},{\"term\":\"reuse\",\"weight\":4}],\"minLength\":6}",
                false
        );
        QuestionAnswerPayload payload = new QuestionAnswerPayload(
                22L,
                "SUBJECTIVE",
                "strategy improves reuse across implementations",
                null,
                List.of(),
                null
        );

        GradingResultView result = gradingAdapter.grade(snapshot, payload);

        assertNull(result.autoScore());
        assertEquals(10.0, result.suggestedScore());
        assertNull(result.finalScore());
        assertEquals("RECOMMENDED", result.scoreSource());
        assertNull(result.correct());
        assertNull(result.normalizedAnswerJson());
    }
}
