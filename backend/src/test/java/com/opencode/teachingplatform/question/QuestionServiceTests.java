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
import com.opencode.teachingplatform.question.service.QuestionCodeRules;
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
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCode(1L, 1L, "Q-OTHER-001")).thenReturn(false);
        when(questionBankRepository.save(any())).thenThrow(new DataIntegrityViolationException("json column invalid"));

        assertThrows(DataIntegrityViolationException.class, () -> questionService.create(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                new SaveQuestionRequest(
                        "Q-OTHER-001",
                        "SINGLE",
                        "测试题干",
                        "MEDIUM",
                        "[\"A\",\"B\"]",
                        "[\"A\"]",
                        "解析"
                )
        ));
    }

    @Test
    void createNormalizesCodeToUpperCase() {
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCode(1L, 1L, "LINUX-001")).thenReturn(false);
        when(questionBankRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        questionService.create(teacher, buildRequest(" linux-001 "));

        verify(questionBankRepository).save(argThat(question -> "LINUX-001".equals(question.getCode())));
    }

    @Test
    void createRejectsCaseInsensitiveDuplicate() {
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCode(1L, 1L, "LINUX-001")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("linux-001")));
        assertEquals(40900, ex.getCode());
        assertEquals("编码已在你的题库中存在", ex.getMessage());
    }

    @Test
    void updateNormalizesCodeBeforeDuplicateCheck() {
        QuestionBank question = existingQuestion("OLD-001");
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(Optional.of(question));
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCodeAndIdNot(1L, 1L, "LINUX-001", 10L)).thenReturn(false);
        when(questionBankRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        questionService.update(teacher, 10L, buildRequest(" linux-001 "));

        verify(questionBankRepository).save(argThat(saved -> "LINUX-001".equals(saved.getCode())));
    }

    @Test
    void updateRejectsCodeExistsForSameOwner() {
        QuestionBank question = existingQuestion("OLD-001");
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(Optional.of(question));
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCodeAndIdNot(1L, 1L, "OTHER-001", 10L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.update(teacher, 10L, buildRequest("other-001")));
        assertEquals(40900, ex.getCode());
        assertEquals("编码已在你的题库中存在", ex.getMessage());
    }

    @Test
    void codeIntegrityViolationUsesOwnerScopeMessage() {
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCode(1L, 1L, "Q-DUP-001")).thenReturn(false);
        when(questionBankRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "Unique index or primary key violation: public.uk_question_bank_school_creator_code_INDEX_C ON public.question_bank(code)"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("Q-DUP-001")));
        assertEquals(40900, ex.getCode());
        assertEquals("编码已在你的题库中存在", ex.getMessage());
    }

    @Test
    void createRejectsChineseCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("中文编码")));
        assertEquals(40000, ex.getCode());
        assertEquals("题目编码只能包含 A-Z、0-9、- 和 _", ex.getMessage());
    }

    @Test
    void createRejectsCodeWithInnerSpace() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("S 001")));
        assertEquals("题目编码只能包含 A-Z、0-9、- 和 _", ex.getMessage());
    }

    @Test
    void createRejectsCodeWithSpecialCharacter() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("S@001")));
        assertEquals("题目编码只能包含 A-Z、0-9、- 和 _", ex.getMessage());
    }

    @Test
    void createRejectsCodeLongerThan64() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("A".repeat(65))));
        assertEquals(40000, ex.getCode());
        assertEquals("题目编码长度不能超过 64 个字符", ex.getMessage());
    }

    @Test
    void createRejectsBlankCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("   ")));
        assertEquals(40000, ex.getCode());
        assertEquals("题目编码不能为空", ex.getMessage());
    }

    @Test
    void createRejectsReservedHomeworkCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("hw12")));
        assertEquals("题目编码不能使用系统自动编码格式（HW{数字} / LAB{数字}S{数字}）", ex.getMessage());
    }

    @Test
    void createRejectsReservedLabCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.create(teacher, buildRequest("lab3s1")));
        assertEquals("题目编码不能使用系统自动编码格式（HW{数字} / LAB{数字}S{数字}）", ex.getMessage());
    }

    @Test
    void questionCodeRulesRejectsReservedAutoShapes() {
        BusinessException homework = assertThrows(BusinessException.class,
                () -> QuestionCodeRules.validateUserSubmitted("HW12", false));
        assertEquals(40000, homework.getCode());
        BusinessException lab = assertThrows(BusinessException.class,
                () -> QuestionCodeRules.validateUserSubmitted("LAB3S1", false));
        assertEquals(40000, lab.getCode());
    }

    @Test
    void updateKeepsLegacyInvalidCodeWhenUnchanged() {
        QuestionBank question = existingQuestion("OLD 001");
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(Optional.of(question));
        when(questionBankRepository.existsBySchoolIdAndCreatedByAndCodeAndIdNot(1L, 1L, "OLD 001", 10L)).thenReturn(false);
        when(questionBankRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        questionService.update(teacher, 10L, buildRequest("OLD 001"));

        verify(questionBankRepository).save(argThat(saved -> "OLD 001".equals(saved.getCode())));
    }

    @Test
    void updateRejectsChangedLegacyInvalidCode() {
        QuestionBank question = existingQuestion("OLD 001");
        when(questionBankRepository.findByIdAndSchoolId(10L, 1L)).thenReturn(Optional.of(question));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> questionService.update(teacher, 10L, buildRequest("NEW 001")));
        assertEquals(40000, ex.getCode());
        assertEquals("题目编码只能包含 A-Z、0-9、- 和 _", ex.getMessage());
    }

    private QuestionBank existingQuestion(String code) {
        QuestionBank question = new QuestionBank();
        question.setSchoolId(1L);
        question.setCode(code);
        question.setCreatedBy(1L);
        return question;
    }

    private SaveQuestionRequest buildRequest(String code) {
        return new SaveQuestionRequest(
                code,
                "SINGLE",
                "测试题干",
                "MEDIUM",
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
