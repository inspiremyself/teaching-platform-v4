package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.lab.controller.LabController;
import com.opencode.teachingplatform.lab.service.LabService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class LabReportViewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LabService labService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void teacherCanReadPureLabReportView() throws Exception {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepId", 3001);
        step.put("stepNo", 1);
        step.put("stepTitle", "步骤1");
        step.put("content", "题干");
        step.put("answerText", "最新答案");
        step.put("images", List.of(Map.of(
                "path", "lab-answers/2026-09-15/shot1.png",
                "name", "shot1.png",
                "contentType", "image/png",
                "size", 3
        )));

        when(labService.getTeacherLabReportView(any(CurrentUser.class), anyLong(), anyLong())).thenReturn(Map.of(
                "labId", 1001,
                "studentId", 2001,
                "courseName", "软件设计与体系结构",
                "reportTitle", "南昌航空大学实验报告",
                "reportDate", "2026 年 4 月 22 日",
                "steps", List.of(step)
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(get("/api/v1/teacher/labs/{labId}/report-view/{studentId}", 1001L, 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.labId").value(1001))
                    .andExpect(jsonPath("$.data.studentId").value(2001))
                    .andExpect(jsonPath("$.data.courseName").value("软件设计与体系结构"))
                    .andExpect(jsonPath("$.data.steps.length()").value(1))
                    .andExpect(jsonPath("$.data.steps[0].answerText").value("最新答案"))
                    .andExpect(jsonPath("$.data.steps[0].images[0].path").value("lab-answers/2026-09-15/shot1.png"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken authenticateTeacher() {
        CurrentUser currentUser = new CurrentUser(1L, "t9001", "演示教师", com.opencode.teachingplatform.common.enums.UserRole.TEACHER);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
    }
}
