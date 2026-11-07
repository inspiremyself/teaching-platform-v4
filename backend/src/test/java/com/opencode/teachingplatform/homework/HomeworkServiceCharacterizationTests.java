package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.assessment.service.QuestionSnapshotFactory;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.util.ActivityStatusParser;
import com.opencode.teachingplatform.homework.service.HomeworkService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 3 FR-7 搬迁前的作业域行为基线。
 */
class HomeworkServiceCharacterizationTests {

    @Test
    void publicApiSurfaceIsUnchanged() {
        Set<String> expected = Set.of(
                "listTeacherHomeworks", "createHomework", "updateHomework",
                "changeHomeworkStatus", "releaseScores", "listStudentHomeworks",
                "getStudentHomeworkDetail", "listTeacherSubmissions", "getTeacherSubmissionDetail",
                "getTeacherHomeworkDetail", "addQuestionFromBank", "addInlineQuestion",
                "updateQuestion", "deleteQuestion", "submitHomework", "saveHomeworkDraft",
                "gradeSubmission", "returnHomeworkSubmission", "gradeAnswer");
        assertEquals(expected, Arrays.stream(HomeworkService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet()));
    }

    @Test
    void storedQuestionSnapshotKeepsAllFields() {
        QuestionSnapshot snapshot = QuestionSnapshotFactory.fromStored(
                11L, "INLINE", "SINGLE", "题干",
                2, 5.0, "[]", "\"A\"", "{}", false);
        assertEquals(11L, snapshot.questionBankId());
        assertEquals("INLINE", snapshot.sourceType());
        assertEquals("SINGLE", snapshot.questionType());
        assertEquals("题干", snapshot.stem());
        assertEquals(2, snapshot.sortOrder());
        assertEquals(5.0, snapshot.score());
        assertEquals("[]", snapshot.optionsJson());
        assertEquals("\"A\"", snapshot.answerJson());
        assertEquals("{}", snapshot.scoringConfigJson());
        assertEquals(Boolean.FALSE, snapshot.reusableFromBank());
    }

    @Test
    void activityStatusParsingKeepsDraftFallbackAndDomainLabel() {
        assertEquals(ActivityStatus.DRAFT, ActivityStatusParser.parse(null, "作业状态"));
        assertEquals(ActivityStatus.CLOSED, ActivityStatusParser.parse("CLOSED", "作业状态"));
    }
}
