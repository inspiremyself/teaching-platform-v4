package com.opencode.teachingplatform.content.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.content.dto.ContentRequests.ChangeSubjectStatusRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreatePlatformSubjectRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateTextbookRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateTextbookSectionRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.GenerateCoursewareRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.GrantSchoolsRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateCoursewareRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdatePlatformSubjectRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateTextbookRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateTextbookSectionRequest;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareAssetItem;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareDetail;
import com.opencode.teachingplatform.content.dto.ContentViews.CoursewareItem;
import com.opencode.teachingplatform.content.dto.ContentViews.DownloadedAsset;
import com.opencode.teachingplatform.content.dto.ContentViews.PlatformSubjectItem;
import com.opencode.teachingplatform.content.dto.ContentViews.ShareGrantItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookItem;
import com.opencode.teachingplatform.content.dto.ContentViews.TextbookSectionItem;
import com.opencode.teachingplatform.content.service.CoursewareGenerationService;
import com.opencode.teachingplatform.content.service.PlatformCatalogService;
import com.opencode.teachingplatform.content.service.PlatformContentService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 平台内容端点族：平台超管制作 / 发布 / 授权 + 平台学科字典与教材目录维护。
 *
 * <p>类级鉴权覆盖 create / update / delete 兄弟操作；本类只取参、取当前用户、转发 Service。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/platform-content")
