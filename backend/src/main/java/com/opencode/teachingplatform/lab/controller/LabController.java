package com.opencode.teachingplatform.lab.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.lab.behavior.dto.BehaviorRequests;
import com.opencode.teachingplatform.lab.behavior.service.BehaviorIngestService;
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
    private final BehaviorIngestService behaviorIngestService;

    public LabController(LabService labService, BehaviorIngestService behaviorIngestService) {
        this.labService = labService;
        this.behaviorIngestService = behaviorIngestService;
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

    @PostMapping("/teacher/labs/{sourceLabId}/copy")
    @PreAuthorize("hasRole('TEACHER')")
    /**
     * 从已有实验深度拷贝创建新实验。
     *
     * <p>复制主表配置、全部题项与填空题可接受答案；学生作答、成绩等运行数据不迁移。
     * 请求体只需目标班级、可选的新名称与发布状态，其余字段由服务端从源实验复制。</p>
     */
    public ApiResponse<?> copyLab(@PathVariable Long sourceLabId,
                                  @Valid @RequestBody LabRequests.CopyLabRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.copyLab(currentUser, sourceLabId, request));
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

    /**
     * 教师端实验报告队列（分页 + 筛选）。
     *
     * <p>返回 {@code PagedResult}（items / page / pageSize / total），
     * 由前端队列页渲染分页表格使用。</p>
     */
    @GetMapping("/teacher/lab-reports")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherReports(@ModelAttribute LabRequests.TeacherLabReportQuery query) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listTeacherReports(currentUser, query));
    }

    /**
     * 教师端实验报告导航队列（全量、无分页）。
     *
     * <p>供报告详情页 / 批量批改页构建"上一份 / 下一份"跳转队列，
     * 语义上必须看到全部报告，因此不能复用分页接口。</p>
     */
    @GetMapping("/teacher/lab-reports/queue")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherReportQueue() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listTeacherReportQueue(currentUser));
    }

    /**
     * 教师端实验报告全局统计。
     *
     * <p>返回 {@code {total, submitted, graded}}，是教师维度的全量口径，
     * 不受队列页任何筛选条件影响，用于头部汇总卡。</p>
     */
    @GetMapping("/teacher/lab-reports/stats")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherReportStats() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getTeacherReportStats(currentUser));
    }

    @GetMapping("/teacher/lab-reports/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherReportDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getTeacherReportDetail(currentUser, id));
    }

    /**
     * 教师查看单份报告的行为详情（metrics 全量 + 行为时间线）。
     *
     * <p>独立于报告详情接口：时间线按需加载，避免膨胀报告详情契约（方案 D11）。</p>
     */
    @GetMapping("/teacher/lab-reports/{id}/behavior")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherReportBehavior(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getTeacherReportBehavior(currentUser, id));
    }

    @GetMapping("/teacher/labs/{labId}/report-view/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherLabReportView(@PathVariable Long labId, @PathVariable Long studentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getTeacherLabReportView(currentUser, labId, studentId));
    }

    @GetMapping("/teacher/labs/{labId}/submission-overview")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getSubmissionOverview(@PathVariable Long labId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getSubmissionOverview(currentUser, labId));
    }

    @GetMapping("/teacher/students/{studentId}/lab-submissions")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listStudentLabSubmissions(@PathVariable Long studentId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.listStudentLabSubmissions(currentUser, studentId));
    }

    @GetMapping("/teacher/labs/{labId}/items/{itemId}/answers")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getLabItemAnswers(@PathVariable Long labId, @PathVariable Long itemId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.getLabItemAnswers(currentUser, labId, itemId));
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

    /**
     * 学生批量上报作答行为事件（行为采集链唯一入口，方案 §5.1）。
     *
     * <p>逐条容错：非法条目计入 dropped 而不整批失败；实验已关闭时返回
     * {@code closed:true} 让前端停采；event_uuid 幂等，重试不产生重复行。</p>
     */
    @PostMapping("/student/labs/{id}/behavior-events")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> reportBehaviorEvents(@PathVariable Long id,
                                               @RequestBody BehaviorRequests.IngestBatch batch) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(behaviorIngestService.ingest(currentUser, id, batch));
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

    /**
     * 教师将单条已提交或已批改实验报告打回，使学生可继续编辑并重新提交。
     */
    @PostMapping("/teacher/lab-reports/{id}/return")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> returnReport(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(labService.returnReport(currentUser, id));
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
