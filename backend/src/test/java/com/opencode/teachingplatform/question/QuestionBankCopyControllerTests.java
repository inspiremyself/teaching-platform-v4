package com.opencode.teachingplatform.question;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.common.accesslog.HttpAccessLogFilter;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.question.controller.QuestionBankCopyController;
import com.opencode.teachingplatform.question.service.QuestionBankCopyService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 复制到题库接口的切片测试。
 *
 * <p>覆盖成功 200 与 40300 / 40000 / 40900 的错误码到 HTTP 状态映射。</p>
 */
@WebMvcTest(QuestionBankCopyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class QuestionBankCopyControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionBankCopyService questionBankCopyService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private HttpAccessLogFilter httpAccessLogFilter;

    @Test
    void teacherCanCopyHomeworkQuestionWithExplicitCode() throws Exception {
        when(questionBankCopyService.copyFromHomework(any(CurrentUser.class), anyLong(), eq("HW-CODE")))
                .thenReturn(Map.of(
                        "created", true,
                        "question", Map.of("id", 21, "code", "HW-CODE", "sourceType", "HOMEWORK_INLINE"),
                        "duplicateQuestionId", 0
                ));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homework-questions/{id}/copy-to-bank", 11L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "HW-CODE"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.created").value(true))
                    .andExpect(jsonPath("$.data.question.code").value("HW-CODE"));

            verify(questionBankCopyService).copyFromHomework(any(CurrentUser.class), eq(11L), eq("HW-CODE"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void copyingForeignHomeworkQuestionReturnsForbidden() throws Exception {
        when(questionBankCopyService.copyFromHomework(any(CurrentUser.class), anyLong(), any()))
                .thenThrow(new BusinessException(40300, "无权限访问该作业"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/homework-questions/{id}/copy-to-bank", 11L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void copyingUnsupportedLabStepReturnsBadRequest() throws Exception {
        when(questionBankCopyService.copyFromLabStep(any(CurrentUser.class), anyLong(), any()))
                .thenThrow(new BusinessException(40000, "该题项类型不支持复制到题库"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/lab-steps/{id}/copy-to-bank", 31L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("该题项类型不支持复制到题库"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void copyingWithDuplicateCodeReturnsConflict() throws Exception {
        when(questionBankCopyService.copyFromLabStep(any(CurrentUser.class), anyLong(), any()))
                .thenThrow(new BusinessException(40900, "题目编码已存在"));

        SecurityContextHolder.getContext().setAuthentication(authenticateTeacher());
        try {
            mockMvc.perform(post("/api/v1/teacher/lab-steps/{id}/copy-to-bank", 31L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "DUP-CODE"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40900));
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
