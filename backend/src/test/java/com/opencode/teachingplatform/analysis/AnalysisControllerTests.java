package com.opencode.teachingplatform.analysis;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherDashboardReturnsRealStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/dashboard")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summaryCards").isArray())
                .andExpect(jsonPath("$.data.summaryCards.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data.summaryCards[0].key").value("activityCount"))
                .andExpect(jsonPath("$.data.summaryCards[0].value").isNumber())
                .andExpect(jsonPath("$.data.trend").isArray())
                .andExpect(jsonPath("$.data.recentTasks").isArray())
                .andExpect(jsonPath("$.data.quickLinks").isArray())
                .andExpect(jsonPath("$.data.summaryCards[0].value").value(9))
                .andExpect(jsonPath("$.data.summaryCards[1].key").value("pendingGradeCount"))
                .andExpect(jsonPath("$.data.summaryCards[1].value").value(3))
                .andExpect(jsonPath("$.data.quickLinks[1].label").value("实验报告批改"))
                .andExpect(jsonPath("$.data.quickLinks[1].badge").value(1));
    }

    @Test
    void studentMyScoresReturnsEnrichedRecords() throws Exception {
        ensureGradedExamSubmissionExists();

        mockMvc.perform(get("/api/v1/analysis/my-scores")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].businessName").value(org.hamcrest.Matchers.containsString("测验")))
                .andExpect(jsonPath("$.data[0].score").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    @Test
    void studentCannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/dashboard")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void studentDashboardReturnsAggregatedTaskData() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/student-dashboard")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summaryCards").isArray())
                .andExpect(jsonPath("$.data.upcomingTasks").isArray())
                .andExpect(jsonPath("$.data.recentMaterials").isArray())
                .andExpect(jsonPath("$.data.quickLinks").isArray())
                .andExpect(jsonPath("$.data.summaryCards[0].key").value("pendingTasks"))
                .andExpect(jsonPath("$.data.summaryCards[0].value").value(1))
                .andExpect(jsonPath("$.data.upcomingTasks[?(@.type=='实验')].title")
                        .value(org.hamcrest.Matchers.hasItem("实验模块联调填空实验")))
                .andExpect(jsonPath("$.data.quickLinks[0].label").value("我的实验"))
                .andExpect(jsonPath("$.data.quickLinks[0].badge").value(1));
    }

    @Test
    void teacherOverviewReturnsComparisonAndInsights() throws Exception {
        ensureGradedExamSubmissionExists();

        mockMvc.perform(get("/api/v1/analysis/teacher-overview")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.summaryCards").isArray())
                .andExpect(jsonPath("$.data.summaryCards.length()").value(4))
                .andExpect(jsonPath("$.data.summaryCards[0].key").value("activityCount"))
                .andExpect(jsonPath("$.data.summaryCards[0].title").value("教学活动总数"))
                .andExpect(jsonPath("$.data.summaryCards[0].value").isNumber())
                .andExpect(jsonPath("$.data.summaryCards[0].description").value("实验、作业与考试总量"))
                .andExpect(jsonPath("$.data.summaryCards[1].key").value("pendingGradeCount"))
                .andExpect(jsonPath("$.data.summaryCards[2].key").value("averageScore"))
                .andExpect(jsonPath("$.data.summaryCards[3].key").value("highRiskPlagiarismCount"))
                .andExpect(jsonPath("$.data.sectionComparison").isArray())
                .andExpect(jsonPath("$.data.sectionComparison.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.sectionComparison[0].className").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andExpect(jsonPath("$.data.sectionComparison[0].avgScore").isNumber())
                .andExpect(jsonPath("$.data.sectionComparison[0].gradedCount").isNumber())
                .andExpect(jsonPath("$.data.businessBreakdown").isArray())
                .andExpect(jsonPath("$.data.businessBreakdown.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.businessBreakdown[*].businessType", org.hamcrest.Matchers.hasItem("EXAM")))
                .andExpect(jsonPath("$.data.businessBreakdown[*].label", org.hamcrest.Matchers.hasItem("考试")))
                .andExpect(jsonPath("$.data.businessBreakdown[0].avgScore").isNumber())
                .andExpect(jsonPath("$.data.businessBreakdown[0].scoreCount").isNumber())
                .andExpect(jsonPath("$.data.insights").isArray())
                .andExpect(jsonPath("$.data.insights.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.insights[0].tone").value("positive"))
                .andExpect(jsonPath("$.data.insights[0].title").value("平均成绩稳定"))
                .andExpect(jsonPath("$.data.insights[0].description").value(org.hamcrest.Matchers.containsString("当前班级平均成绩")))
                .andExpect(jsonPath("$.data.insights[*].title", org.hamcrest.Matchers.hasItem("最佳班级表现")))
                .andExpect(jsonPath("$.data.insights[*].title", org.hamcrest.Matchers.hasItem("重点关注项")));
    }

    @Test
    void myScoreOverviewReturnsSummaryAndGroupedData() throws Exception {
        ensureGradedExamSubmissionExists();

        mockMvc.perform(get("/api/v1/analysis/my-score-overview")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.summaryCards").isArray())
                .andExpect(jsonPath("$.data.summaryCards.length()").value(4))
                .andExpect(jsonPath("$.data.summaryCards[0].key").value("averageScore"))
                .andExpect(jsonPath("$.data.summaryCards[0].title").value("平均成绩"))
                .andExpect(jsonPath("$.data.summaryCards[0].value").isNumber())
                .andExpect(jsonPath("$.data.summaryCards[1].key").value("completedItems"))
                .andExpect(jsonPath("$.data.summaryCards[1].value").isNumber())
                .andExpect(jsonPath("$.data.summaryCards[2].key").value("highestScore"))
                .andExpect(jsonPath("$.data.summaryCards[3].key").value("completionRate"))
                .andExpect(jsonPath("$.data.groupedScores").isArray())
                .andExpect(jsonPath("$.data.groupedScores.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.groupedScores[*].businessType", org.hamcrest.Matchers.hasItem("EXAM")))
                .andExpect(jsonPath("$.data.groupedScores[*].label", org.hamcrest.Matchers.hasItem("考试")))
                .andExpect(jsonPath("$.data.groupedScores[0].avgScore").isNumber())
                .andExpect(jsonPath("$.data.groupedScores[0].scoreCount").isNumber())
                .andExpect(jsonPath("$.data.recentRecords").isArray())
                .andExpect(jsonPath("$.data.recentRecords.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.recentRecords[0].id").isNumber())
                .andExpect(jsonPath("$.data.recentRecords[0].businessType").value("EXAM"))
                .andExpect(jsonPath("$.data.recentRecords[0].businessName").value(org.hamcrest.Matchers.containsString("测验")))
                .andExpect(jsonPath("$.data.recentRecords[0].score").isNumber())
                .andExpect(jsonPath("$.data.recentRecords[0].gradedAt").exists())
                .andExpect(jsonPath("$.data.feedbackNotes").isArray())
                .andExpect(jsonPath("$.data.feedbackNotes.length()").value(2))
                .andExpect(jsonPath("$.data.feedbackNotes[0].tone").value("positive"))
                .andExpect(jsonPath("$.data.feedbackNotes[0].title").value("优势项"))
                .andExpect(jsonPath("$.data.feedbackNotes[0].description").value(org.hamcrest.Matchers.containsString("表现最佳")))
                .andExpect(jsonPath("$.data.feedbackNotes[1].tone").value("warning"))
                .andExpect(jsonPath("$.data.feedbackNotes[1].title").value("待提升项"))
                .andExpect(jsonPath("$.data.feedbackNotes[1].description").value(org.hamcrest.Matchers.containsString("建议优先安排复盘")));
    }

    @Test
    void teacherCannotAccessMyScores() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/my-scores")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void teacherCannotAccessStudentDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/student-dashboard")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void studentCannotAccessTeacherOverview() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/teacher-overview")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void teacherCannotAccessMyScoreOverview() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/my-score-overview")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private void ensureGradedExamSubmissionExists() throws Exception {
        MvcResult initialResults = teacherExamResults();
        com.jayway.jsonpath.DocumentContext initialContext = com.jayway.jsonpath.JsonPath.parse(initialResults.getResponse().getContentAsString());
        List<?> submissions = initialContext.read("$.data");

        if (submissions.isEmpty()) {
            startAndSubmitExam();
            initialResults = teacherExamResults();
            initialContext = com.jayway.jsonpath.JsonPath.parse(initialResults.getResponse().getContentAsString());
        } else {
            String status = initialContext.read("$.data[0].status", String.class);
            if ("IN_PROGRESS".equals(status)) {
                submitExamAnswers();
                initialResults = teacherExamResults();
                initialContext = com.jayway.jsonpath.JsonPath.parse(initialResults.getResponse().getContentAsString());
            }
        }

        Integer submissionId = initialContext.read("$.data[0].submissionId");
        String latestStatus = initialContext.read("$.data[0].status", String.class);
        if (!"GRADED".equals(latestStatus)) {
            gradeSubmission(submissionId);
        }
    }

    private MvcResult teacherExamResults() throws Exception {
        return mockMvc.perform(get("/api/v1/teacher/exams/{id}/results", -1L)
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    private void startAndSubmitExam() throws Exception {
        mockMvc.perform(post("/api/v1/exams/{id}/start", -1L)
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        submitExamAnswers();
    }

    private void submitExamAnswers() throws Exception {
        String submitBody = """
                {
                  \"answers\": [
                    {\"questionId\": 1, \"answerJson\": \"[\\\"A\\\"]\"},
                    {\"questionId\": 2, \"answerJson\": \"[\\\"true\\\"]\"},
                    {\"questionId\": 3, \"answerJson\": \"[\\\"统一错误处理与前端解析\\\"]\"}
                  ]
                }
                """;
        mockMvc.perform(post("/api/v1/exams/{id}/submit", -1L)
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void gradeSubmission(Integer submissionId) throws Exception {
        String gradeBody = """
                {
                  \"answers\": [
                    {\"questionId\": 3, \"score\": 8.0, \"teacherComment\": \"回答较好\"}
                  ]
                }
                """;
        mockMvc.perform(post("/api/v1/teacher/exam-submissions/{id}/grade", submissionId)
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String bearerToken(SysUser user) {
        return IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                user.getId()
        );
    }

    private SysUser teacherUser(Long id, String username, String displayName) {
        return buildUser(id, username, displayName, UserRole.TEACHER);
    }

    private SysUser studentUser(Long id, String username, String displayName) {
        return buildUser(id, username, displayName, UserRole.STUDENT);
    }

    private SysUser buildUser(Long id, String username, String displayName, UserRole role) {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return id;
            }
        };
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("{noop}123456");
        return user;
    }
}
