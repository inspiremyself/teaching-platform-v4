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
        return new CurrentUser(STUDENT_ID, "20260001", "演示学生", UserRole.STUDENT, "test-session");
    }

    private static CurrentUser teacher() {
        return new CurrentUser(TEACHER_ID, "t9001", "演示教师", UserRole.TEACHER, "test-session");
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
