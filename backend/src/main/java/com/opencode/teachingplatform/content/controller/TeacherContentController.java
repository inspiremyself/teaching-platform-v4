package com.opencode.teachingplatform.content.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest;
import com.opencode.teachingplatform.content.dto.ContentViews.ContentBrowseItem;
import com.opencode.teachingplatform.content.dto.ContentViews.ContentVisibilityItem;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareSectionItem;
import com.opencode.teachingplatform.content.dto.ContentViews.DownloadedAsset;
import com.opencode.teachingplatform.content.dto.ContentViews.PlatformSubjectItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TeacherAssignmentItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookSectionItem;
import com.opencode.teachingplatform.content.service.CoursewareAssignmentWriteService;
import com.opencode.teachingplatform.content.service.TeacherContentService;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师内容浏览端点族：五类筛选 + 按课件内小节预览 + 按班读取已布置记录。
 *
 * <p>只提供 GET，不含任何写入端点；布置写入动作归阶段 4 的独立 Controller。</p>
 */
@RestController
@RequestMapping("/api/v1/teacher/content")
@Validated
@PreAuthorize("hasRole('TEACHER')")
public class TeacherContentController {

    private final TeacherContentService teacherContentService;
    private final CoursewareAssignmentWriteService coursewareAssignmentWriteService;

    public TeacherContentController(TeacherContentService teacherContentService,
                                    CoursewareAssignmentWriteService coursewareAssignmentWriteService) {
        this.teacherContentService = teacherContentService;
        this.coursewareAssignmentWriteService = coursewareAssignmentWriteService;
    }

    @GetMapping("/coursewares")
    public ApiResponse<List<ContentBrowseItem>> listCoursewares(
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) Long textbookId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long coursewareSectionId,
            @RequestParam(required = false) Long subjectId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        ContentFilterRequest request = new ContentFilterRequest(filterType, textbookId, sectionId,
                coursewareSectionId, subjectId);
        return ApiResponse.ok(teacherContentService.list(currentUser, request));
    }

    @GetMapping("/coursewares/{coursewareId}/sections")
    public ApiResponse<List<CoursewareSectionItem>> listSections(@PathVariable Long coursewareId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(teacherContentService.listSections(currentUser, coursewareId));
    }

    @GetMapping("/coursewares/{coursewareId}/sections/{sectionId}")
    public ApiResponse<CoursewareSectionItem> getSection(@PathVariable Long coursewareId,
                                                          @PathVariable Long sectionId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(teacherContentService.getSection(currentUser, coursewareId, sectionId));
    }

    @GetMapping("/coursewares/{coursewareId}/assets/{assetId}")
    public ResponseEntity<ByteArrayResource> readAsset(@PathVariable Long coursewareId,
                                                         @PathVariable Long assetId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        DownloadedAsset asset = teacherContentService.readAsset(currentUser, coursewareId, assetId);
        String mime = asset.mime() == null || asset.mime().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : asset.mime();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header("Content-Disposition", "inline; filename=\"" + asset.fileName() + "\"")
                .body(new ByteArrayResource(asset.bytes()));
    }

    @GetMapping("/textbooks")
    public ApiResponse<List<TextbookItem>> listTextbooks() {
        return ApiResponse.ok(teacherContentService.listTextbooks());
    }

    @GetMapping("/subjects")
    public ApiResponse<List<PlatformSubjectItem>> listSubjects() {
        return ApiResponse.ok(teacherContentService.listSubjects());
    }

    @GetMapping("/textbooks/{textbookId}/sections")
    public ApiResponse<List<TextbookSectionItem>> listTextbookSections(@PathVariable Long textbookId) {
        return ApiResponse.ok(teacherContentService.listTextbookSections(textbookId));
    }

    @GetMapping("/coursewares/{coursewareId}/visibility")
    public ApiResponse<ContentVisibilityItem> listVisibility(@PathVariable Long coursewareId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(teacherContentService.visibility(currentUser, coursewareId));
    }

    @GetMapping("/assignments")
    public ApiResponse<List<TeacherAssignmentItem>> listAssignments(@RequestParam @NotNull Long classId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(coursewareAssignmentWriteService.listByClass(currentUser, classId));
    }
}
