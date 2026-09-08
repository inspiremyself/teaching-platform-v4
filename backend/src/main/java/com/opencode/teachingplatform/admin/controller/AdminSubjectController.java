package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeSubjectStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateSubjectRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateSubjectRequest;
import com.opencode.teachingplatform.admin.service.AdminSubjectService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/subjects")
@Validated
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class AdminSubjectController {

    private final AdminSubjectService adminSubjectService;

    public AdminSubjectController(AdminSubjectService adminSubjectService) {
        this.adminSubjectService = adminSubjectService;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String status) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminSubjectService.listSubjects(currentUser, status));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateSubjectRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminSubjectService.createSubject(currentUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateSubjectRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminSubjectService.updateSubject(currentUser, id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<?> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeSubjectStatusRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminSubjectService.changeStatus(currentUser, id, request));
    }
}
