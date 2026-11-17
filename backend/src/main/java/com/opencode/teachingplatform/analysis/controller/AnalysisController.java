package com.opencode.teachingplatform.analysis.controller;

import com.opencode.teachingplatform.analysis.service.AnalysisService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> dashboard() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(analysisService.getDashboard(currentUser));
    }

    @GetMapping("/teacher-overview")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> teacherOverview() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(analysisService.getTeacherOverview(currentUser));
    }

    @GetMapping("/teacher/participation/{businessType}/{businessId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> teacherParticipation(@PathVariable String businessType, @PathVariable Long businessId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(analysisService.getTeacherParticipation(currentUser, businessType, businessId));
    }

    @GetMapping("/my-scores")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> myScores() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(analysisService.getMyScores(currentUser));
    }

    @GetMapping("/my-score-overview")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> myScoreOverview() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(analysisService.getMyScoreOverview(currentUser));
    }

    @GetMapping("/student-dashboard")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> studentDashboard() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(analysisService.getStudentDashboard(currentUser));
    }
}
