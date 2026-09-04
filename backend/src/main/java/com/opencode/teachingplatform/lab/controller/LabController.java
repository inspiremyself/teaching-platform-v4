package com.opencode.teachingplatform.lab.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.service.LabService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
/**
 * 实验模块统一入口。
 *
 * <p>这一层基本不承载复杂业务，而是负责三件事：
 * 1. 定义前后端交互时使用的 HTTP 路由；
 * 2. 通过 {@code @PreAuthorize} 做教师 / 学生角色限制；
 * 3. 从安全上下文拿到 {@link CurrentUser} 后，把请求转发给 {@link LabService}。</p>
 *
 * <p>因此向老师讲解时，可以把本类理解为“实验模块的 API 门面层”，
 * 真正的状态流转、评分和落库都在 service 层完成。</p>
 */
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    @GetMapping("/teacher/labs")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherLabs() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listTeacherLabs(currentUser));
    }

    @PostMapping("/teacher/labs")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> createLab(@Valid @RequestBody LabRequests.CreateLabRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.createLab(currentUser, request));
    }

    @PutMapping("/teacher/labs/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> updateLab(@PathVariable Long id, @Valid @RequestBody LabRequests.UpdateLabRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.updateLab(currentUser, id, request));
    }

    @PutMapping("/teacher/labs/{id}/status")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> changeLabStatus(@PathVariable Long id, @Valid @RequestBody LabRequests.ChangeLabStatusRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.changeLabStatus(currentUser, id, request));
    }

    @GetMapping("/teacher/labs/{id}/steps")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherSteps(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listTeacherSteps(currentUser, id));
    }

    @GetMapping("/teacher/labs/{id}/blank-items")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listBlankItems(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listBlankItems(currentUser, id));
    }

    @GetMapping("/teacher/labs/{id}/blank-items/{itemId}/answer-distribution")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getBlankItemAnswerDistribution(@PathVariable Long id, @PathVariable Long itemId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getBlankItemAnswerDistribution(currentUser, id, itemId));
    }

    @PostMapping("/teacher/labs/{id}/blank-items/accepted-answers")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> saveBlankAcceptedAnswers(@PathVariable Long id,
                                                   @Valid @RequestBody LabRequests.SaveBlankAcceptedAnswersRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.saveBlankAcceptedAnswers(currentUser, id, request));
    }

    @PostMapping("/teacher/labs/{id}/steps")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> createStep(@PathVariable Long id, @Valid @RequestBody LabRequests.CreateStepRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.createStep(currentUser, id, request));
    }

    @PutMapping("/teacher/lab-steps/{stepId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> updateStep(@PathVariable Long stepId, @Valid @RequestBody LabRequests.UpdateStepRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.updateStep(currentUser, stepId, request));
    }

    @DeleteMapping("/teacher/lab-steps/{stepId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> deleteStep(@PathVariable Long stepId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.deleteStep(currentUser, stepId));
    }

    @GetMapping("/teacher/lab-reports")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherReports(@ModelAttribute LabRequests.TeacherLabReportQuery query) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listTeacherReports(currentUser, query));
    }

    @GetMapping("/teacher/lab-reports/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherReportDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getTeacherReportDetail(currentUser, id));
    }

    @GetMapping("/teacher/labs/{labId}/report-view/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherLabReportView(@PathVariable Long labId, @PathVariable Long studentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getTeacherLabReportView(currentUser, labId, studentId));
    }

    @GetMapping("/student/labs")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> listStudentLabs() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listStudentLabs(currentUser));
    }

    @GetMapping("/student/labs/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> getStudentLabDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getStudentLabDetail(currentUser, id));
    }

    @PostMapping("/student/labs/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    /**
     * 学生正式提交实验。
     *
     * <p>与 saveAnswer 不同，这一步会触发实验评分链路：
     * 先把提交状态改为 SUBMITTED，再由 service 逐步骤调用自动评分支持类，
     * 最后汇总整份实验的总分和状态。</p>
     */
    public ApiResponse<?> submitLab(@PathVariable Long id, @Valid @RequestBody LabRequests.SubmitLabRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.submitLab(currentUser, id, request));
    }

    @PostMapping("/student/labs/{id}/answers/{stepId}")
    @PreAuthorize("hasRole('STUDENT')")
    /**
     * 学生保存某一步骤的答案。
     *
     * <p>这里对应前端的“暂存/自动保存”动作：service 会确保存在一条实验提交记录，
     * 然后把当前步骤答案写入 experiment_answer，并额外保存一份日志快照。</p>
     */
    public ApiResponse<?> saveAnswer(@PathVariable Long id, @PathVariable Long stepId, @Valid @RequestBody LabRequests.SaveStepAnswerRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.saveAnswer(currentUser, id, stepId, request));
    }

    @PostMapping(value = "/student/labs/{id}/answers/{stepId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> uploadAnswerImage(@PathVariable Long id,
                                            @PathVariable Long stepId,
                                            @RequestPart("file") MultipartFile file) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.uploadAnswerImage(currentUser, id, stepId, file));
    }

    @GetMapping("/labs/answer-images")
    public ResponseEntity<ByteArrayResource> previewAnswerImage(@RequestParam("path") String path) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        LabService.PreviewAnswerImageResult result = labService.previewAnswerImage(currentUser, path);
        MediaType mediaType = MediaType.parseMediaType(result.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new ByteArrayResource(result.bytes()));
    }

    @PostMapping("/teacher/lab-reports/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    /**
     * 教师对整份实验报告进行批改。
     *
     * <p>这条链路适合讲解“教师端评分如何回写数据库”：
     * service 会校验评分项覆盖范围，逐条更新步骤答案分数，最后刷新实验提交总分并写入成绩记录。</p>
     */
    public ApiResponse<?> gradeReport(@PathVariable Long id, @Valid @RequestBody LabRequests.GradeLabReportRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.gradeReport(currentUser, id, request));
    }

    @PostMapping("/teacher/lab-reports/{id}/confirm-step-score")
    @PreAuthorize("hasRole('TEACHER')")
    /**
     * 教师确认某一步骤的最终分数。
     *
     * <p>既支持直接采纳系统建议分，也支持教师手工改分，
     * 适合说明“自动评分 + 人工确认”的双阶段设计。</p>
     */
    public ApiResponse<?> confirmStepScore(@PathVariable Long id, @Valid @RequestBody LabRequests.ConfirmStepScoreRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.confirmStepScore(currentUser, id, request));
    }

    @PostMapping("/teacher/labs/{id}/release-scores")
    @PreAuthorize("hasRole('TEACHER')")
    /**
     * 教师手动发布实验成绩。
     *
     * <p>是否对学生可见不仅取决于是否评分完成，还取决于实验配置里的分数可见性策略。</p>
     */
    public ApiResponse<?> releaseScores(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.releaseScores(currentUser, id));
    }
}
