package com.opencode.teachingplatform.plagiarism.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plagiarism/tasks")
/**
 * 查重任务接口入口。
 *
 * <p>这里暴露的是“查重任务查询与教师复核”能力，而不是查重算法本身。
 * 真正的查重计算发生在作业/实验等业务提交流程里，结果再汇总到 plagiarism_task 供本控制器查询。</p>
 */
public class PlagiarismController {

    public record ReviewPlagiarismRequest(
            String reviewConclusion,
            @NotBlank String reviewComment
    ) {
    }

    private final PlagiarismService plagiarismService;

    public PlagiarismController(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> list() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(plagiarismService.listTasks(currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<?> detail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(plagiarismService.getTaskDetail(currentUser, id));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> review(@PathVariable Long id, @Valid @RequestBody ReviewPlagiarismRequest payload) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(plagiarismService.reviewTask(currentUser, id, payload.reviewConclusion(), payload.reviewComment()));
    }
}
