package com.opencode.teachingplatform.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.question.dto.QuestionRequests.SaveQuestionRequest;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.question.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTests {

    @Mock
    private QuestionBankRepository questionBankRepository;

    @Mock
    private AuditLogService auditLogService;

    private QuestionService questionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        questionService = new QuestionService(questionBankRepository, auditLogService, new ObjectMapper());
    }

    @Test
    void nonCodeIntegrityViolationIsNotReportedAsDuplicateCode() {
        when(questionBankRepository.existsByCode("Q-OTHER-001")).thenReturn(false);
        when(questionBankRepository.save(any())).thenThrow(new DataIntegrityViolationException("json column invalid"));

        assertThrows(DataIntegrityViolationException.class, () -> questionService.create(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                new SaveQuestionRequest(
                        "Q-OTHER-001",
                        "SINGLE",
                        "测试题干",
                        "MEDIUM",
                        5,
                        "[\"A\",\"B\"]",
                        "[\"A\"]",
                        "解析"
                )
        ));
    }

    @Test
    void delete_writesQuestionDelete() {
        QuestionBank question = new QuestionBank() {
            @Override
            public Long getId() {
                return 10L;
            }
        };
        question.setCode("Q-DEL-001");
        when(questionBankRepository.findByIdAndCreatedBy(10L, 1L)).thenReturn(java.util.Optional.of(question));

        questionService.delete(new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER), 10L);

        verify(auditLogService).log(
                argThat(user -> user.id().equals(1L)),
                eq(AuditAction.QUESTION_DELETE),
                eq(AuditResourceType.QUESTION),
                eq(10L),
                eq(java.util.Map.of("code", "Q-DEL-001"))
        );
        verify(questionBankRepository).delete(question);
    }
}
