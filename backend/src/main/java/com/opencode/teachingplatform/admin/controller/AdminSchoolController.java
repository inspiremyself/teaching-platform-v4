package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSchoolRequest;
import com.opencode.teachingplatform.admin.service.AdminSchoolService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/school")
@Validated
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class AdminSchoolController {

    private final AdminSchoolService adminSchoolService;

    public AdminSchoolController(AdminSchoolService adminSchoolService) {
        this.adminSchoolService = adminSchoolService;
    }

    @GetMapping
    public ApiResponse<?> get() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminSchoolService.getSchool(currentUser));
    }

    @PutMapping
    public ApiResponse<?> update(@Valid @RequestBody UpdateSchoolRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminSchoolService.updateSchool(currentUser, request));
    }
}
