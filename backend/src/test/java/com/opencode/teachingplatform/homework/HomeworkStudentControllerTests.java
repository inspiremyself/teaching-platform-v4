package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.homework.controller.HomeworkController;
import com.opencode.teachingplatform.homework.service.HomeworkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeworkController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class HomeworkStudentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeworkService homeworkService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void studentOnlySeesPublishedHomeworksForOwnClasses() throws Exception {
        when(homeworkService.listStudentHomeworks(any(CurrentUser.class))).thenReturn(List.of(Map.of(
                "id", 1,
                "title", "分层架构作业",
                "status", "PUBLISHED"
        )));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/student/homeworks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("分层架构作业"))
                    .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentCanSubmitHomeworkWithRealPlagiarismRate() throws Exception {
        when(homeworkService.submitHomework(any(CurrentUser.class), org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(Map.of(
                "submissionId", 9,
                "status", "SUBMITTED",
                "plagiarismRate", 42.86
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/homeworks/{id}/submit", 1L)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answerText": "这是本次作业答案",
                                      "attachment": "/uploads/homework/a.docx"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(9))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.data.plagiarismRate").value(42.86))
                    .andExpect(jsonPath("$.data.plagiarismStatus").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentHomeworkDetailContainsClassAndAttachmentContext() throws Exception {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", 1);
        detail.put("title", "分层架构作业");
        detail.put("className", "SE2026-1");
        detail.put("submissionStatus", "SUBMITTED");
        detail.put("answerFilePath", "/uploads/homework/a.docx");
        detail.put("attachmentAnswerPath", "/uploads/homework/a.docx");
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("homeworkQuestionId", 11);
        answer.put("score", 10);
        answer.put("autoScore", 10);
        answer.put("suggestedScore", null);
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", 11);
        question.put("sortOrder", 1);
        question.put("type", "SINGLE_CHOICE");
        question.put("stem", "策略模式的核心目的是什么？");
        question.put("score", 10);
        question.put("answer", answer);
        detail.put("questions", List.of(question));

        when(homeworkService.getStudentHomeworkDetail(any(CurrentUser.class), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/student/homeworks/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.answerFilePath").value("/uploads/homework/a.docx"))
                    .andExpect(jsonPath("$.data.attachmentAnswerPath").value("/uploads/homework/a.docx"))
                    .andExpect(jsonPath("$.data.questions[0].stem").value("策略模式的核心目的是什么？"))
                    .andExpect(jsonPath("$.data.questions[0].answer.autoScore").value(10));
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
