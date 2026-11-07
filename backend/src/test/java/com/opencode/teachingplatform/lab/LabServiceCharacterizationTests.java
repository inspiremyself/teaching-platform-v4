package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.util.ActivityStatusParser;
import com.opencode.teachingplatform.grading.enums.QuestionType;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringContext;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.strategy.QuestionScoringStrategy;
import com.opencode.teachingplatform.lab.service.LabAssessmentAdapter;
import com.opencode.teachingplatform.lab.service.LabService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

/**
 * Phase 3 FR-7 搬迁前的实验域行为基线。
 *
 * <p>service 拆分只允许改内部实现，不允许增删 public 方法或改变解析 / 发布 / 评分结果。</p>
 */
class LabServiceCharacterizationTests {

    @Test
    void publicApiSurfaceIsUnchanged() {
        Set<String> expected = Set.of(
                "listTeacherLabs", "createLab", "copyLab", "updateLab", "changeLabStatus",
                "releaseScores", "getStudentLabDetail", "listTeacherSteps", "listBlankItems",
                "getBlankItemAnswerDistribution", "saveBlankAcceptedAnswers", "createStep",
                "updateStep", "deleteStep", "listTeacherReports", "listTeacherReportQueue",
                "getTeacherReportStats", "getTeacherReportDetail", "getTeacherReportBehavior",
                "getTeacherLabReportView", "getSubmissionOverview", "listStudentLabSubmissions",
                "getLabItemAnswers", "listStudentLabs", "saveAnswer", "uploadAnswerImage",
                "previewAnswerImage", "submitLab", "gradeReport", "confirmStepScore",
                "returnReport");
        assertEquals(expected, publicMethods(LabService.class));
    }

    @Test
    void activityStatusParsingKeepsDraftFallbackAndDomainLabel() {
        assertEquals(ActivityStatus.DRAFT, ActivityStatusParser.parse(null, "实验状态"));
        assertEquals(ActivityStatus.DRAFT, ActivityStatusParser.parse("  ", "实验状态"));
        assertEquals(ActivityStatus.PUBLISHED, ActivityStatusParser.parse("PUBLISHED", "实验状态"));
        assertThrows(RuntimeException.class, () -> ActivityStatusParser.parse("NOT_A_STATUS", "实验状态"));
    }

    @Test
    void scoreReleaseViewIsUnchanged() {
        Map<String, Object> view = ScoreReleaseSupport.releasedView("AFTER_DEADLINE");
        assertEquals(2, view.size());
        assertEquals(Boolean.TRUE, view.get("released"));
        assertEquals("AFTER_DEADLINE", view.get("scoreVisibilityMode"));
    }

    @Test
    void assessmentAdapterDoesNotAlterScoringResult() {
        ScoringResult expected = new ScoringResult(3.0, 3.0, 3.0,
                ScoreSource.RECOMMENDED, "ok", false, true);
        QuestionScoringStrategy strategy = Mockito.mock(QuestionScoringStrategy.class);
        Mockito.when(strategy.supports(any())).thenReturn(true);
        Mockito.when(strategy.evaluate(any())).thenReturn(expected);

        ScoringEngine engine = new ScoringEngine(java.util.List.of(strategy));
        LabAssessmentAdapter adapter = new LabAssessmentAdapter(engine);
        ScoringContext context = new ScoringContext(QuestionType.SINGLE_CHOICE, 3.0,
                Map.of(), "A", true);

        assertSame(expected, adapter.evaluate(context));
    }

    private static Set<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
