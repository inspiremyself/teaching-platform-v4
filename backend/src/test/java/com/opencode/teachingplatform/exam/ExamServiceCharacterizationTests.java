package com.opencode.teachingplatform.exam;

import com.opencode.teachingplatform.exam.service.ExamService;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 3 FR-7 搬迁前的考试域行为基线。
 */
class ExamServiceCharacterizationTests {

    @Test
    void publicApiSurfaceIsUnchanged() {
        Set<String> expected = Set.of(
                "listExams", "createExam", "updateExam", "getExamDetail", "changeStatus",
                "releaseScores", "startExam", "submitExam", "finalizeExamSubmission",
                "saveDraft", "getExamResults", "getSubmissionDetail", "gradeSubmission",
                "confirmAnswer");
        assertEquals(expected, Arrays.stream(ExamService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet()));
    }

    @Test
    void scoreReleaseViewIsUnchanged() {
        Map<String, Object> view = ScoreReleaseSupport.releasedView("MANUAL_RELEASE");
        assertEquals(2, view.size());
        assertEquals(Boolean.TRUE, view.get("released"));
        assertEquals("MANUAL_RELEASE", view.get("scoreVisibilityMode"));
    }
}
