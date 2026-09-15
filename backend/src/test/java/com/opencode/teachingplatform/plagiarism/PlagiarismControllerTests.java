package com.opencode.teachingplatform.plagiarism;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.plagiarism.controller.PlagiarismController;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlagiarismController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class PlagiarismControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlagiarismService plagiarismService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void teacherCanListPlagiarismTasks() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1L);
        item.put("businessType", "HOMEWORK");
        item.put("studentId", 2L);
        item.put("studentName", "演示学生");
        item.put("businessName", "体系结构分析作业");
        item.put("similarityRate", 66.67);
        item.put("plagiarismRate", 66.67);
        item.put("riskLevel", "HIGH");
        item.put("topMatchSummary", "最高相似来源: 体系结构分析作业#-1 (66.67%)");
        item.put("status", "COMPLETED");
        item.put("reviewStatus", "COMPLETED");
        item.put("review", Map.of("status", "COMPLETED", "conclusion", "HIGH_RISK_PENDING"));

        when(plagiarismService.listTasks(any(CurrentUser.class))).thenReturn(List.of(item));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/plagiarism/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].studentName").value("演示学生"))
                    .andExpect(jsonPath("$.data[0].businessName").value("体系结构分析作业"))
                    .andExpect(jsonPath("$.data[0].similarityRate").value(66.67))
                    .andExpect(jsonPath("$.data[0].plagiarismRate").value(66.67))
                    .andExpect(jsonPath("$.data[0].riskLevel").value("HIGH"))
                    .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReviewTask() throws Exception {
        when(plagiarismService.reviewTask(any(CurrentUser.class), eq(1L), eq("LOW_RISK_CONFIRMED"), eq("确认无抄袭")))
                .thenReturn(Map.of(
                        "reviewed", true,
                        "status", "REVIEWED",
                        "reviewStatus", "REVIEWED",
                        "review", Map.of("status", "REVIEWED", "conclusion", "LOW_RISK_CONFIRMED", "comment", "确认无抄袭"),
                        "reviewComment", "确认无抄袭",
                        "reviewConclusion", "LOW_RISK_CONFIRMED"
                ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/plagiarism/tasks/{id}/review", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reviewConclusion": "LOW_RISK_CONFIRMED", "reviewComment": "确认无抄袭"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.reviewed").value(true))
                    .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                    .andExpect(jsonPath("$.data.review.conclusion").value("LOW_RISK_CONFIRMED"))
                    .andExpect(jsonPath("$.data.reviewComment").value("确认无抄袭"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentCanOnlySeeOwnTask() throws Exception {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", 1L);
        detail.put("businessType", "HOMEWORK");
        detail.put("studentId", 2L);
        detail.put("studentName", "演示学生");
        detail.put("businessName", "体系结构分析作业");
        detail.put("similarityRate", 66.67);
        detail.put("plagiarismRate", 66.67);
        detail.put("riskLevel", "HIGH");
        detail.put("reviewStatus", "COMPLETED");
        detail.put("review", Map.of("status", "COMPLETED", "conclusion", "HIGH_RISK_PENDING", "comment", "待教师进一步核查"));
        detail.put("reviewComment", "待教师进一步核查");
        detail.put("reviewConclusion", "HIGH_RISK_PENDING");

        when(plagiarismService.getTaskDetail(any(CurrentUser.class), eq(1L))).thenReturn(detail);

        // Student 2 can see own task
        SecurityContextHolder.getContext().setAuthentication(authenticateStudent(2L, "20260001"));
        try {
            mockMvc.perform(get("/api/v1/plagiarism/tasks/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.taskId").value(1));
        } finally {
            SecurityContextHolder.clearContext();
        }

        // Student 99 gets 403 for other student's task
        when(plagiarismService.getTaskDetail(any(CurrentUser.class), eq(1L)))
                .thenThrow(new com.opencode.teachingplatform.common.exception.BusinessException(40300, "无权限访问该查重记录"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent(99L, "20260099"));
        try {
            mockMvc.perform(get("/api/v1/plagiarism/tasks/{id}", 1L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCannotSeeOtherTeachersTask() throws Exception {
        when(plagiarismService.getTaskDetail(any(CurrentUser.class), eq(1L)))
                .thenThrow(new com.opencode.teachingplatform.common.exception.BusinessException(40300, "无权限访问该查重记录"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/plagiarism/tasks/{id}", 1L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300))
                    .andExpect(jsonPath("$.message").value("无权限访问该查重记录"));
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

    private UsernamePasswordAuthenticationToken authenticateStudent(Long id, String username) {
        CurrentUser currentUser = new CurrentUser(id, username, "测试学生", UserRole.STUDENT);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }
}
