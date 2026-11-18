package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.util.ActivityStatusParser;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import com.opencode.teachingplatform.lab.service.LabService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static Set<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
