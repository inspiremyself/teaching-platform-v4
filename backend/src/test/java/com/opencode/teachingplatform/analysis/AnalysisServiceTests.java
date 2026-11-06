package com.opencode.teachingplatform.analysis;

import com.opencode.teachingplatform.analysis.service.AnalysisService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AnalysisServiceTests {

    private static final long STUDENT_ID = 2L;
    private static final long TEACHER_ID = 1L;
    private static final long PENDING_EXPERIMENT_ID = 1003L;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanupExperimentSubmissionFixture() {
        cleanupVisibilityFixture();
        jdbcTemplate.update(
                "DELETE FROM experiment_submission WHERE experiment_id = ? AND student_id = ?",
                PENDING_EXPERIMENT_ID,
                STUDENT_ID
        );
        jdbcTemplate.update("UPDATE score_record SET business_id = -1 WHERE id = -1");
    }

    @Test
    void pendingLabWhenNoSubmission() {
        Map<String, Object> dashboard = analysisService.getStudentDashboard(student());

        assertEquals(1, summaryCardValue(dashboard, "pendingTasks"));
        assertEquals(1, quickLinkBadge(dashboard, "我的实验"));
        assertTrue(upcomingTaskTitles(dashboard).contains("实验模块联调填空实验"));
    }

    @Test
    void savedSubmissionStillCountsAsPending() {
        insertExperimentSubmission("SAVED");

        Map<String, Object> dashboard = analysisService.getStudentDashboard(student());

        assertEquals(1, quickLinkBadge(dashboard, "我的实验"));
        assertTrue(upcomingTaskTitles(dashboard).contains("实验模块联调填空实验"));
    }

    @Test
    void submittedLabExcludedFromPending() {
        insertExperimentSubmission("SUBMITTED");

        Map<String, Object> dashboard = analysisService.getStudentDashboard(student());

        assertEquals(0, summaryCardValue(dashboard, "pendingTasks"));
        assertEquals(0, quickLinkBadge(dashboard, "我的实验"));
        assertFalse(upcomingTaskTitles(dashboard).contains("实验模块联调填空实验"));
    }

    @Test
    void gradedLabExcludedFromPending() {
        Map<String, Object> dashboard = analysisService.getStudentDashboard(student());

        assertFalse(upcomingTaskTitles(dashboard).contains("分层架构分析实验"));
    }

    @Test
    void teacherPendingLabFromExperimentSubmission() {
        Map<String, Object> dashboard = analysisService.getDashboard(teacher());

        assertEquals(1, quickLinkBadge(dashboard, "实验报告批改"));
        assertTrue(recentTaskLabels(dashboard).contains("实验报告待批改"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherDashboardTrendLoadsWithoutDialectSpecificSql() {
        Map<String, Object> dashboard = analysisService.getDashboard(teacher());
        List<Map<String, Object>> trend = (List<Map<String, Object>>) dashboard.get("trend");

        assertTrue(trend != null);
        for (Map<String, Object> row : trend) {
            assertTrue(row.containsKey("date"));
            assertTrue(row.containsKey("scoreCount"));
            assertTrue(row.containsKey("avgScore"));
            assertTrue(((String) row.get("date")).matches("\\d{4}-\\d{2}-\\d{2}"));
        }
    }

    @Test
    void dashboardScopesPlagiarismCountToTeacherCreatedHomework() {
        seedOwnHomeworkFixture();
        insertPlagiarismTask(PLAG_OWN_HIGH_ID, "HOMEWORK", SUBMISSION_OWN_ID, 88.0);
        insertOtherTeacherHighRiskFixture();

        Map<String, Object> dashboard = analysisService.getDashboard(teacher());

        assertEquals(1, summaryCardValue(dashboard, "highRiskPlagiarismCount"));
    }

    @Test
    void highRiskPlagiarismCountIgnoresBoundarySimilarityAndNonHomeworkTasks() {
        seedOwnHomeworkFixture();
        insertPlagiarismTask(PLAG_BOUNDARY_ID, "HOMEWORK", SUBMISSION_OWN_ID, 50.0);
        insertPlagiarismTask(PLAG_NON_HOMEWORK_ID, "LAB", SUBMISSION_OWN_ID, 99.0);

        Map<String, Object> dashboard = analysisService.getDashboard(teacher());

        assertEquals(0, summaryCardValue(dashboard, "highRiskPlagiarismCount"));
    }

    @Test
    void dashboardScopesTrendToTeacherClassesAndSchool() {
        seedOwnHomeworkFixture();
        int baseline = totalTrendScoreCount(analysisService.getDashboard(teacher()));
        insertTrendScoreRecord(TREND_OWN_SCORE_ID, OWN_CLASS_ID, 85.0);

        Map<String, Object> dashboard = analysisService.getDashboard(teacher());

        assertEquals(baseline + 1, totalTrendScoreCount(dashboard));
    }

    @Test
    void teacherTrendExcludesForeignSchoolAndUnassignedClasses() {
        insertClass(FOREIGN_CLASS_ID, "他校趋势班", "TREND-FOREIGN", 2L);
        insertClass(UNASSIGNED_CLASS_ID, "未分配趋势班", "TREND-UNASSIGNED", 1L);
        int baseline = totalTrendScoreCount(analysisService.getDashboard(teacher()));
        insertTrendScoreRecord(TREND_FOREIGN_SCORE_ID, FOREIGN_CLASS_ID, 66.0);
        insertTrendScoreRecord(TREND_UNASSIGNED_SCORE_ID, UNASSIGNED_CLASS_ID, 77.0);

        Map<String, Object> dashboard = analysisService.getDashboard(teacher());

        assertEquals(baseline, totalTrendScoreCount(dashboard));
    }

    @Test
    void dashboardDoesNotDoubleCountWhenClassHasTwoTeachers() {
        seedOwnHomeworkFixture();
        insertClassTeacher(OWN_CLASS_ID, OTHER_TEACHER_ID);
        int baseline = totalTrendScoreCount(analysisService.getDashboard(teacher()));
        insertTrendScoreRecord(TREND_OWN_SCORE_ID, OWN_CLASS_ID, 90.0);

        Map<String, Object> dashboard = analysisService.getDashboard(teacher());

        assertEquals(baseline + 1, totalTrendScoreCount(dashboard));
    }

    @Test
    void dashboardReturnsEmptyValuesForNewTeacher() {
        CurrentUser newTeacher = new CurrentUser(8888L, "new-teacher", "新教师", UserRole.TEACHER, 1L, "test-session");

        Map<String, Object> dashboard = analysisService.getDashboard(newTeacher);

        assertEquals(0, summaryCardValue(dashboard, "highRiskPlagiarismCount"));
        assertTrue(((List<?>) dashboard.get("trend")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherOverviewOnlyIncludesAssignedClassesViaClassTeacherJoin() {
        Map<String, Object> overview = analysisService.getTeacherOverview(teacher());
        List<Map<String, Object>> sectionComparison = (List<Map<String, Object>>) overview.get("sectionComparison");

        assertFalse(sectionComparison.isEmpty());
        assertTrue(sectionComparison.stream().anyMatch(row -> "软件工程 2026-1 班".equals(row.get("className"))));
    }

    @Test
    void labBusinessNameResolvesExperimentId() {
        jdbcTemplate.update("UPDATE score_record SET business_id = 1001 WHERE id = -1");

        List<Map<String, Object>> scores = analysisService.getMyScores(student());
        String labName = scores.stream()
                .filter(item -> "LAB".equals(item.get("businessType")))
                .map(item -> (String) item.get("businessName"))
                .findFirst()
                .orElse("");

        assertEquals("分层架构分析实验", labName);
    }

    @Test
    void totalItemsIncludesPublishedLabs() {
        Map<String, Object> overview = analysisService.getMyScoreOverview(student());

        assertEquals(6, overview.get("totalItems"));
    }

    @Test
    void manualReleaseVisibilityFollowsBusinessFlag() {
        seedVisibilityFixture("MANUAL_RELEASE", false, futureDeadline(), "GRADED", 10.0);

        assertFalse(visibleScoreIds(student()).contains(EXAM_SCORE_ID));
        assertFalse(visibleScoreIds(student()).contains(HOMEWORK_SCORE_ID));
        assertFalse(visibleScoreIds(student()).contains(LAB_SCORE_ID));
        // 未发布时，概览与工作台也不应泄露对应成绩
        assertFalse(recentRecordIds(analysisService.getMyScoreOverview(student())).contains(EXAM_SCORE_ID));
        assertFalse(feedbackBusinessNames(analysisService.getStudentDashboard(student())).contains("可见性矩阵考试"));

        releaseScores();

        List<Long> visibleIds = visibleScoreIds(student());
        assertTrue(visibleIds.contains(EXAM_SCORE_ID));
        assertTrue(visibleIds.contains(HOMEWORK_SCORE_ID));
        assertTrue(visibleIds.contains(LAB_SCORE_ID));
        assertTrue(recentRecordIds(analysisService.getMyScoreOverview(student())).contains(EXAM_SCORE_ID));
        assertTrue(feedbackBusinessNames(analysisService.getStudentDashboard(student())).contains("可见性矩阵考试"));
    }

    @Test
    void afterTeacherConfirmRequiresGradedSubmission() {
        seedVisibilityFixture("AFTER_TEACHER_CONFIRM", false, futureDeadline(), "SUBMITTED", 10.0);

        List<Long> hiddenIds = visibleScoreIds(student());
        assertFalse(hiddenIds.contains(EXAM_SCORE_ID));
        assertFalse(hiddenIds.contains(HOMEWORK_SCORE_ID));
        assertFalse(hiddenIds.contains(LAB_SCORE_ID));

        updateSubmissionStatuses("GRADED", 10.0);

        List<Long> visibleIds = visibleScoreIds(student());
        assertTrue(visibleIds.contains(EXAM_SCORE_ID));
        assertTrue(visibleIds.contains(HOMEWORK_SCORE_ID));
        assertTrue(visibleIds.contains(LAB_SCORE_ID));

        // DRAFT 业务即使已批改也不可见
        updateBusinessStatuses("DRAFT");
        List<Long> draftHiddenIds = visibleScoreIds(student());
        assertFalse(draftHiddenIds.contains(EXAM_SCORE_ID));
        assertFalse(draftHiddenIds.contains(HOMEWORK_SCORE_ID));
        assertFalse(draftHiddenIds.contains(LAB_SCORE_ID));
    }

    @Test
    void afterDeadlineVisibilityDependsOnDeadline() {
        // 考试需先进入 GRADED 最终态，AFTER_DEADLINE 只决定最终成绩的读取时点
        seedVisibilityFixture("AFTER_DEADLINE", false, futureDeadline(), "GRADED", 10.0);

        List<Long> hiddenIds = visibleScoreIds(student());
        assertFalse(hiddenIds.contains(EXAM_SCORE_ID));
        assertFalse(hiddenIds.contains(HOMEWORK_SCORE_ID));
        assertFalse(hiddenIds.contains(LAB_SCORE_ID));

        updateDeadlines(pastDeadline());

        List<Long> visibleIds = visibleScoreIds(student());
        assertTrue(visibleIds.contains(EXAM_SCORE_ID));
        assertTrue(visibleIds.contains(HOMEWORK_SCORE_ID));
        assertTrue(visibleIds.contains(LAB_SCORE_ID));
    }

    @Test
    void immediateModeRequiresSubmissionAndLabTotalScore() {
        // 考试需先进入 GRADED 最终态，IMMEDIATE 才立即可见
        seedVisibilityFixture("IMMEDIATE", false, futureDeadline(), "GRADED", null);

        List<Long> visibleIds = visibleScoreIds(student());
        assertTrue(visibleIds.contains(EXAM_SCORE_ID));
        assertTrue(visibleIds.contains(HOMEWORK_SCORE_ID));
        assertFalse(visibleIds.contains(LAB_SCORE_ID));

        jdbcTemplate.update(
                "UPDATE experiment_submission SET total_score = ? WHERE experiment_id = ? AND student_id = ?",
                12.0,
                LAB_ID,
                STUDENT_ID
        );

        assertTrue(visibleScoreIds(student()).contains(LAB_SCORE_ID));
    }

    @Test
    void zeroScoreRecordStaysVisible() {
        seedVisibilityFixture("MANUAL_RELEASE", true, futureDeadline(), "GRADED", 10.0);
        jdbcTemplate.update("UPDATE score_record SET score = 0 WHERE id = ?", EXAM_SCORE_ID);

        Map<String, Object> examItem = analysisService.getMyScores(student()).stream()
                .filter(item -> Long.valueOf(EXAM_SCORE_ID).equals(((Number) item.get("id")).longValue()))
                .findFirst()
                .orElseThrow();

        assertEquals(0.0, ((Number) examItem.get("score")).doubleValue());
    }

    @Test
    void missingBusinessIsDroppedWithoutUnknownPlaceholder() {
        jdbcTemplate.update(
                "INSERT INTO score_record (id, business_type, business_id, student_id, class_id, score, graded_at) " +
                        "VALUES (?, 'EXAM', ?, ?, 1, 88, CURRENT_TIMESTAMP)",
                ORPHAN_SCORE_ID,
                -9999L,
                STUDENT_ID
        );

        List<Map<String, Object>> scores = analysisService.getMyScores(student());

        assertFalse(scores.stream().anyMatch(item -> Long.valueOf(ORPHAN_SCORE_ID)
                .equals(((Number) item.get("id")).longValue())));
        assertFalse(scores.stream().anyMatch(item -> "未知".equals(item.get("businessName"))));
    }

    @Test
    void visibleScoresAreIsolatedPerStudent() {
        seedVisibilityFixture("MANUAL_RELEASE", true, futureDeadline(), "GRADED", 10.0);
        jdbcTemplate.update(
                "INSERT INTO score_record (id, business_type, business_id, student_id, class_id, score, graded_at) " +
                        "VALUES (?, 'HOMEWORK', ?, ?, 1, 90, CURRENT_TIMESTAMP)",
                OTHER_STUDENT_SCORE_ID,
                HOMEWORK_ID,
                OTHER_STUDENT_ID
        );

        List<Long> studentIds = visibleScoreIds(student());
        assertTrue(studentIds.contains(HOMEWORK_SCORE_ID));
        assertFalse(studentIds.contains(OTHER_STUDENT_SCORE_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewAndDashboardDeriveFromVisibleScores() {
        seedVisibilityFixture("MANUAL_RELEASE", true, futureDeadline(), "GRADED", 10.0);

        List<Map<String, Object>> scores = analysisService.getMyScores(student());
        Map<String, Object> overview = analysisService.getMyScoreOverview(student());

        assertEquals(scores.size(), ((Number) overview.get("completedItems")).intValue());

        double expectedAverage = roundOne(scores.stream()
                .mapToDouble(item -> ((Number) item.get("score")).doubleValue())
                .average().orElse(0.0));
        List<Map<String, Object>> summaryCards = (List<Map<String, Object>>) overview.get("summaryCards");
        double overviewAverage = summaryCards.stream()
                .filter(card -> "averageScore".equals(card.get("key")))
                .map(card -> ((Number) card.get("value")).doubleValue())
                .findFirst()
                .orElseThrow();
        assertEquals(expectedAverage, overviewAverage, 0.001);

        List<String> expectedGroupOrder = scores.stream()
                .map(item -> (String) item.get("businessType"))
                .distinct()
                .sorted()
                .toList();
        List<String> groupedOrder = ((List<Map<String, Object>>) overview.get("groupedScores")).stream()
                .map(row -> (String) row.get("businessType"))
                .toList();
        assertEquals(expectedGroupOrder, groupedOrder);

        assertEquals(Math.min(8, scores.size()), ((List<?>) overview.get("recentRecords")).size());
        // 概览反馈项固定为“优势项 + 待提升项”两条
        assertEquals(2, ((List<?>) overview.get("feedbackNotes")).size());
        int totalItems = ((Number) overview.get("totalItems")).intValue();
        double expectedCompletionRate = roundOne(scores.size() * 100.0 / totalItems);
        double overviewCompletionRate = summaryCards.stream()
                .filter(card -> "completionRate".equals(card.get("key")))
                .map(card -> ((Number) card.get("value")).doubleValue())
                .findFirst()
                .orElseThrow();
        assertEquals(expectedCompletionRate, overviewCompletionRate, 0.001);

        Map<String, Object> dashboard = analysisService.getStudentDashboard(student());
        List<Map<String, Object>> recentFeedback = (List<Map<String, Object>>) dashboard.get("recentFeedback");
        assertEquals(Math.min(4, scores.size()), recentFeedback.size());
        assertEquals(recentFeedback.size(), quickLinkBadge(dashboard, "我的成绩"));
        List<Map<String, Object>> dashboardCards = (List<Map<String, Object>>) dashboard.get("summaryCards");
        double dashboardAverage = dashboardCards.stream()
                .filter(card -> "averageScore".equals(card.get("key")))
                .map(card -> ((Number) card.get("value")).doubleValue())
                .findFirst()
                .orElseThrow();
        assertEquals(overviewAverage, dashboardAverage, 0.001);
        for (int i = 1; i < recentFeedback.size(); i++) {
            OffsetDateTime previous = (OffsetDateTime) recentFeedback.get(i - 1).get("gradedAt");
            OffsetDateTime current = (OffsetDateTime) recentFeedback.get(i).get("gradedAt");
            assertFalse(current.isAfter(previous), "最近反馈应按 gradedAt DESC 排序");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyVisibleResultReturnsZeroedOverview() {
        CurrentUser emptyStudent = new CurrentUser(9999L, "no-score-student", "无成绩学生", UserRole.STUDENT, 1L, "test-session");

        assertTrue(analysisService.getMyScores(emptyStudent).isEmpty());

        Map<String, Object> overview = analysisService.getMyScoreOverview(emptyStudent);
        List<Map<String, Object>> summaryCards = (List<Map<String, Object>>) overview.get("summaryCards");
        Map<String, Object> averageCard = summaryCards.stream()
                .filter(card -> "averageScore".equals(card.get("key")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> highestCard = summaryCards.stream()
                .filter(card -> "highestScore".equals(card.get("key")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> completionCard = summaryCards.stream()
                .filter(card -> "completionRate".equals(card.get("key")))
                .findFirst()
                .orElseThrow();

        assertEquals(0.0, ((Number) averageCard.get("value")).doubleValue());
        assertEquals(0.0, ((Number) highestCard.get("value")).doubleValue());
        assertEquals(0.0, ((Number) completionCard.get("value")).doubleValue());
        assertEquals(0, ((Number) overview.get("completedItems")).intValue());
        assertTrue(((List<?>) overview.get("groupedScores")).isEmpty());
        assertTrue(((List<?>) overview.get("recentRecords")).isEmpty());
        assertTrue(((List<?>) overview.get("feedbackNotes")).isEmpty());
        // totalItems 仍取三域已发布任务计数（该学生无班级归属，因此为 0），不随可见成绩口径推导
        assertEquals(0, ((Number) overview.get("totalItems")).intValue());
    }

    private static final long EXAM_ID = -101L;
    private static final long HOMEWORK_ID = -102L;
    private static final long LAB_ID = -103L;
    private static final long EXAM_SCORE_ID = -9101L;
    private static final long HOMEWORK_SCORE_ID = -9102L;
    private static final long LAB_SCORE_ID = -9103L;
    private static final long ORPHAN_SCORE_ID = -9104L;
    private static final long OTHER_STUDENT_SCORE_ID = -9105L;
    private static final long OTHER_STUDENT_ID = 3L;
    private static final long OTHER_TEACHER_ID = 9999L;
    private static final long OWN_CLASS_ID = -201L;
    private static final long FOREIGN_CLASS_ID = -202L;
    private static final long UNASSIGNED_CLASS_ID = -203L;
    private static final long HOMEWORK_OWN_ID = -301L;
    private static final long HOMEWORK_FOREIGN_ID = -302L;
    private static final long SUBMISSION_OWN_ID = -3021L;
    private static final long SUBMISSION_FOREIGN_ID = -3022L;
    private static final long PLAG_OWN_HIGH_ID = -3031L;
    private static final long PLAG_FOREIGN_HIGH_ID = -3032L;
    private static final long PLAG_BOUNDARY_ID = -3033L;
    private static final long PLAG_NON_HOMEWORK_ID = -3034L;
    private static final long TREND_OWN_SCORE_ID = -3041L;
    private static final long TREND_FOREIGN_SCORE_ID = -3042L;
    private static final long TREND_UNASSIGNED_SCORE_ID = -3043L;

    /**
     * 构造“教师本人作业”基础夹具：所属班级、班级教师归属、作业与提交记录。
     */
    private void seedOwnHomeworkFixture() {
        insertClass(OWN_CLASS_ID, "查重归属班", "PLAG-OWN", 1L);
        insertClassTeacher(OWN_CLASS_ID, TEACHER_ID);
        insertHomework(HOMEWORK_OWN_ID, "教师自有作业", OWN_CLASS_ID, TEACHER_ID);
        insertHomeworkSubmission(SUBMISSION_OWN_ID, HOMEWORK_OWN_ID);
    }

    /**
     * 构造“其他教师布置的作业”夹具，用于验证高风险查重数量不会跨教师统计。
     */
    private void insertOtherTeacherHighRiskFixture() {
        insertClass(FOREIGN_CLASS_ID, "他校查重班", "PLAG-FOREIGN", 2L);
        insertHomework(HOMEWORK_FOREIGN_ID, "他人作业", FOREIGN_CLASS_ID, OTHER_TEACHER_ID);
        insertHomeworkSubmission(SUBMISSION_FOREIGN_ID, HOMEWORK_FOREIGN_ID);
        insertPlagiarismTask(PLAG_FOREIGN_HIGH_ID, "HOMEWORK", SUBMISSION_FOREIGN_ID, 95.0);
    }

    private void insertClass(long id, String name, String code, long schoolId) {
        jdbcTemplate.update(
                "INSERT INTO class_room (id, name, code, school_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                id, name, code, schoolId
        );
    }

    private void insertClassTeacher(long classId, long teacherId) {
        jdbcTemplate.update(
                "INSERT INTO class_teacher (class_id, teacher_user_id, role, assigned_at) " +
                        "VALUES (?, ?, 'SUBJECT', CURRENT_TIMESTAMP)",
                classId, teacherId
        );
    }

    private void insertHomework(long id, String title, long classId, long createdBy) {
        jdbcTemplate.update(
                "INSERT INTO homework (id, title, class_id, created_by, status) VALUES (?, ?, ?, ?, 'PUBLISHED')",
                id, title, classId, createdBy
        );
    }

    private void insertHomeworkSubmission(long id, long homeworkId) {
        jdbcTemplate.update(
                "INSERT INTO homework_submission (id, homework_id, student_id, submit_status) " +
                        "VALUES (?, ?, ?, 'SUBMITTED')",
                id, homeworkId, STUDENT_ID
        );
    }

    private void insertPlagiarismTask(long id, String businessType, long businessId, double similarityRate) {
        jdbcTemplate.update(
                "INSERT INTO plagiarism_task (id, business_type, business_id, student_id, status, similarity_rate) " +
                        "VALUES (?, ?, ?, ?, 'COMPLETED', ?)",
                id, businessType, businessId, STUDENT_ID, similarityRate
        );
    }

    private void insertTrendScoreRecord(long id, long classId, double score) {
        jdbcTemplate.update(
                "INSERT INTO score_record (id, business_type, business_id, student_id, class_id, score, graded_at) " +
                        "VALUES (?, 'HOMEWORK', ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, id, STUDENT_ID, classId, score
        );
    }

    @SuppressWarnings("unchecked")
    private static int totalTrendScoreCount(Map<String, Object> dashboard) {
        List<Map<String, Object>> trend = (List<Map<String, Object>>) dashboard.get("trend");
        return trend.stream().mapToInt(row -> ((Number) row.get("scoreCount")).intValue()).sum();
    }

    /**
     * 清理教师看板归属隔离夹具，避免非事务测试之间互相污染。
     */
    @BeforeEach
    @AfterEach
    void cleanupTeacherIsolationFixture() {
        jdbcTemplate.update("DELETE FROM score_record WHERE id IN (?, ?, ?)",
                TREND_OWN_SCORE_ID, TREND_FOREIGN_SCORE_ID, TREND_UNASSIGNED_SCORE_ID);
        jdbcTemplate.update("DELETE FROM plagiarism_task WHERE id IN (?, ?, ?, ?)",
                PLAG_OWN_HIGH_ID, PLAG_FOREIGN_HIGH_ID, PLAG_BOUNDARY_ID, PLAG_NON_HOMEWORK_ID);
        jdbcTemplate.update("DELETE FROM homework_submission WHERE id IN (?, ?)",
                SUBMISSION_OWN_ID, SUBMISSION_FOREIGN_ID);
        jdbcTemplate.update("DELETE FROM homework WHERE id IN (?, ?)",
                HOMEWORK_OWN_ID, HOMEWORK_FOREIGN_ID);
        jdbcTemplate.update("DELETE FROM class_teacher WHERE class_id IN (?, ?, ?)",
                OWN_CLASS_ID, FOREIGN_CLASS_ID, UNASSIGNED_CLASS_ID);
        jdbcTemplate.update("DELETE FROM class_room WHERE id IN (?, ?, ?)",
                OWN_CLASS_ID, FOREIGN_CLASS_ID, UNASSIGNED_CLASS_ID);
    }

    /**
     * 构造三域可见性矩阵夹具：业务行 + 当前学生提交行 + 成绩记录。
     *
     * @param mode             成绩可见模式
     * @param released         业务成绩发布标记
     * @param deadline         截止时间（考试 end_at / 作业 due_at / 实验 end_at）
     * @param submissionStatus 提交状态
     * @param labTotalScore    实验总分（null 表示未算出总分）
     */
    private void seedVisibilityFixture(String mode, boolean released, OffsetDateTime deadline,
                                       String submissionStatus, Double labTotalScore) {
        jdbcTemplate.update(
                "INSERT INTO exam (id, title, class_id, duration_minutes, status, created_by, " +
                        "score_visibility_mode, score_released, end_at) VALUES (?, ?, 1, 60, 'PUBLISHED', ?, ?, ?, ?)",
                EXAM_ID, "可见性矩阵考试", TEACHER_ID, mode, released, toTimestamp(deadline)
        );
        jdbcTemplate.update(
                "INSERT INTO homework (id, title, class_id, created_by, status, score_visibility_mode, " +
                        "score_released, due_at) VALUES (?, ?, 1, ?, 'PUBLISHED', ?, ?, ?)",
                HOMEWORK_ID, "可见性矩阵作业", TEACHER_ID, mode, released, toTimestamp(deadline)
        );
        jdbcTemplate.update(
                "INSERT INTO t_experiment (experiment_id, experiment_no, experiment_name, status, class_id, created_by, " +
                        "score_visibility_mode, score_released, end_at) VALUES (?, ?, ?, 'PUBLISHED', 1, ?, ?, ?, ?)",
                LAB_ID, (int) LAB_ID, "可见性矩阵实验", TEACHER_ID, mode, released, toTimestamp(deadline)
        );
        jdbcTemplate.update(
                "INSERT INTO exam_submission (id, exam_id, student_id, status) VALUES (?, ?, ?, ?)",
                -9201L, EXAM_ID, STUDENT_ID, submissionStatus
        );
        jdbcTemplate.update(
                "INSERT INTO homework_submission (id, homework_id, student_id, submit_status) VALUES (?, ?, ?, ?)",
                -9202L, HOMEWORK_ID, STUDENT_ID, submissionStatus
        );
        jdbcTemplate.update(
                "INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, total_score) " +
                        "VALUES (?, ?, ?, ?, ?)",
                -9203L, LAB_ID, STUDENT_ID, submissionStatus, labTotalScore
        );
        insertScoreRecord(EXAM_SCORE_ID, "EXAM", EXAM_ID, STUDENT_ID, 60.0);
        insertScoreRecord(HOMEWORK_SCORE_ID, "HOMEWORK", HOMEWORK_ID, STUDENT_ID, 70.0);
        insertScoreRecord(LAB_SCORE_ID, "LAB", LAB_ID, STUDENT_ID, 80.0);
    }

    private void insertScoreRecord(long id, String businessType, long businessId, long studentId, double score) {
        jdbcTemplate.update(
                "INSERT INTO score_record (id, business_type, business_id, student_id, class_id, score, graded_at) " +
                        "VALUES (?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP)",
                id, businessType, businessId, studentId, score
        );
    }

    private void releaseScores() {
        jdbcTemplate.update("UPDATE exam SET score_released = TRUE WHERE id = ?", EXAM_ID);
        jdbcTemplate.update("UPDATE homework SET score_released = TRUE WHERE id = ?", HOMEWORK_ID);
        jdbcTemplate.update("UPDATE t_experiment SET score_released = TRUE WHERE experiment_id = ?", LAB_ID);
    }

    private void updateSubmissionStatuses(String status, double labTotalScore) {
        jdbcTemplate.update("UPDATE exam_submission SET status = ? WHERE exam_id = ?", status, EXAM_ID);
        jdbcTemplate.update("UPDATE homework_submission SET submit_status = ? WHERE homework_id = ?", status, HOMEWORK_ID);
        jdbcTemplate.update(
                "UPDATE experiment_submission SET submit_status = ?, total_score = ? WHERE experiment_id = ?",
                status, labTotalScore, LAB_ID
        );
    }

    private void updateBusinessStatuses(String status) {
        jdbcTemplate.update("UPDATE exam SET status = ? WHERE id = ?", status, EXAM_ID);
        jdbcTemplate.update("UPDATE homework SET status = ? WHERE id = ?", status, HOMEWORK_ID);
        jdbcTemplate.update("UPDATE t_experiment SET status = ? WHERE experiment_id = ?", status, LAB_ID);
    }

    private void updateDeadlines(OffsetDateTime deadline) {
        jdbcTemplate.update("UPDATE exam SET end_at = ? WHERE id = ?", toTimestamp(deadline), EXAM_ID);
        jdbcTemplate.update("UPDATE homework SET due_at = ? WHERE id = ?", toTimestamp(deadline), HOMEWORK_ID);
        jdbcTemplate.update("UPDATE t_experiment SET end_at = ? WHERE experiment_id = ?", toTimestamp(deadline), LAB_ID);
    }

    private static OffsetDateTime futureDeadline() {
        return OffsetDateTime.now().plusDays(1);
    }

    private static OffsetDateTime pastDeadline() {
        return OffsetDateTime.now().minusDays(1);
    }

    private static java.sql.Timestamp toTimestamp(OffsetDateTime value) {
        return java.sql.Timestamp.from(value.toInstant());
    }

    private List<Long> visibleScoreIds(CurrentUser user) {
        return analysisService.getMyScores(user).stream()
                .map(item -> ((Number) item.get("id")).longValue())
                .toList();
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void cleanupVisibilityFixture() {
        jdbcTemplate.update("DELETE FROM score_record WHERE id IN (?, ?, ?, ?, ?)",
                EXAM_SCORE_ID, HOMEWORK_SCORE_ID, LAB_SCORE_ID, ORPHAN_SCORE_ID, OTHER_STUDENT_SCORE_ID);
        jdbcTemplate.update("DELETE FROM exam_submission WHERE exam_id = ?", EXAM_ID);
        jdbcTemplate.update("DELETE FROM exam WHERE id = ?", EXAM_ID);
        jdbcTemplate.update("DELETE FROM homework_submission WHERE homework_id = ?", HOMEWORK_ID);
        jdbcTemplate.update("DELETE FROM homework WHERE id = ?", HOMEWORK_ID);
        jdbcTemplate.update("DELETE FROM experiment_submission WHERE experiment_id = ?", LAB_ID);
        jdbcTemplate.update("DELETE FROM t_experiment WHERE experiment_id = ?", LAB_ID);
    }

    private void insertExperimentSubmission(String submitStatus) {
        jdbcTemplate.update(
                "DELETE FROM experiment_submission WHERE experiment_id = ? AND student_id = ?",
                PENDING_EXPERIMENT_ID,
                STUDENT_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO experiment_submission (
                    id, experiment_id, student_id, submit_status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                9903L,
                PENDING_EXPERIMENT_ID,
                STUDENT_ID,
                submitStatus
        );
    }

    private static CurrentUser student() {
        return new CurrentUser(STUDENT_ID, "20260001", "演示学生", UserRole.STUDENT, 1L, "test-session");
    }

    private static CurrentUser teacher() {
        return new CurrentUser(TEACHER_ID, "t9001", "演示教师", UserRole.TEACHER, 1L, "test-session");
    }

    @SuppressWarnings("unchecked")
    private static int summaryCardValue(Map<String, Object> dashboard, String key) {
        List<Map<String, Object>> cards = (List<Map<String, Object>>) dashboard.get("summaryCards");
        return cards.stream()
                .filter(card -> key.equals(card.get("key")))
                .map(card -> ((Number) card.get("value")).intValue())
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static int quickLinkBadge(Map<String, Object> dashboard, String label) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) dashboard.get("quickLinks");
        return links.stream()
                .filter(link -> label.equals(link.get("label")))
                .map(link -> ((Number) link.get("badge")).intValue())
                .findFirst()
                .orElseThrow();
    }

    /**
     * 概览最近记录中的成绩 id 列表，用于断言未发布成绩不会从概览泄露。
     */
    @SuppressWarnings("unchecked")
    private static List<Long> recentRecordIds(Map<String, Object> overview) {
        List<Map<String, Object>> records = (List<Map<String, Object>>) overview.get("recentRecords");
        return records.stream()
                .map(item -> ((Number) item.get("id")).longValue())
                .toList();
    }

    /**
     * 工作台最近反馈中的业务名称列表（反馈契约不含成绩 id，按标题识别）。
     */
    @SuppressWarnings("unchecked")
    private static List<String> feedbackBusinessNames(Map<String, Object> dashboard) {
        List<Map<String, Object>> feedback = (List<Map<String, Object>>) dashboard.get("recentFeedback");
        return feedback.stream()
                .map(item -> (String) item.get("businessName"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> upcomingTaskTitles(Map<String, Object> dashboard) {
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) dashboard.get("upcomingTasks");
        return tasks.stream().map(task -> (String) task.get("title")).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> recentTaskLabels(Map<String, Object> dashboard) {
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) dashboard.get("recentTasks");
        return tasks.stream().map(task -> (String) task.get("label")).toList();
    }
}
