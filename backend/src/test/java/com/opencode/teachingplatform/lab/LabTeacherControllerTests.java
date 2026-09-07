package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.lab.controller.LabController;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.service.LabService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class LabTeacherControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LabService labService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void teacherCanCreatePublishedLabForOwnedClass() throws Exception {
        when(labService.createLab(any(CurrentUser.class), argThat(request ->
                request != null && Boolean.TRUE.equals(request.summaryRequired())
        ))).thenReturn(Map.of(
                "id", 1,
                "title", "体系结构分层实验",
                "classId", 1,
                "status", "PUBLISHED",
                "summaryRequired", true
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/labs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "体系结构分层实验",
                                      "description": "完成分层架构分析与步骤作答",
                                      "classId": 1,
                                      "status": "PUBLISHED",
                                      "summaryRequired": true
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.classId").value(1))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.summaryRequired").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanGradeOwnStudentSubmission() throws Exception {
        when(labService.gradeReport(any(CurrentUser.class), anyLong(), any(LabRequests.GradeLabReportRequest.class))).thenReturn(Map.of(
                "graded", true,
                "totalScore", 90
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/lab-reports/{id}/grade", 5L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "teacherComment": "结构清晰",
                                      "items": [
                                        {"answerId": 11, "score": 30, "teacherComment": "第一步完整"},
                                        {"answerId": 12, "score": 30, "teacherComment": "第二步完整"},
                                        {"answerId": 13, "score": 30, "teacherComment": "第三步完整"}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.graded").value(true))
                    .andExpect(jsonPath("$.data.totalScore").value(90));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReturnSubmittedLabReport() throws Exception {
        when(labService.returnReport(any(CurrentUser.class), anyLong())).thenReturn(Map.of(
                "submissionId", 5L,
                "status", "SAVED",
                "labId", 1001L,
                "studentId", 2L
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/lab-reports/{id}/return", 5L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(5))
                    .andExpect(jsonPath("$.data.status").value("SAVED"))
                    .andExpect(jsonPath("$.data.labId").value(1001))
                    .andExpect(jsonPath("$.data.studentId").value(2));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReadLabReportList() throws Exception {
        when(labService.listTeacherReports(any(CurrentUser.class), any(LabRequests.TeacherLabReportQuery.class))).thenReturn(List.of(Map.of(
                "id", 1,
                "labId", 1,
                "labTitle", "分层架构分析实验",
                "className", "SE2026-1",
                "studentName", "演示学生",
                "studentNo", "20260001",
                "submitStatus", "SUBMITTED"
        )));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/lab-reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].labTitle").value("分层架构分析实验"))
                    .andExpect(jsonPath("$.data[0].className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data[0].studentNo").value("20260001"))
                    .andExpect(jsonPath("$.data[0].submitStatus").value("SUBMITTED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherLabReportListForwardsQueryFilters() throws Exception {
        when(labService.listTeacherReports(any(CurrentUser.class), argThat(query ->
                query != null && "架构".equals(query.keyword()) && "SUBMITTED".equals(query.status())
        ))).thenReturn(List.of());

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/lab-reports")
                            .param("keyword", "架构")
                            .param("status", "SUBMITTED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherReportDetailReturnsReportViewWithoutReportLevelScore() throws Exception {
        Map<String, Object> answerItem = new LinkedHashMap<>();
        answerItem.put("answerId", 11);
        answerItem.put("stepTitle", "分层设计");
        answerItem.put("questionType", "TEXT");
        answerItem.put("maxScore", 30);
        answerItem.put("score", 26);
        answerItem.put("autoScore", 30);
        answerItem.put("suggestedScore", 28);
        answerItem.put("scoreSource", "TEACHER");
        answerItem.put("autoJudgeDetail", "exact matched answers: a");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", 5);
        detail.put("className", "SE2026-1");
        detail.put("studentNo", "20260001");
        detail.put("summaryText", "实验小结内容");
        detail.put("teacherComment", "报告备注");
        detail.put("totalScore", 26);
        detail.put("submittedAt", "2026-04-14T10:00:00Z");
        detail.put("gradedAt", "2026-04-14T11:00:00Z");
        detail.put("items", List.of(answerItem));

        when(labService.getTeacherReportDetail(any(CurrentUser.class), anyLong())).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/lab-reports/{id}", 5L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.studentNo").value("20260001"))
                    .andExpect(jsonPath("$.data.summaryText").value("实验小结内容"))
                    .andExpect(jsonPath("$.data.teacherComment").value("报告备注"))
                    .andExpect(jsonPath("$.data.totalScore").value(26))
                    .andExpect(jsonPath("$.data.reportScore").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].stepTitle").value("分层设计"))
                    .andExpect(jsonPath("$.data.items[0].questionType").value("TEXT"))
                    .andExpect(jsonPath("$.data.items[0].maxScore").value(30))
                    .andExpect(jsonPath("$.data.items[0].autoScore").value(30))
                    .andExpect(jsonPath("$.data.items[0].suggestedScore").value(28))
                    .andExpect(jsonPath("$.data.items[0].scoreSource").value("TEACHER"))
                    .andExpect(jsonPath("$.data.items[0].autoJudgeDetail").value("exact matched answers: a"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanListBlankItemsForLab() throws Exception {
        when(labService.listBlankItems(any(CurrentUser.class), anyLong())).thenReturn(List.of(Map.of(
                "id", 11,
                "stepNo", 2,
                "title", "填写 Spring 关键组件",
                "questionType", "FILL_BLANK",
                "stepScore", 10
        )));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/labs/{id}/blank-items", 5L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(11))
                    .andExpect(jsonPath("$.data[0].questionType").value("FILL_BLANK"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanReadBlankItemAnswerDistribution() throws Exception {
        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("item", Map.of(
                "id", 11,
                "labId", 5,
                "stepNo", 2,
                "title", "填写 Spring 关键组件",
                "questionType", "FILL_BLANK",
                "stepScore", 10,
                "questionId", 99,
                "content", "填写组件名称"
        ));
        distribution.put("acceptedAnswers", List.of("spring", "boot"));
        distribution.put("answerDistribution", List.of(
                Map.of("answerText", "Spring Boot", "normalizedAnswer", "spring boot", "count", 2, "accepted", true),
                Map.of("answerText", "spring", "normalizedAnswer", "spring", "count", 1, "accepted", true)
        ));
        when(labService.getBlankItemAnswerDistribution(any(CurrentUser.class), anyLong(), anyLong())).thenReturn(distribution);

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/labs/{id}/blank-items/{itemId}/answer-distribution", 5L, 11L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.item.id").value(11))
                    .andExpect(jsonPath("$.data.item.stepNo").value(2))
                    .andExpect(jsonPath("$.data.item.questionId").value(99))
                    .andExpect(jsonPath("$.data.acceptedAnswers.length()").value(2))
                    .andExpect(jsonPath("$.data.answerDistribution[0].count").value(2))
                    .andExpect(jsonPath("$.data.answerDistribution[0].accepted").value(true))
                    .andExpect(jsonPath("$.data.answerDistribution[1].accepted").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanSaveBlankAcceptedAnswers() throws Exception {
        when(labService.saveBlankAcceptedAnswers(any(CurrentUser.class), anyLong(), any(LabRequests.SaveBlankAcceptedAnswersRequest.class)))
                .thenReturn(Map.of(
                        "saved", true,
                        "experimentItemId", 11,
                        "regradedCount", 3,
                        "acceptedAnswers", List.of("spring", "boot")
                ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/labs/{id}/blank-items/accepted-answers", 5L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "experimentItemId": 11,
                                      "acceptedAnswers": ["spring", "boot"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.saved").value(true))
                    .andExpect(jsonPath("$.data.experimentItemId").value(11))
                    .andExpect(jsonPath("$.data.regradedCount").value(3))
                    .andExpect(jsonPath("$.data.acceptedAnswers.length()").value(2));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanUpdateOwnedLab() throws Exception {
        when(labService.updateLab(any(CurrentUser.class), anyLong(), argThat(request ->
                request != null && Boolean.FALSE.equals(request.summaryRequired())
        ))).thenReturn(Map.of(
                "id", 1,
                "title", "体系结构分层实验-更新",
                "classId", 1,
                "status", "DRAFT",
                "summaryRequired", false
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(put("/api/v1/teacher/labs/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "体系结构分层实验-更新",
                                      "description": "补充实验说明",
                                      "classId": 1,
                                      "status": "DRAFT",
                                      "materialId": 2,
                                      "summaryRequired": false
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("体系结构分层实验-更新"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.summaryRequired").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanChangeOwnedLabStatus() throws Exception {
        when(labService.changeLabStatus(any(CurrentUser.class), anyLong(), any(LabRequests.ChangeLabStatusRequest.class))).thenReturn(Map.of(
                "id", 1,
                "status", "CLOSED"
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(put("/api/v1/teacher/labs/{id}/status", 1L)
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
    void teacherCannotCreateLabForUnownedClass() throws Exception {
        when(labService.createLab(any(CurrentUser.class), any(LabRequests.CreateLabRequest.class)))
                .thenThrow(new BusinessException(40300, "无权限访问该班级"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/labs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "越权实验",
                                      "description": "不应创建成功",
                                      "classId": 2,
                                      "status": "DRAFT"
                                    }
                                    """))
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
