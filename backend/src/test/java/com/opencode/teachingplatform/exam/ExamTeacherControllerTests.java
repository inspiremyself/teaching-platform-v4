package com.opencode.teachingplatform.exam;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.common.enums.UserRole;
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
    void teacherCanCreateExamWithInlineQuestion() throws Exception {
        when(examService.createExam(any(CurrentUser.class), any(ExamRequests.CreateExamRequest.class)))
                .thenReturn(Map.of(
                        "id", 11,
                        "title", "含内联题考试",
                        "classId", 1,
                        "status", "DRAFT"
                ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "含内联题考试",
                                      "description": "允许教师直接新增题目",
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
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(11))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
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

    private UsernamePasswordAuthenticationToken authenticateTeacher() {
        CurrentUser currentUser = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
    }
}
