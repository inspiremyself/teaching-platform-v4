package com.opencode.teachingplatform.analysis;

import com.opencode.teachingplatform.analysis.service.AnalysisService;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AnalysisParticipationTests {

    private static final long TEACHER_ID = 910010L;
    private static final long SCHOOL_ID = 1L;
    private static final long CLASS_ID = 910001L;
    private static final long NO_ACCESS_CLASS_ID = 910002L;
    private static final long FOREIGN_CLASS_ID = 910003L;
    private static final long STUDENT_A = 910011L;
    private static final long STUDENT_B = 910012L;
    private static final long OTHER_TEACHER = 910090L;
    private static final long LAB_ID = 910101L;
    private static final long HOMEWORK_ID = 910102L;
    private static final long EXAM_ID = 910103L;
    private static final long FOREIGN_EXAM_ID = 910104L;
    private static final long FOREIGN_CREATOR_EXAM_ID = 910105L;
    private static final long NO_ACCESS_LAB_ID = 910106L;
    private static final long DRAFT_EXAM_ID = 910107L;
    private static final long LAB_SUBMISSION = 910201L;
    private static final long HOMEWORK_SUBMISSION = 910202L;
    private static final long EXAM_SUBMISSION = 910203L;
    private static final long SAVED_LAB_SUBMISSION = 910204L;
    private static final long IN_PROGRESS_EXAM_SUBMISSION = 910205L;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM experiment_submission WHERE id IN (?, ?)", LAB_SUBMISSION, SAVED_LAB_SUBMISSION);
        jdbcTemplate.update("DELETE FROM homework_submission WHERE id = ?", HOMEWORK_SUBMISSION);
        jdbcTemplate.update("DELETE FROM exam_submission WHERE id IN (?, ?)", EXAM_SUBMISSION, IN_PROGRESS_EXAM_SUBMISSION);
        jdbcTemplate.update("DELETE FROM t_experiment WHERE experiment_id IN (?, ?)", LAB_ID, NO_ACCESS_LAB_ID);
        jdbcTemplate.update("DELETE FROM homework WHERE id = ?", HOMEWORK_ID);
        jdbcTemplate.update("DELETE FROM exam WHERE id IN (?, ?, ?, ?)",
                EXAM_ID, FOREIGN_EXAM_ID, FOREIGN_CREATOR_EXAM_ID, DRAFT_EXAM_ID);
        jdbcTemplate.update("DELETE FROM class_member WHERE class_id IN (?, ?, ?)",
                CLASS_ID, NO_ACCESS_CLASS_ID, FOREIGN_CLASS_ID);
        jdbcTemplate.update("DELETE FROM class_teacher WHERE class_id = ?", CLASS_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?, ?)",
                TEACHER_ID, STUDENT_A, STUDENT_B, OTHER_TEACHER);
        jdbcTemplate.update("DELETE FROM class_room WHERE id IN (?, ?, ?)",
                CLASS_ID, NO_ACCESS_CLASS_ID, FOREIGN_CLASS_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewCountsMatchClassMemberDenominator() {
        seedBaseFixture();

        List<Map<String, Object>> overview = (List<Map<String, Object>>) analysisService
                .getTeacherOverview(teacher()).get("participationOverview");

        assertEquals(3, overview.size());
        for (Map<String, Object> row : overview) {
            assertEquals(2, ((Number) row.get("expectedCount")).intValue());
            assertEquals(1, ((Number) row.get("participatedCount")).intValue());
            assertEquals(1, ((Number) row.get("completedCount")).intValue());
            assertEquals(1, ((Number) row.get("notStartedCount")).intValue());
            assertEquals(50.0, ((Number) row.get("participationRate")).doubleValue());
            assertEquals(50.0, ((Number) row.get("completionRate")).doubleValue());
            List<Map<String, Object>> activities = (List<Map<String, Object>>) row.get("activities");
            assertEquals(1, activities.size());
            assertTrue(activities.get(0).containsKey("businessId"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void draftActivitiesExcludedFromDenominator() {
        seedBaseFixture();
        insertExam(DRAFT_EXAM_ID, CLASS_ID, TEACHER_ID, SCHOOL_ID, "DRAFT");

        List<Map<String, Object>> overview = (List<Map<String, Object>>) analysisService
                .getTeacherOverview(teacher()).get("participationOverview");
        Map<String, Object> examRow = overview.stream()
                .filter(row -> "EXAM".equals(row.get("businessType")))
                .findFirst().orElseThrow();

        assertEquals(1, ((Number) examRow.get("activityCount")).intValue());
        assertEquals(2, ((Number) examRow.get("expectedCount")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroDenominatorReturnsZeroRates() {
        insertClass(CLASS_ID, SCHOOL_ID);
        insertClassTeacher(CLASS_ID, TEACHER_ID);
        insertLab(LAB_ID, CLASS_ID, TEACHER_ID, SCHOOL_ID, "PUBLISHED");

        List<Map<String, Object>> overview = (List<Map<String, Object>>) analysisService
                .getTeacherOverview(teacher()).get("participationOverview");
        for (Map<String, Object> row : overview) {
            assertEquals(0, ((Number) row.get("expectedCount")).intValue());
            assertEquals(0.0, ((Number) row.get("participationRate")).doubleValue());
            assertEquals(0.0, ((Number) row.get("completionRate")).doubleValue());
        }
        Map<String, Object> labRow = overview.stream()
                .filter(row -> "LAB".equals(row.get("businessType")))
                .findFirst().orElseThrow();
        assertEquals(1, ((Number) labRow.get("activityCount")).intValue());
        List<Map<String, Object>> activities = (List<Map<String, Object>>) labRow.get("activities");
        assertEquals(1, activities.size());
        assertEquals(0, ((Number) activities.get(0).get("expectedCount")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void savedAndInProgressCountAsParticipatedNotCompleted() {
        seedBaseFixture();
        jdbcTemplate.update("INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status) "
                + "VALUES (?, ?, ?, 'SAVED')", SAVED_LAB_SUBMISSION, LAB_ID, STUDENT_B);
        jdbcTemplate.update("INSERT INTO exam_submission (id, exam_id, student_id, status) "
                + "VALUES (?, ?, ?, 'IN_PROGRESS')", IN_PROGRESS_EXAM_SUBMISSION, EXAM_ID, STUDENT_B);

        List<Map<String, Object>> overview = (List<Map<String, Object>>) analysisService
                .getTeacherOverview(teacher()).get("participationOverview");
        Map<String, Object> labRow = overview.stream()
                .filter(row -> "LAB".equals(row.get("businessType")))
                .findFirst().orElseThrow();
        Map<String, Object> examRow = overview.stream()
                .filter(row -> "EXAM".equals(row.get("businessType")))
                .findFirst().orElseThrow();

        assertEquals(2, ((Number) labRow.get("participatedCount")).intValue());
        assertEquals(1, ((Number) labRow.get("completedCount")).intValue());
        assertEquals(2, ((Number) examRow.get("participatedCount")).intValue());
        assertEquals(1, ((Number) examRow.get("completedCount")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailListsStudentsWithoutSubmission() {
        seedBaseFixture();

        Map<String, Object> detail = analysisService.getTeacherParticipation(teacher(), "EXAM", EXAM_ID);
        List<Map<String, Object>> students = (List<Map<String, Object>>) detail.get("students");

        assertEquals(2, students.size());
        assertEquals("participationA", students.get(0).get("studentNo"));
        assertEquals("PARTICIPATED", students.get(0).get("participation"));
        assertEquals("COMPLETED", students.get(0).get("completion"));
        assertEquals("participationB", students.get(1).get("studentNo"));
        assertEquals("NOT_STARTED", students.get(1).get("participation"));
        assertEquals("IN_PROGRESS", students.get(1).get("completion"));
        assertNull(students.get(1).get("submissionId"));
        assertNull(students.get(1).get("submitStatus"));
        assertEquals("SE2026-1-测试班", detail.get("className"));
    }

    @Test
    void detailRejectsInvalidTypeCrossSchoolForeignCreatorAndNonAssignedClass() {
        seedBaseFixture();
        insertClass(FOREIGN_CLASS_ID, 2L);
        insertClass(NO_ACCESS_CLASS_ID, SCHOOL_ID);
        insertExam(FOREIGN_EXAM_ID, FOREIGN_CLASS_ID, TEACHER_ID, 2L, "PUBLISHED");
        insertExam(FOREIGN_CREATOR_EXAM_ID, CLASS_ID, OTHER_TEACHER, SCHOOL_ID, "PUBLISHED");
        insertLab(NO_ACCESS_LAB_ID, NO_ACCESS_CLASS_ID, TEACHER_ID, SCHOOL_ID, "PUBLISHED");

        assertEquals(40000, assertThrows(BusinessException.class,
                () -> analysisService.getTeacherParticipation(teacher(), "UNKNOWN", EXAM_ID)).getCode());
        assertEquals(40400, assertThrows(BusinessException.class,
                () -> analysisService.getTeacherParticipation(teacher(), "EXAM", FOREIGN_EXAM_ID)).getCode());
        assertEquals(40300, assertThrows(BusinessException.class,
                () -> analysisService.getTeacherParticipation(teacher(), "EXAM", FOREIGN_CREATOR_EXAM_ID)).getCode());
        assertEquals(40300, assertThrows(BusinessException.class,
                () -> analysisService.getTeacherParticipation(teacher(), "LAB", NO_ACCESS_LAB_ID)).getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailsAndOverviewAgreeOnSameActivity() {
        seedBaseFixture();

        Map<String, Object> detail = analysisService.getTeacherParticipation(teacher(), "HOMEWORK", HOMEWORK_ID);
        List<Map<String, Object>> overview = (List<Map<String, Object>>) analysisService
                .getTeacherOverview(teacher()).get("participationOverview");
        Map<String, Object> homeworkRow = overview.stream()
                .filter(row -> "HOMEWORK".equals(row.get("businessType")))
                .findFirst().orElseThrow();

        assertEquals(detail.get("expectedCount"), homeworkRow.get("expectedCount"));
        assertEquals(detail.get("participatedCount"), homeworkRow.get("participatedCount"));
        assertEquals(detail.get("completedCount"), homeworkRow.get("completedCount"));
        assertEquals(detail.get("notStartedCount"), homeworkRow.get("notStartedCount"));
    }

    private void seedBaseFixture() {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, display_name, role, status, school_id) "
                + "VALUES (?, 'participationTeacher', '{noop}test', '参与口径教师', 'TEACHER', 'ACTIVE', ?)",
                TEACHER_ID, SCHOOL_ID);
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, display_name, role, status, school_id) "
                + "VALUES (?, 'otherParticipationTeacher', '{noop}test', '其他教师', 'TEACHER', 'ACTIVE', ?)",
                OTHER_TEACHER, SCHOOL_ID);
        insertClass(CLASS_ID, SCHOOL_ID);
        insertClassTeacher(CLASS_ID, TEACHER_ID);
        insertStudent(STUDENT_A, "participationA");
        insertStudent(STUDENT_B, "participationB");
        jdbcTemplate.update("INSERT INTO class_member (id, class_id, student_user_id, joined_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)", 910011L, CLASS_ID, STUDENT_A);
        jdbcTemplate.update("INSERT INTO class_member (id, class_id, student_user_id, joined_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)", 910012L, CLASS_ID, STUDENT_B);
        insertLab(LAB_ID, CLASS_ID, TEACHER_ID, SCHOOL_ID, "PUBLISHED");
        insertHomework(HOMEWORK_ID, CLASS_ID, TEACHER_ID, SCHOOL_ID, "PUBLISHED");
        insertExam(EXAM_ID, CLASS_ID, TEACHER_ID, SCHOOL_ID, "PUBLISHED");
        jdbcTemplate.update("INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status) "
                + "VALUES (?, ?, ?, 'SUBMITTED')", LAB_SUBMISSION, LAB_ID, STUDENT_A);
        jdbcTemplate.update("INSERT INTO homework_submission (id, homework_id, student_id, submit_status) "
                + "VALUES (?, ?, ?, 'GRADED')", HOMEWORK_SUBMISSION, HOMEWORK_ID, STUDENT_A);
        jdbcTemplate.update("INSERT INTO exam_submission (id, exam_id, student_id, status) "
                + "VALUES (?, ?, ?, 'AUTO_SUBMITTED')", EXAM_SUBMISSION, EXAM_ID, STUDENT_A);
    }

    private void insertClass(long id, long schoolId) {
        jdbcTemplate.update("INSERT INTO class_room (id, name, code, school_id, status) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE')", id, "SE2026-1-测试班", "PART-CLASS-" + id, schoolId);
    }

    private void insertClassTeacher(long classId, long teacherId) {
        jdbcTemplate.update("INSERT INTO class_teacher (class_id, teacher_user_id, role, assigned_at) "
                + "VALUES (?, ?, 'SUBJECT', CURRENT_TIMESTAMP)", classId, teacherId);
    }

    private void insertStudent(long id, String username) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, display_name, role, status, school_id) "
                + "VALUES (?, ?, '{noop}test', ?, 'STUDENT', 'ACTIVE', ?)", id, username, username, SCHOOL_ID);
    }

    private void insertLab(long id, long classId, long createdBy, long schoolId, String status) {
        jdbcTemplate.update("INSERT INTO t_experiment (experiment_id, experiment_no, experiment_name, status, "
                + "class_id, created_by, school_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, id, "参与口径实验-" + id, status, classId, createdBy, schoolId);
    }

    private void insertHomework(long id, long classId, long createdBy, long schoolId, String status) {
        jdbcTemplate.update("INSERT INTO homework (id, title, class_id, created_by, status, school_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, "参与口径作业-" + id, classId, createdBy, status, schoolId);
    }

    private void insertExam(long id, long classId, long createdBy, long schoolId, String status) {
        jdbcTemplate.update("INSERT INTO exam (id, title, class_id, duration_minutes, status, created_by, "
                + "score_visibility_mode, score_released, school_id) VALUES (?, ?, ?, 60, ?, ?, 'IMMEDIATE', FALSE, ?)",
                id, "参与口径考试-" + id, classId, status, createdBy, schoolId);
    }

    private static CurrentUser teacher() {
        return new CurrentUser(TEACHER_ID, "t9001", "演示教师", UserRole.TEACHER, SCHOOL_ID, "test-session");
    }
}
