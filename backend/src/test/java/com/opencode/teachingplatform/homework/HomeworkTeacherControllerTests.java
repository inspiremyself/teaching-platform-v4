package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.homework.controller.HomeworkController;
import com.opencode.teachingplatform.homework.dto.HomeworkRequests;
import com.opencode.teachingplatform.homework.service.HomeworkService;
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

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeworkController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class HomeworkTeacherControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeworkService homeworkService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void teacherCanCreateAndPublishHomework() throws Exception {
        when(homeworkService.createHomework(any(CurrentUser.class), any(HomeworkRequests.CreateHomeworkRequest.class))).thenReturn(Map.of(
                "id", 1,
                "title", "分层架构作业",
                "classId", 1,
                "status", "PUBLISHED"
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homeworks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "分层架构作业",
                                      "description": "完成分层设计说明",
                                      "classId": 1,
                                      "status": "PUBLISHED"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.classId").value(1))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanGradeHomeworkSubmission() throws Exception {
        when(homeworkService.gradeSubmission(any(CurrentUser.class), anyLong(), any(HomeworkRequests.GradeHomeworkRequest.class))).thenReturn(Map.of(
                "graded", true,
                "totalScore", 92
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homework-submissions/{id}/grade", 5L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "totalScore": 92,
                                      "teacherComment": "分析完整"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.graded").value(true))
                    .andExpect(jsonPath("$.data.totalScore").value(92));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanGetHomeworkSubmissionDetail() throws Exception {
        when(homeworkService.getTeacherSubmissionDetail(any(CurrentUser.class), anyLong())).thenReturn(Map.of(
                "id", 5,
                "homeworkId", 1,
                "className", "SE2026-1",
                "studentUsername", "20260001",
                "studentNo", "20260001",
                "submitStatus", "SUBMITTED",
                "plagiarismRate", 66.67,
                "topMatchSummary", "最高相似来源: 历史作业#1 (66.67%)"
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/homework-submissions/{id}", 5L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(5))
                    .andExpect(jsonPath("$.data.className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.studentUsername").value("20260001"))
                    .andExpect(jsonPath("$.data.studentNo").value("20260001"))
                    .andExpect(jsonPath("$.data.submitStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.plagiarismRate").value(66.67))
                    .andExpect(jsonPath("$.data.topMatchSummary").value("最高相似来源: 历史作业#1 (66.67%)"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherChangesHomeworkStatusWithPostEndpoint() throws Exception {
        when(homeworkService.changeHomeworkStatus(any(CurrentUser.class), anyLong(), any(HomeworkRequests.ChangeHomeworkStatusRequest.class))).thenReturn(Map.of(
                "id", 1,
                "status", "CLOSED"
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homeworks/{id}/status", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "status": "CLOSED"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanAttachQuestionFromBank() throws Exception {
        when(homeworkService.addQuestionFromBank(any(CurrentUser.class), anyLong(), any(HomeworkRequests.AddHomeworkQuestionFromBankRequest.class))).thenReturn(Map.of(
                "id", 11,
                "homeworkId", 1,
                "questionId", 9,
                "sortOrder", 1,
                "questionScore", 10
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homeworks/{id}/questions/bank", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "questionId": 9,
                                      "sortOrder": 1,
                                      "questionScore": 10
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(11))
                    .andExpect(jsonPath("$.data.questionId").value(9))
                    .andExpect(jsonPath("$.data.sortOrder").value(1));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanAttachInlineQuestion() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", 12);
        result.put("homeworkId", 1);
        result.put("questionId", null);
        result.put("sortOrder", 2);
        result.put("questionScore", 15);
        when(homeworkService.addInlineQuestion(any(CurrentUser.class), anyLong(), any(HomeworkRequests.AddInlineHomeworkQuestionRequest.class))).thenReturn(result);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
                    mockMvc.perform(post("/api/v1/teacher/homeworks/{id}/questions/inline", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "type": "SUBJECTIVE",
                                      "stem": "请说明策略模式的价值",
                                      "questionScore": 15,
                                      "scoringConfigJson": "{\\"keywords\\":[{\\"term\\":\\"strategy\\",\\"weight\\":8}],\\"minLength\\":6}",
                                      "saveToQuestionBank": false,
                                      "sortOrder": 2
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(12))
                    .andExpect(jsonPath("$.data.sortOrder").value(2));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanGradeSingleHomeworkAnswer() throws Exception {
        when(homeworkService.gradeAnswer(any(CurrentUser.class), anyLong(), any(HomeworkRequests.GradeHomeworkAnswerRequest.class))).thenReturn(Map.of(
                "answerId", 21,
                "score", 12,
                "acceptedAutoScore", true
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homework-answers/{id}/grade", 21L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "score": 12,
                                      "teacherComment": "采纳推荐分",
                                      "acceptSuggested": true
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.answerId").value(21))
                    .andExpect(jsonPath("$.data.score").value(12))
                    .andExpect(jsonPath("$.data.acceptedAutoScore").value(true));
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
