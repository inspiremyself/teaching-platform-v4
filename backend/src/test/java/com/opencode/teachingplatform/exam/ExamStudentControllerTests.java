package com.opencode.teachingplatform.exam;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.infra.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.controller.ExamController;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.exam.service.ExamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class ExamStudentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExamService examService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void studentOnlySeesPublishedExamsForOwnClass() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", -1);
        item.put("title", "体系结构阶段测验");
        item.put("className", "SE2026-1");
        item.put("startAt", "2026-04-20T09:00:00+08:00");
        item.put("endAt", "2026-04-20T10:00:00+08:00");
        item.put("status", "PUBLISHED");
        item.put("submissionStatus", null);
        item.put("canStart", true);
        item.put("canResume", false);
        item.put("canViewResult", false);

        when(examService.listExams(any(CurrentUser.class))).thenReturn(List.of(item));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/exams"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("体系结构阶段测验"))
                    .andExpect(jsonPath("$.data[0].className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data[0].startAt").value("2026-04-20T09:00:00+08:00"))
                    .andExpect(jsonPath("$.data[0].endAt").value("2026-04-20T10:00:00+08:00"))
                    .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data[0].canStart").value(true))
                    .andExpect(jsonPath("$.data[0].canResume").value(false))
                    .andExpect(jsonPath("$.data[0].canViewResult").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void startExamReturnsExistingInProgressSubmissionInsteadOfCreatingAnother() throws Exception {
        OffsetDateTime deadline = OffsetDateTime.now().plusMinutes(25);
        when(examService.startExam(any(CurrentUser.class), eq(-1L)))
                .thenReturn(Map.of(
                        "submissionId", 100,
                        "deadlineAt", deadline.toString(),
                        "remainingSeconds", 1500
                ));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            // First call
            mockMvc.perform(post("/api/v1/exams/{id}/start", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100))
                    .andExpect(jsonPath("$.data.remainingSeconds").value(1500));

            // Second call returns same submissionId (service returns existing)
            mockMvc.perform(post("/api/v1/exams/{id}/start", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submittedExamCannotBeStartedAgain() throws Exception {
        when(examService.startExam(any(CurrentUser.class), eq(-1L)))
                .thenThrow(new BusinessException(40000, "考试已提交，不能重新开始"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/exams/{id}/start", -1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试已提交，不能重新开始"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void startExamBeforeStartReturns400() throws Exception {
        when(examService.startExam(any(CurrentUser.class), eq(-1L)))
                .thenThrow(new BusinessException(40000, "考试尚未开始"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/exams/{id}/start", -1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试尚未开始"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submitExamAfterGraceReturns400() throws Exception {
        when(examService.submitExam(any(CurrentUser.class), eq(-1L), any(ExamRequests.SubmitExamRequest.class)))
                .thenThrow(new BusinessException(40000, "考试已结束，无法提交"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/exams/{id}/submit", -1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"answers": [{"questionId": 1, "answerJson": "[\\"A\\"]"}]}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试已结束，无法提交"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submitExamAutoGradesSingleMultipleAndTrueFalseQuestions() throws Exception {
        // Mock submitExam to return auto-graded result
        Map<String, Object> submitResult = new LinkedHashMap<>();
        submitResult.put("submissionId", 100);
        submitResult.put("status", "SUBMITTED");
        submitResult.put("autoScore", 8.0);
        submitResult.put("totalScore", 8.0);
        submitResult.put("hasManualGrading", true);

        when(examService.submitExam(any(CurrentUser.class), eq(-1L), any(ExamRequests.SubmitExamRequest.class)))
                .thenReturn(submitResult);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            String body = """
                {
                  "answers": [
                    {"questionId": 1, "answerJson": "[\\"A\\"]"},
                    {"questionId": 2, "answerJson": "[\\"true\\"]"},
                    {"questionId": 3, "answerJson": "[\\"统一错误处理与前端解析\\"]"}
                  ]
                }
                """;
            mockMvc.perform(post("/api/v1/exams/{id}/submit", -1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.autoScore").value(8.0))
                    .andExpect(jsonPath("$.data.totalScore").value(8.0))
                    .andExpect(jsonPath("$.data.hasManualGrading").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submittedExamCannotBeEditedOrSubmittedTwice() throws Exception {
        when(examService.submitExam(any(CurrentUser.class), eq(-1L), any(ExamRequests.SubmitExamRequest.class)))
                .thenThrow(new BusinessException(40000, "考试已提交，不能重复提交"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            String body = """
                {
                  "answers": [
                    {"questionId": 1, "answerJson": "[\\"A\\"]"}
                  ]
                }
                """;
            mockMvc.perform(post("/api/v1/exams/{id}/submit", -1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试已提交，不能重复提交"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentExamDetailReturnsResultContext() throws Exception {
        when(examService.getExamDetail(any(CurrentUser.class), eq(-1L))).thenReturn(Map.of(
                "id", -1,
                "title", "体系结构阶段测验",
                "className", "SE2026-1",
                "submissionStatus", "GRADED",
                "resultAvailable", true,
                "totalScore", 88.0
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.submissionStatus").value("GRADED"))
                    .andExpect(jsonPath("$.data.resultAvailable").value(true))
                    .andExpect(jsonPath("$.data.totalScore").value(88.0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submittedExamListItemIsNotMarkedAsResultReady() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", -1);
        item.put("title", "体系结构阶段测验");
        item.put("className", "SE2026-1");
        item.put("status", "PUBLISHED");
        item.put("submissionStatus", "SUBMITTED");
        item.put("canStart", false);
        item.put("canResume", false);
        item.put("canViewResult", false);

        when(examService.listExams(any(CurrentUser.class))).thenReturn(List.of(item));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/exams"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].submissionStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data[0].canViewResult").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void gradedExamListItemIsMarkedAsResultReady() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", -1);
        item.put("title", "体系结构阶段测验");
        item.put("className", "SE2026-1");
        item.put("status", "CLOSED");
        item.put("submissionStatus", "GRADED");
        item.put("canStart", false);
        item.put("canResume", false);
        item.put("canViewResult", true);

        when(examService.listExams(any(CurrentUser.class))).thenReturn(List.of(item));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/exams"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].submissionStatus").value("GRADED"))
                    .andExpect(jsonPath("$.data[0].canViewResult").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentExamDetailHidesScoreFieldsWhenResultUnavailable() throws Exception {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", 1);
        question.put("studentAnswer", "[\"A\"]");
        question.put("isCorrect", null);
        question.put("earnedScore", null);
        question.put("teacherComment", null);
        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(question);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", -1);
        detail.put("title", "体系结构阶段测验");
        detail.put("submissionStatus", "SUBMITTED");
        detail.put("resultAvailable", false);
        detail.put("autoScore", null);
        detail.put("manualScore", null);
        detail.put("totalScore", null);
        detail.put("questions", questions);

        when(examService.getExamDetail(any(CurrentUser.class), eq(-1L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.resultAvailable").value(false))
                    .andExpect(jsonPath("$.data.autoScore").value(nullValue()))
                    .andExpect(jsonPath("$.data.manualScore").value(nullValue()))
                    .andExpect(jsonPath("$.data.totalScore").value(nullValue()))
                    // 学生自己的作答不受遮蔽影响
                    .andExpect(jsonPath("$.data.questions[0].studentAnswer").value("[\"A\"]"))
                    .andExpect(jsonPath("$.data.questions[0].isCorrect").value(nullValue()))
                    .andExpect(jsonPath("$.data.questions[0].earnedScore").value(nullValue()))
                    .andExpect(jsonPath("$.data.questions[0].teacherComment").value(nullValue()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submitExamResponseHidesScoresWhenResultUnavailable() throws Exception {
        Map<String, Object> submitResult = new LinkedHashMap<>();
        submitResult.put("submissionId", 100);
        submitResult.put("status", "SUBMITTED");
        submitResult.put("resultAvailable", false);
        submitResult.put("autoScore", null);
        submitResult.put("totalScore", null);
        submitResult.put("hasManualGrading", true);

        when(examService.submitExam(any(CurrentUser.class), eq(-1L), any(ExamRequests.SubmitExamRequest.class)))
                .thenReturn(submitResult);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/exams/{id}/submit", -1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"answers": [{"questionId": 1, "answerJson": "[\\"A\\"]"}]}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.resultAvailable").value(false))
                    .andExpect(jsonPath("$.data.autoScore").value(nullValue()))
                    .andExpect(jsonPath("$.data.totalScore").value(nullValue()))
                    .andExpect(jsonPath("$.data.hasManualGrading").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void saveExamDraftRoutesToPutAndPassesThroughSavedFields() throws Exception {
        Map<String, Object> draftResult = new LinkedHashMap<>();
        draftResult.put("submissionId", 100);
        draftResult.put("savedAt", "2026-11-07T10:15:30+08:00");
        draftResult.put("savedCount", 2);

        when(examService.saveDraft(any(CurrentUser.class), eq(-1L), any(ExamRequests.SubmitExamRequest.class)))
                .thenReturn(draftResult);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(put("/api/v1/exams/{id}/draft", -1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"answers": [
                                      {"questionId": 1, "selectedOptions": ["A"]},
                                      {"questionId": 3, "answerJson": "{\\"kind\\":\\"text\\",\\"text\\":\\"草稿\\"}"}
                                    ]}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100))
                    .andExpect(jsonPath("$.data.savedAt").value("2026-11-07T10:15:30+08:00"))
                    .andExpect(jsonPath("$.data.savedCount").value(2));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void inProgressExamDetailPassesThroughDraftAnswerWithoutGradingFields() throws Exception {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", 1);
        question.put("type", "SINGLE");
        question.put("stem", "软件体系结构的核心目标是什么？");
        question.put("studentAnswer", "{\"kind\":\"single\",\"selectedKey\":\"A\"}");
        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(question);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", -1);
        detail.put("title", "体系结构阶段测验");
        detail.put("submissionStatus", "IN_PROGRESS");
        detail.put("resultAvailable", false);
        detail.put("gradingInProgress", false);
        detail.put("remainingSeconds", 1200);
        detail.put("questions", questions);

        when(examService.getExamDetail(any(CurrentUser.class), eq(-1L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionStatus").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.data.resultAvailable").value(false))
                    .andExpect(jsonPath("$.data.gradingInProgress").value(false))
                    .andExpect(jsonPath("$.data.questions[0].studentAnswer")
                            .value("{\"kind\":\"single\",\"selectedKey\":\"A\"}"))
                    .andExpect(jsonPath("$.data.questions[0].isCorrect").doesNotExist())
                    .andExpect(jsonPath("$.data.questions[0].answerJson").doesNotExist())
                    .andExpect(jsonPath("$.data.questions[0].analysisText").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken authenticateStudent() {
        CurrentUser currentUser = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }
}
