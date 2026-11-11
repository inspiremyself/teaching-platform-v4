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
import com.opencode.teachingplatform.content.service.TaskSubjectValidator;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class ExamTeacherControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskSubjectValidator taskSubjectValidator;

    @MockBean
    private ExamService examService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void teacherCanCreateAndPublishExam() throws Exception {
        // Step 1: Create exam
        when(examService.createExam(any(CurrentUser.class), any(ExamRequests.CreateExamRequest.class)))
                .thenReturn(Map.of(
                        "id", 10,
                        "title", "期中考试",
                        "classId", 1,
                        "status", "DRAFT"
                ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "期中考试",
                                      "description": "覆盖前四章内容",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 1, "sortOrder": 1, "questionScore": 5.0},
                                        {"questionId": 2, "sortOrder": 2, "questionScore": 3.0}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            // Step 2: Publish exam via status change
            when(examService.changeStatus(any(CurrentUser.class), anyLong(), any(ExamRequests.ChangeExamStatusRequest.class)))
                    .thenReturn(Map.of(
                            "id", 10,
                            "status", "PUBLISHED"
                    ));

            mockMvc.perform(post("/api/v1/teacher/exams/{id}/status", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status": "PUBLISHED"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamRejectsDurationBelowMinimum() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "时长下界",
                                      "classId": 1,
                                      "durationMinutes": 0,
                                      "status": "DRAFT",
                                      "questions": []
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试时长必须大于 0 分钟"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamRejectsDurationAboveMaximum() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "时长上界",
                                      "classId": 1,
                                      "durationMinutes": 601,
                                      "status": "DRAFT",
                                      "questions": []
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试时长不能超过 600 分钟"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamRejectsQuestionScoreBelowRange() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "分值下界",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 1, "sortOrder": 1, "questionScore": 0}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("分值必须为 1–100 的整数"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamRejectsQuestionScoreAboveRange() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "分值上界",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 1, "sortOrder": 1, "questionScore": 101}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("分值必须为 1–100 的整数"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamRejectsNonIntegerQuestionScore() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "分值小数",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 1, "sortOrder": 1, "questionScore": 2.5}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("分值必须为 1–100 的整数"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamRejectsNegativeQuestionScore() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "分值负数",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 1, "sortOrder": 1, "questionScore": -1}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("分值必须为 1–100 的整数"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createExamAcceptsBoundaryQuestionScores() throws Exception {
        when(examService.createExam(any(CurrentUser.class), any(ExamRequests.CreateExamRequest.class)))
                .thenReturn(Map.of("id", 10, "title", "边界分值", "classId", 1, "status", "DRAFT"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "边界分值下界",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 1, "sortOrder": 1, "questionScore": 1}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "边界分值上界",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {"questionId": 2, "sortOrder": 2, "questionScore": 100}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCreateExamWithInlineQuestionReturns400() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "含内联题考试",
                                      "description": "内联题已下线",
                                      "classId": 1,
                                      "durationMinutes": 60,
                                      "status": "DRAFT",
                                      "questions": [
                                        {
                                          "sourceType": "INLINE",
                                          "questionType": "SHORT",
                                          "stem": "请说明考试与作业题目能力共享的意义",
                                          "sortOrder": 1,
                                          "questionScore": 10.0,
                                          "answerJson": "[]",
                                          "scoringConfigJson": "{}"
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("考试题目必须来自题库"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReadOwnExamDetail() throws Exception {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", -1);
        detail.put("title", "体系结构阶段测验");
        detail.put("className", "SE2026-1");
        detail.put("questions", List.of(Map.of(
                "questionId", 1,
                "sortOrder", 1,
                "questionScore", 5.0,
                "type", "SINGLE",
                "stem", "ApiResponse 的 code 字段含义？",
                "answerJson", "[\"A\"]",
                "analysisText", "code 为 0 表示成功"
        )));

        when(examService.getExamDetail(any(CurrentUser.class), eq(-1L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.title").value("体系结构阶段测验"))
                    .andExpect(jsonPath("$.data.questions.length()").value(1))
                    .andExpect(jsonPath("$.data.questions[0].answerJson").value("[\"A\"]"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCannotReadAnotherTeachersExamDetail() throws Exception {
        when(examService.getExamDetail(any(CurrentUser.class), eq(-2L)))
                .thenThrow(new BusinessException(40300, "无权限查看该考试"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -2L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300))
                    .andExpect(jsonPath("$.message").value("无权限查看该考试"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void schoolAdminCannotReadExamDetail() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CurrentUser(9L, "admin9001", "校管", UserRole.SCHOOL_ADMIN),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"))
                )
        );
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -1L))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanListExamResults() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("submissionId", 100);
        item.put("examId", -1);
        item.put("examTitle", "期中考试");
        item.put("className", "SE2026-1");
        item.put("studentId", 2);
        item.put("studentName", "演示学生");
        item.put("studentUsername", "20260001");
        item.put("studentNo", "20260001");
        item.put("status", "SUBMITTED");
        item.put("autoScore", 8.0);
        item.put("manualScore", 0.0);
        item.put("totalScore", 8.0);
        item.put("startedAt", OffsetDateTime.now().minusMinutes(30).toString());
        item.put("submittedAt", OffsetDateTime.now().minusMinutes(5).toString());
        results.add(item);

        when(examService.getExamResults(any(CurrentUser.class), eq(-1L))).thenReturn(results);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/exams/{id}/results", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].submissionId").value(100))
                    .andExpect(jsonPath("$.data[0].examTitle").value("期中考试"))
                    .andExpect(jsonPath("$.data[0].className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data[0].studentId").value(2))
                    .andExpect(jsonPath("$.data[0].studentName").value("演示学生"))
                    .andExpect(jsonPath("$.data[0].studentNo").value("20260001"))
                    .andExpect(jsonPath("$.data[0].status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data[0].autoScore").value(8.0))
                    .andExpect(jsonPath("$.data[0].totalScore").value(8.0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReadOwnExamSubmissionDetail() throws Exception {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("submissionId", 100);
        detail.put("examId", -1);
        detail.put("examTitle", "期中考试");
        detail.put("className", "SE2026-1");
        detail.put("studentId", 2);
        detail.put("studentName", "演示学生");
        detail.put("studentUsername", "20260001");
        detail.put("studentNo", "20260001");
        detail.put("status", "SUBMITTED");
        detail.put("autoScore", 8.0);
        detail.put("manualScore", 0.0);
        detail.put("totalScore", 8.0);

        List<Map<String, Object>> answers = new ArrayList<>();
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("questionId", 1);
        a1.put("questionType", "SINGLE");
        a1.put("stem", "ApiResponse 的 code 字段含义？");
        a1.put("studentAnswer", "[\"A\"]");
        a1.put("isCorrect", true);
        a1.put("score", 5.0);
        answers.add(a1);

        Map<String, Object> a3 = new LinkedHashMap<>();
        a3.put("questionId", 3);
        a3.put("questionType", "SHORT");
        a3.put("stem", "简述统一异常处理机制的实现方式");
        a3.put("studentAnswer", "[\"统一错误处理与前端解析\"]");
        a3.put("isCorrect", null);
        a3.put("score", 0.0);
        answers.add(a3);

        detail.put("answers", answers);

        when(examService.getSubmissionDetail(any(CurrentUser.class), eq(100L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/exam-submissions/{id}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100))
                    .andExpect(jsonPath("$.data.examTitle").value("期中考试"))
                    .andExpect(jsonPath("$.data.className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.studentName").value("演示学生"))
                    .andExpect(jsonPath("$.data.studentNo").value("20260001"))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.answers.length()").value(2))
                    .andExpect(jsonPath("$.data.answers[0].questionType").value("SINGLE"))
                    .andExpect(jsonPath("$.data.answers[0].isCorrect").value(true))
                    .andExpect(jsonPath("$.data.answers[1].questionType").value("SHORT"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCannotReadAnotherTeachersExamSubmissionDetail() throws Exception {
        when(examService.getSubmissionDetail(any(CurrentUser.class), eq(200L)))
                .thenThrow(new BusinessException(40300, "无权限查看该提交详情"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/exam-submissions/{id}", 200L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300))
                    .andExpect(jsonPath("$.message").value("无权限查看该提交详情"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void gradingShortAnswerUpdatesManualScoreTotalScoreAndScoreRecord() throws Exception {
        Map<String, Object> gradeResult = new LinkedHashMap<>();
        gradeResult.put("submissionId", 100);
        gradeResult.put("status", "GRADED");
        gradeResult.put("autoScore", 8.0);
        gradeResult.put("manualScore", 7.0);
        gradeResult.put("totalScore", 15.0);

        when(examService.gradeSubmission(any(CurrentUser.class), eq(100L), any(ExamRequests.GradeExamSubmissionRequest.class)))
                .thenReturn(gradeResult);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/exam-submissions/{id}/grade", 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answers": [
                                        {"questionId": 3, "score": 7.0, "teacherComment": "回答正确"}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(100))
                    .andExpect(jsonPath("$.data.status").value("GRADED"))
                    .andExpect(jsonPath("$.data.autoScore").value(8.0))
                    .andExpect(jsonPath("$.data.manualScore").value(7.0))
                    .andExpect(jsonPath("$.data.totalScore").value(15.0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanGradeSubmissionWithEmptyAnswers() throws Exception {
        Map<String, Object> gradeResult = new LinkedHashMap<>();
        gradeResult.put("submissionId", 100);
        gradeResult.put("status", "GRADED");
        gradeResult.put("autoScore", 5.0);
        gradeResult.put("manualScore", 0.0);
        gradeResult.put("totalScore", 5.0);

        when(examService.gradeSubmission(any(CurrentUser.class), eq(100L), any(ExamRequests.GradeExamSubmissionRequest.class)))
                .thenReturn(gradeResult);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/exam-submissions/{id}/grade", 100L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answers": []
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("GRADED"))
                    .andExpect(jsonPath("$.data.totalScore").value(5.0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherSubmissionDetailExposesScoringMetadata() throws Exception {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("questionId", 1);
        answer.put("studentAnswer", "{\"kind\":\"single\",\"selectedKey\":\"A\"}");
        answer.put("isCorrect", true);
        answer.put("score", 5.0);
        answer.put("autoScore", 5.0);
        answer.put("suggestedScore", 5.0);
        answer.put("scoreSource", "AUTO");
        answer.put("judgeDetail", "matched");
        answer.put("acceptedAutoScore", true);
        answer.put("teacherComment", null);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("submissionId", 100);
        detail.put("status", "GRADED");
        detail.put("answers", List.of(answer));

        when(examService.getSubmissionDetail(any(CurrentUser.class), eq(100L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/exam-submissions/{id}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.answers[0].autoScore").value(5.0))
                    .andExpect(jsonPath("$.data.answers[0].suggestedScore").value(5.0))
                    .andExpect(jsonPath("$.data.answers[0].scoreSource").value("AUTO"))
                    .andExpect(jsonPath("$.data.answers[0].judgeDetail").value("matched"))
                    .andExpect(jsonPath("$.data.answers[0].acceptedAutoScore").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherExamDetailExposesScoreReleasedFlag() throws Exception {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", -1);
        detail.put("title", "体系结构阶段测验");
        detail.put("scoreReleased", true);
        detail.put("questions", List.of());

        when(examService.getExamDetail(any(CurrentUser.class), eq(-1L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/exams/{id}", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.scoreReleased").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReleaseScoresAndResponsePassesThrough() throws Exception {
        when(examService.releaseScores(any(CurrentUser.class), eq(-1L)))
                .thenReturn(Map.of("released", true, "scoreVisibilityMode", "MANUAL_RELEASE"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/exams/{id}/release-scores", -1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.released").value(true))
                    .andExpect(jsonPath("$.data.scoreVisibilityMode").value("MANUAL_RELEASE"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCannotReleaseScoresOfAnotherTeachersExam() throws Exception {
        when(examService.releaseScores(any(CurrentUser.class), eq(-2L)))
                .thenThrow(new BusinessException(40300, "无权限发布该考试成绩"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/exams/{id}/release-scores", -2L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken authenticateTeacher() {
        CurrentUser currentUser = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
    }
}
