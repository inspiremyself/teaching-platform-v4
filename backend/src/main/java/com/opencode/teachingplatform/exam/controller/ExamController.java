package com.opencode.teachingplatform.exam.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.exam.service.ExamService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping("/exams")
    public ApiResponse<?> list() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.listExams(currentUser));
    }

    @PostMapping("/exams")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> create(@Valid @RequestBody ExamRequests.CreateExamRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.createExam(currentUser, request));
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody ExamRequests.UpdateExamRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.updateExam(currentUser, id, request));
    }

    @GetMapping("/exams/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<?> detail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.getExamDetail(currentUser, id));
    }

    @PostMapping("/teacher/exams/{id}/status")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> changeStatus(@PathVariable Long id, @Valid @RequestBody ExamRequests.ChangeExamStatusRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.changeStatus(currentUser, id, request));
    }

    @PostMapping("/exams/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> start(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.startExam(currentUser, id));
    }

    @PostMapping("/exams/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> submit(@PathVariable Long id, @Valid @RequestBody ExamRequests.SubmitExamRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.submitExam(currentUser, id, request));
    }

    /**
     * 保存考试草稿：仅学生、仅 IN_PROGRESS 提交可写。
     * 请求体与交卷一致，草稿只落 answer_json，不评分、不改提交状态。
     */
    @PutMapping("/exams/{id}/draft")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> saveDraft(@PathVariable Long id,
                                    @Valid @RequestBody ExamRequests.SubmitExamRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.saveDraft(currentUser, id, request));
    }

    @GetMapping("/teacher/exams/{id}/results")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> examResults(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.getExamResults(currentUser, id));
    }

    @GetMapping("/teacher/exam-submissions/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> submissionDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.getSubmissionDetail(currentUser, id));
    }

    @PostMapping("/teacher/exam-submissions/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> gradeSubmission(@PathVariable Long id, @Valid @RequestBody ExamRequests.GradeExamSubmissionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.gradeSubmission(currentUser, id, request));
    }

    @PostMapping("/teacher/exam-submissions/{id}/confirm-answer")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> confirmAnswer(@PathVariable Long id, @Valid @RequestBody ExamRequests.ConfirmExamAnswerRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.confirmAnswer(currentUser, id, request));
    }

    @PostMapping("/teacher/exams/{id}/release-scores")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> releaseScores(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(examService.releaseScores(currentUser, id));
    }

}
