package com.opencode.teachingplatform.clazz.controller;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.clazz.service.ClassService;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.common.api.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/classes")
@Validated
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<PagedResult<?>> list() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(classService.listTeacherClasses(currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> get(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(classService.getClass(currentUser, id));
    }
}
