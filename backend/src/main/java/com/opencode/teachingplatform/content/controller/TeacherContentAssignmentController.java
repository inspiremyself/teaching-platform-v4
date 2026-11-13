package com.opencode.teachingplatform.content.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateCoursewareAssignmentRequest;
import com.opencode.teachingplatform.content.dto.ContentViews.CreateAssignmentResult;
import com.opencode.teachingplatform.content.service.CoursewareAssignmentWriteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师课件布置端点族：只提供一次布置 POST，按班读取记录在 {@link TeacherContentController}。
 *
 * <p>类级鉴权覆盖全部写入动作；请求体之外的鉴权与可用性判定由 Service 在事务内完成。</p>
 */
@RestController
@RequestMapping("/api/v1/teacher/content/assignments")
@Validated
@PreAuthorize("hasRole('TEACHER')")
public class TeacherContentAssignmentController {

    private final CoursewareAssignmentWriteService coursewareAssignmentWriteService;

    public TeacherContentAssignmentController(CoursewareAssignmentWriteService coursewareAssignmentWriteService) {
        this.coursewareAssignmentWriteService = coursewareAssignmentWriteService;
    }

    @PostMapping
    public ApiResponse<CreateAssignmentResult> create(
            @Valid @RequestBody CreateCoursewareAssignmentRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(coursewareAssignmentWriteService.create(currentUser, request));
    }
}
