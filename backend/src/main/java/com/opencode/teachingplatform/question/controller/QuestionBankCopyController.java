package com.opencode.teachingplatform.question.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.question.dto.QuestionRequests.CopyQuestionToBankRequest;
import com.opencode.teachingplatform.question.service.QuestionBankCopyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作业内联题 / 实验题项复制到题库的入口。
 *
 * <p>控制器只做参数解析与转发，权限与内容完整性校验由 {@link QuestionBankCopyService} 完成。</p>
 */
@RestController
@RequestMapping("/api/v1/teacher")
public class QuestionBankCopyController {

    private final QuestionBankCopyService questionBankCopyService;

    public QuestionBankCopyController(QuestionBankCopyService questionBankCopyService) {
        this.questionBankCopyService = questionBankCopyService;
    }

    @PostMapping("/homework-questions/{homeworkQuestionId}/copy-to-bank")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> copyHomeworkQuestionToBank(
            @PathVariable Long homeworkQuestionId,
            @Valid @RequestBody(required = false) CopyQuestionToBankRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(questionBankCopyService.copyFromHomework(currentUser, homeworkQuestionId, resolveCode(request)));
    }

    @PostMapping("/lab-steps/{stepId}/copy-to-bank")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> copyLabStepToBank(
            @PathVariable Long stepId,
            @Valid @RequestBody(required = false) CopyQuestionToBankRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(questionBankCopyService.copyFromLabStep(currentUser, stepId, resolveCode(request)));
    }

    private String resolveCode(CopyQuestionToBankRequest request) {
        return request == null ? null : request.code();
    }
}
