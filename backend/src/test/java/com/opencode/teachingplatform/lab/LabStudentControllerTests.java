package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.lab.behavior.dto.BehaviorRequests;
import com.opencode.teachingplatform.lab.behavior.service.BehaviorIngestService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class LabStudentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LabService labService;

    @MockBean
    private BehaviorIngestService behaviorIngestService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void studentOnlySeesPublishedLabsForOwnClasses() throws Exception {
        when(labService.listStudentLabs(any(CurrentUser.class))).thenReturn(List.of(Map.of(
                "id", 1,
                "title", "体系结构分层实验",
                "status", "PUBLISHED"
        )));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/student/labs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("体系结构分层实验"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void savingAnswerCreatesSubmissionInSavedStatus() throws Exception {
        when(labService.saveAnswer(any(CurrentUser.class), anyLong(), anyLong(), any(LabRequests.SaveStepAnswerRequest.class))).thenReturn(Map.of(
                "submissionStatus", "SAVED",
                "answerId", 10
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/answers/{stepId}", 1L, 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "answerText": "第一步答案"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionStatus").value("SAVED"))
                    .andExpect(jsonPath("$.data.answerId").value(10));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentLabDetailDoesNotExposeAutoJudgeDetail() throws Exception {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", 2);
        step.put("answerText", "第一步答案");
        step.put("score", 18);
        step.put("teacherComment", "已阅");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", 1);
        detail.put("className", "SE2026-1");
        detail.put("summaryText", "实验小结内容");
        detail.put("items", List.of(step));
        detail.put("steps", List.of(step));

        when(labService.getStudentLabDetail(any(CurrentUser.class), anyLong())).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/student/labs/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.className").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.summaryText").value("实验小结内容"))
                    .andExpect(jsonPath("$.data.items[0].id").value(2))
                    .andExpect(jsonPath("$.data.steps[0].id").value(2))
                    .andExpect(jsonPath("$.data.steps[0].answerText").value("第一步答案"))
                    .andExpect(jsonPath("$.data.items[0].autoJudgeDetail").doesNotExist())
                    .andExpect(jsonPath("$.data.steps[0].autoJudgeDetail").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentLabDetailDoesNotExposeSuggestedTeacherCommentBeforeTeacherGrades() throws Exception {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", 2);
        step.put("answerText", "文本题答案");
        step.put("score", 0);
        step.put("teacherComment", null);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", 1);
        detail.put("teacherComment", null);
        detail.put("steps", List.of(step));

        when(labService.getStudentLabDetail(any(CurrentUser.class), anyLong())).thenReturn(detail);

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(get("/api/v1/student/labs/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.teacherComment").isEmpty())
                    .andExpect(jsonPath("$.data.steps[0].teacherComment").isEmpty())
                    .andExpect(jsonPath("$.data.steps[0].autoJudgeDetail").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submitLabAcceptsSummaryTextRequestBody() throws Exception {
        when(labService.submitLab(any(CurrentUser.class), anyLong(), argThat(request ->
                request != null && "实验小结内容".equals(request.summaryText())
        ))).thenReturn(Map.of(
                "submissionId", 7,
                "status", "SUBMITTED"
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/submit", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "summaryText": "实验小结内容"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(7))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submitLabAllowsBlankSummaryRequestBodyToReachService() throws Exception {
        when(labService.submitLab(any(CurrentUser.class), anyLong(), argThat(request ->
                request != null && "   ".equals(request.summaryText())
        ))).thenReturn(Map.of(
                "submissionId", 8,
                "status", "SUBMITTED"
        ));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/submit", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "summaryText": "   "
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionId").value(8))
                    .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void submittingClosedLabIsRejected() throws Exception {
        when(labService.submitLab(any(CurrentUser.class), anyLong(), any(LabRequests.SubmitLabRequest.class)))
                .thenThrow(new BusinessException(40000, "实验已关闭，不能提交"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/submit", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "summaryText": "尝试提交"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("实验已关闭，不能提交"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void studentCanPostBehaviorEvents() throws Exception {
        when(behaviorIngestService.ingest(any(CurrentUser.class), anyLong(), any(BehaviorRequests.IngestBatch.class)))
                .thenReturn(Map.of("accepted", 2, "dropped", 0));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/behavior-events", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "events": [
                                        {"eventUuid": "uuid-1", "eventType": "LAB_ENTER", "occurredAt": "2026-10-28T10:00:00+08:00"},
                                        {"eventUuid": "uuid-2", "eventType": "HEARTBEAT", "occurredAt": "2026-10-28T10:01:00+08:00", "payload": {"activeMs": 60000}}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.accepted").value(2))
                    .andExpect(jsonPath("$.data.dropped").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void behaviorEventsRejectsEmptyBatch() throws Exception {
        when(behaviorIngestService.ingest(any(CurrentUser.class), anyLong(), any(BehaviorRequests.IngestBatch.class)))
                .thenThrow(new BusinessException(40000, "events 不能为空"));

        SecurityContextHolder.getContext().setAuthentication(authenticateStudent());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/behavior-events", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"events\": []}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCannotPostBehaviorEvents() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/student/labs/{id}/behavior-events", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"events\": []}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
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

    private UsernamePasswordAuthenticationToken authenticateTeacher() {
        CurrentUser currentUser = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
    }
}
