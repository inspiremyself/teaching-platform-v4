package com.opencode.teachingplatform.question;

import com.opencode.teachingplatform.assessment.service.QuestionContentValidator;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditResourceType;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.question.dto.QuestionRequests.SaveQuestionRequest;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.question.service.QuestionBankViewMapper;
import com.opencode.teachingplatform.question.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Mock
    private QuestionContentValidator questionContentValidator;

    private QuestionService questionService;

    private final CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        questionService = new QuestionService(
                questionBankRepository,
                auditLogService,
                questionContentValidator,
                new QuestionBankViewMapper()
        );
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
    void createNormalizesCodeToUpperCase() {
        when(questionBankRepository.existsByCode("LINUX-001")).thenReturn(false);
        when(questionBankRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        questionService.create(teacher, buildRequest(" linux-001 "));

        verify(questionBankRepository).save(argThat(question -> "LINUX-001".equals(question.getCode())));
    }

    @Test
    void createRejectsCaseInsensitiveDuplicate() {
        when(questionBankRepository.existsByCode("LINUX-001")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("linux-001")));
        assertEquals(40900, ex.getCode());
    }

    @Test
    void updateNormalizesCodeBeforeDuplicateCheck() {
        QuestionBank question = new QuestionBank();
        question.setSchoolId(1L);
        question.setCode("OLD-001");
        question.setCreatedBy(1L);
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(Optional.of(question));
        when(questionBankRepository.existsByCodeAndIdNot("LINUX-001", 10L)).thenReturn(false);
        when(questionBankRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        questionService.update(teacher, 10L, buildRequest(" linux-001 "));

        verify(questionBankRepository).save(argThat(saved -> "LINUX-001".equals(saved.getCode())));
    }

    @Test
    void updateRejectsCodeExistsGlobally() {
        QuestionBank question = new QuestionBank();
        question.setSchoolId(1L);
        question.setCode("OLD-001");
        question.setCreatedBy(1L);
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(Optional.of(question));
        when(questionBankRepository.existsByCodeAndIdNot("OTHER-001", 10L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.update(teacher, 10L, buildRequest("other-001")));
        assertEquals(40900, ex.getCode());
    }

    private SaveQuestionRequest buildRequest(String code) {
        return new SaveQuestionRequest(
                code,
                "SINGLE",
                "测试题干",
                "MEDIUM",
                5,
                "[\"A\",\"B\"]",
                "[\"A\"]",
                "解析"
        );
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
        question.setCreatedBy(1L);
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(java.util.Optional.of(question));

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
