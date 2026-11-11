package com.opencode.teachingplatform.question.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.content.service.TaskSubjectValidator;
import com.opencode.teachingplatform.question.dto.QuestionRequests.SaveQuestionRequest;
import com.opencode.teachingplatform.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/questions")
@Validated
public class QuestionController {

    private final QuestionService questionService;
    private final TaskSubjectValidator taskSubjectValidator;

    public QuestionController(QuestionService questionService, TaskSubjectValidator taskSubjectValidator) {
        this.questionService = questionService;
        this.taskSubjectValidator = taskSubjectValidator;
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> list() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(questionService.list(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> create(@Valid @RequestBody SaveQuestionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        taskSubjectValidator.requireForCreate(currentUser, request.subjectId());
        return ApiResponse.ok(questionService.create(currentUser, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody SaveQuestionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        taskSubjectValidator.requireForUpdate(currentUser, request.subjectId());
        return ApiResponse.ok(questionService.update(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> delete(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        questionService.delete(currentUser, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