@Validated
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformContentController {

    private final PlatformContentService platformContentService;
    private final PlatformCatalogService platformCatalogService;
    private final CoursewareGenerationService coursewareGenerationService;

    public PlatformContentController(PlatformContentService platformContentService,
                                     PlatformCatalogService platformCatalogService,
                                     CoursewareGenerationService coursewareGenerationService) {
        this.platformContentService = platformContentService;
        this.platformCatalogService = platformCatalogService;
        this.coursewareGenerationService = coursewareGenerationService;
    }

    @GetMapping("/subjects")
    public ApiResponse<List<PlatformSubjectItem>> listSubjects() {
        return ApiResponse.ok(platformCatalogService.listSubjects());
    }

    @PostMapping("/subjects")
    public ApiResponse<PlatformSubjectItem> createSubject(@Valid @RequestBody CreatePlatformSubjectRequest request) {
        return ApiResponse.ok(platformCatalogService.createSubject(request));
    }

    @PutMapping("/subjects/{subjectId}")
    public ApiResponse<PlatformSubjectItem> updateSubject(@PathVariable Long subjectId,
                                                          @Valid @RequestBody UpdatePlatformSubjectRequest request) {
        return ApiResponse.ok(platformCatalogService.updateSubject(subjectId, request));
    }

    @PostMapping("/subjects/{subjectId}/status")
    public ApiResponse<PlatformSubjectItem> changeSubjectStatus(@PathVariable Long subjectId,
                                                                 @Valid @RequestBody ChangeSubjectStatusRequest request) {
        return ApiResponse.ok(platformCatalogService.changeSubjectStatus(subjectId, request));
    }

    @GetMapping("/textbooks")
    public ApiResponse<List<TextbookItem>> listTextbooks(@RequestParam(required = false) Long globalSubjectId) {
        return ApiResponse.ok(platformCatalogService.listTextbooks(globalSubjectId));
    }

    @PostMapping("/textbooks")
    public ApiResponse<TextbookItem> createTextbook(@Valid @RequestBody CreateTextbookRequest request) {
        return ApiResponse.ok(platformCatalogService.createTextbook(request));
    }

    @PutMapping("/textbooks/{textbookId}")
    public ApiResponse<TextbookItem> updateTextbook(@PathVariable Long textbookId,
                                                     @Valid @RequestBody UpdateTextbookRequest request) {
        return ApiResponse.ok(platformCatalogService.updateTextbook(textbookId, request));
    }

    @GetMapping("/textbooks/{textbookId}/sections")
    public ApiResponse<List<TextbookSectionItem>> listTextbookSections(@PathVariable Long textbookId) {
        return ApiResponse.ok(platformCatalogService.listSections(textbookId));
    }

    @PostMapping("/textbooks/{textbookId}/sections")
    public ApiResponse<TextbookSectionItem> createTextbookSection(@PathVariable Long textbookId,
                                                                   @Valid @RequestBody CreateTextbookSectionRequest request) {
        return ApiResponse.ok(platformCatalogService.createSection(textbookId, request));
    }

    @PutMapping("/textbooks/{textbookId}/sections/{sectionId}")
    public ApiResponse<TextbookSectionItem> updateTextbookSection(@PathVariable Long textbookId,
                                                                   @PathVariable Long sectionId,
                                                                   @Valid @RequestBody UpdateTextbookSectionRequest request) {
        return ApiResponse.ok(platformCatalogService.updateSection(textbookId, sectionId, request));
    }

    @PostMapping("/coursewares/generate")
    public ApiResponse<CoursewareDetail> generateCourseware(@Valid @RequestBody GenerateCoursewareRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(coursewareGenerationService.generate(currentUser, request));
    }

    @GetMapping("/coursewares")
    public ApiResponse<List<CoursewareItem>> listCoursewares() {
        return ApiResponse.ok(platformContentService.list());
    }

    @GetMapping("/coursewares/{coursewareId}")
    public ApiResponse<CoursewareDetail> getCourseware(@PathVariable Long coursewareId) {
        return ApiResponse.ok(platformContentService.get(coursewareId));
    }

    @PutMapping("/coursewares/{coursewareId}")
    public ApiResponse<CoursewareDetail> updateCourseware(@PathVariable Long coursewareId,
                                                          @Valid @RequestBody UpdateCoursewareRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(platformContentService.update(currentUser, coursewareId, request));
    }

    @DeleteMapping("/coursewares/{coursewareId}")
    public ApiResponse<Map<String, Boolean>> deleteCourseware(@PathVariable Long coursewareId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        platformContentService.delete(currentUser, coursewareId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/coursewares/{coursewareId}/publish")
    public ApiResponse<CoursewareDetail> publish(@PathVariable Long coursewareId) {
        return ApiResponse.ok(platformContentService.publish(coursewareId));
    }

    @PostMapping("/coursewares/{coursewareId}/unpublish")
    public ApiResponse<CoursewareDetail> unpublish(@PathVariable Long coursewareId) {
        return ApiResponse.ok(platformContentService.unpublish(coursewareId));
    }

    @GetMapping("/coursewares/{coursewareId}/grants")
    public ApiResponse<List<ShareGrantItem>> listGrants(@PathVariable Long coursewareId) {
        return ApiResponse.ok(platformContentService.listGrants(coursewareId));
    }

    @PostMapping("/coursewares/{coursewareId}/grants")
    public ApiResponse<List<ShareGrantItem>> grantSchools(@PathVariable Long coursewareId,
                                                           @Valid @RequestBody GrantSchoolsRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(platformContentService.grant(currentUser, coursewareId, request));
    }

    @DeleteMapping("/coursewares/{coursewareId}/grants/{schoolId}")
    public ApiResponse<Map<String, Boolean>> revokeSchool(@PathVariable Long coursewareId,
                                                            @PathVariable Long schoolId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        platformContentService.revoke(currentUser, coursewareId, schoolId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping(value = "/coursewares/{coursewareId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursewareAssetItem> uploadAsset(@PathVariable Long coursewareId,
                                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(platformContentService.uploadAsset(coursewareId, file));
    }

    @GetMapping("/coursewares/{coursewareId}/assets/{assetId}")
    public ResponseEntity<ByteArrayResource> readAsset(@PathVariable Long coursewareId,
                                                         @PathVariable Long assetId) {
        DownloadedAsset asset = platformContentService.readAsset(coursewareId, assetId);
        String mime = asset.mime() == null || asset.mime().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : asset.mime();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header("Content-Disposition", "inline; filename=\"" + asset.fileName() + "\"")
                .body(new ByteArrayResource(asset.bytes()));
    }

    @GetMapping("/coursewares/{coursewareId}/preview")
    public ApiResponse<CoursewareDetail> previewCourseware(@PathVariable Long coursewareId) {
        return ApiResponse.ok(platformContentService.get(coursewareId));
    }
}
