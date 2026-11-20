package com.opencode.teachingplatform.grading;

import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.grading.support.ScoreVisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreVisibilityPolicyTests {

    @Test
    void nullSubmissionIsNeverVisible() {
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.IMMEDIATE, false, false, false, null, true));
        assertFalse(ScoreVisibilityPolicy.HOMEWORK.isVisible(ScoreVisibilityMode.IMMEDIATE, false, false, false, null, true));
        assertFalse(ScoreVisibilityPolicy.EXAM.isVisible(ScoreVisibilityMode.IMMEDIATE, false, false, false, null, true));
    }

    @Test
    void examRequiresFinalStateInEveryMode() {
        for (ScoreVisibilityMode mode : ScoreVisibilityMode.values()) {
            assertFalse(ScoreVisibilityPolicy.EXAM.isVisible(mode, true, false, true, null, true));
        }
        assertTrue(ScoreVisibilityPolicy.EXAM.isVisible(ScoreVisibilityMode.IMMEDIATE, true, true, false, null, false));
        assertTrue(ScoreVisibilityPolicy.EXAM.isVisible(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM, true, true, false, null, false));
    }

    @Test
    void labImmediateRequiresScore() {
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.IMMEDIATE, true, true, false, null, false));
        assertTrue(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.IMMEDIATE, true, true, true, null, false));
    }

    @Test
    void homeworkImmediateDoesNotRequireScore() {
        assertTrue(ScoreVisibilityPolicy.HOMEWORK.isVisible(ScoreVisibilityMode.IMMEDIATE, true, false, false, null, false));
    }

    @Test
    void afterTeacherConfirmDependsOnFinalState() {
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM, true, false, true, null, true));
        assertTrue(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM, true, true, false, null, false));
        assertFalse(ScoreVisibilityPolicy.HOMEWORK.isVisible(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM, true, false, true, null, true));
        assertTrue(ScoreVisibilityPolicy.HOMEWORK.isVisible(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM, true, true, false, null, false));
    }

    @Test
    void afterDeadlineBoundary() {
        OffsetDateTime now = OffsetDateTime.now();
        assertTrue(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.AFTER_DEADLINE, true, false, false, now.minusSeconds(1), false));
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.AFTER_DEADLINE, true, false, false, now.plusSeconds(60), false));
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.AFTER_DEADLINE, true, false, false, null, false));
        assertTrue(ScoreVisibilityPolicy.EXAM.isVisible(ScoreVisibilityMode.AFTER_DEADLINE, true, true, false, now.minusSeconds(1), false));
    }

    @Test
    void manualReleaseDependsOnReleasedFlag() {
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.MANUAL_RELEASE, true, false, false, null, false));
        assertTrue(ScoreVisibilityPolicy.LAB.isVisible(ScoreVisibilityMode.MANUAL_RELEASE, true, false, false, null, true));
        assertFalse(ScoreVisibilityPolicy.EXAM.isVisible(ScoreVisibilityMode.MANUAL_RELEASE, true, true, false, null, false));
        assertTrue(ScoreVisibilityPolicy.EXAM.isVisible(ScoreVisibilityMode.MANUAL_RELEASE, true, true, false, null, true));
    }

    @Test
    void nullModeFallsBackToAfterTeacherConfirm() {
        assertFalse(ScoreVisibilityPolicy.LAB.isVisible(null, true, false, true, null, true));
        assertTrue(ScoreVisibilityPolicy.LAB.isVisible(null, true, true, false, null, false));
        assertFalse(ScoreVisibilityPolicy.EXAM.isVisible(null, true, false, true, null, true));
        assertTrue(ScoreVisibilityPolicy.EXAM.isVisible(null, true, true, false, null, false));
    }
}
