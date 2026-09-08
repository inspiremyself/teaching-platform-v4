package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateClassRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateClassRequest;
import com.opencode.teachingplatform.admin.service.AdminClassService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/classes")
@Validated
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class AdminClassController {

    private final AdminClassService adminClassService;

    public AdminClassController(AdminClassService adminClassService) {
        this.adminClassService = adminClassService;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) Long teacherUserId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminClassService.listClasses(currentUser, teacherUserId));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminClassService.getClass(currentUser, id));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateClassRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminClassService.createClass(currentUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateClassRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminClassService.updateClass(currentUser, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        adminClassService.deleteClass(currentUser, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
