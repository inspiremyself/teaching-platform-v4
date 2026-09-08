package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeTeacherStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateTeacherRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateTeacherRequest;
import com.opencode.teachingplatform.admin.service.AdminTeacherService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/teachers")
@Validated
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class AdminTeacherController {

    private final AdminTeacherService adminTeacherService;

    public AdminTeacherController(AdminTeacherService adminTeacherService) {
        this.adminTeacherService = adminTeacherService;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String status) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminTeacherService.listTeachers(currentUser, status));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateTeacherRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminTeacherService.createTeacher(currentUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateTeacherRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminTeacherService.updateTeacher(currentUser, id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<?> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeTeacherStatusRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminTeacherService.changeStatus(currentUser, id, request));
    }
}
