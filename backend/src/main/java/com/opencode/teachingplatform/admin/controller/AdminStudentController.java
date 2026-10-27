package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.dto.AdminRequests.ChangeStudentStatusRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.CreateStudentRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.ResetStudentPasswordRequest;
import com.opencode.teachingplatform.admin.dto.AdminRequests.UpdateStudentRequest;
import com.opencode.teachingplatform.student.dto.StudentRequests.ImportStudentsRequest;
import com.opencode.teachingplatform.admin.service.AdminStudentService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/students")
@Validated
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class AdminStudentController {

    private final AdminStudentService adminStudentService;

    public AdminStudentController(AdminStudentService adminStudentService) {
        this.adminStudentService = adminStudentService;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) Long classId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminStudentService.listStudents(currentUser, classId));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateStudentRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminStudentService.createStudent(currentUser, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminStudentService.updateStudent(currentUser, id, request));
    }

    @PostMapping("/import")
    public ApiResponse<?> importStudents(@Valid @RequestBody ImportStudentsRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminStudentService.importStudents(currentUser, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<?> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStudentStatusRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminStudentService.changeStatus(currentUser, id, request));
    }

    @PostMapping("/{id}/password")
    public ApiResponse<?> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetStudentPasswordRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(adminStudentService.resetPassword(currentUser, id, request));
    }
}
