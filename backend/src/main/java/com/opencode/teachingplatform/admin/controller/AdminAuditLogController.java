package com.opencode.teachingplatform.admin.controller;

import com.opencode.teachingplatform.admin.service.AdminAuditLogQueryService;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditLogQueryService adminAuditLogQueryService;

    public AdminAuditLogController(AdminAuditLogQueryService adminAuditLogQueryService) {
        this.adminAuditLogQueryService = adminAuditLogQueryService;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ApiResponse.ok(adminAuditLogQueryService.list(
                SecurityUtils.currentUser(),
                page,
                pageSize,
                action,
                resourceType,
                userId,
                from,
                to
        ));
    }
}
