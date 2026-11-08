package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateSchoolRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSchoolStatusRequest;
import com.opencode.teachingplatform.admin.dto.PlatformSchoolItem;
import com.opencode.teachingplatform.admin.service.PlatformSchoolService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台超管的学校管理入口，与校管端 {@code /api/v1/admin/school} 不是同一资源。
 */
@RestController
@RequestMapping("/api/v1/admin/schools")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformSchoolController {

    private final PlatformSchoolService platformSchoolService;

    public PlatformSchoolController(PlatformSchoolService platformSchoolService) {
        this.platformSchoolService = platformSchoolService;
    }

    @GetMapping
    public ApiResponse<List<PlatformSchoolItem>> list() {
        return ApiResponse.ok(platformSchoolService.listSchools());
    }

    @PostMapping
    public ApiResponse<PlatformSchoolItem> create(@Valid @RequestBody CreateSchoolRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(platformSchoolService.createSchool(currentUser, request));
    }

    @PostMapping("/{schoolId}/status")
    public ApiResponse<PlatformSchoolItem> updateStatus(
            @PathVariable Long schoolId,
            @Valid @RequestBody UpdateSchoolStatusRequest request
    ) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(platformSchoolService.updateSchoolStatus(currentUser, schoolId, request));
    }
}
