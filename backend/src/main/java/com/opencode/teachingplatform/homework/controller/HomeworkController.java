package com.opencode.teachingplatform.homework.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.SecurityUtils;
import com.opencode.teachingplatform.common.api.ApiResponse;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.homework.dto.HomeworkRequests;
import com.opencode.teachingplatform.homework.service.HomeworkService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final ObjectMapper objectMapper;

    public HomeworkController(HomeworkService homeworkService, ObjectMapper objectMapper) {
        this.homeworkService = homeworkService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/teacher/homeworks")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherHomeworks() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.listTeacherHomeworks(currentUser));
    }

    @PostMapping("/teacher/homeworks")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> createHomework(@Valid @RequestBody HomeworkRequests.CreateHomeworkRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.createHomework(currentUser, request));
    }

    @PutMapping("/teacher/homeworks/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> updateHomework(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.UpdateHomeworkRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.updateHomework(currentUser, id, request));
    }

    @GetMapping("/teacher/homeworks/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherHomeworkDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.getTeacherHomeworkDetail(currentUser, id));
    }

    @PostMapping("/teacher/homeworks/{id}/status")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> changeHomeworkStatus(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.ChangeHomeworkStatusRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.changeHomeworkStatus(currentUser, id, request));
    }

    @PostMapping("/teacher/homeworks/{id}/questions/bank")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> addQuestionFromBank(@PathVariable Long id, HttpServletRequest servletRequest) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        HomeworkRequests.AddHomeworkQuestionFromBankRequest request = toAddHomeworkQuestionFromBankRequest(readRequestBody(servletRequest));
        return ApiResponse.ok(homeworkService.addQuestionFromBank(currentUser, id, request));
    }

    @PostMapping("/teacher/homeworks/{id}/questions/inline")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> addInlineQuestion(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.SaveHomeworkQuestionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.addInlineQuestion(currentUser, id, request));
    }

    @PutMapping("/teacher/homeworks/{id}/questions/{homeworkQuestionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> updateQuestion(@PathVariable Long id,
                                         @PathVariable Long homeworkQuestionId,
                                         @Valid @RequestBody HomeworkRequests.SaveHomeworkQuestionRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.updateQuestion(currentUser, id, homeworkQuestionId, request));
    }

    @DeleteMapping("/teacher/homeworks/{id}/questions/{homeworkQuestionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> deleteQuestion(@PathVariable Long id, @PathVariable Long homeworkQuestionId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.deleteQuestion(currentUser, id, homeworkQuestionId));
    }

    @PostMapping("/teacher/homeworks/{id}/questions/bank-debug")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> addQuestionFromBankDebug(@PathVariable Long id, HttpServletRequest servletRequest) {
        Map<String, Object> requestBody = readRequestBody(servletRequest);
        return ApiResponse.ok(Map.of(
                "homeworkId", id,
                "questionId", toLong(requestBody.get("questionId"), "questionId"),
                "sortOrder", toInteger(requestBody.get("sortOrder"), "sortOrder"),
                "questionScore", toDouble(requestBody.get("questionScore"), "questionScore")
        ));
    }

    private Map<String, Object> readRequestBody(HttpServletRequest servletRequest) {
        try {
            return objectMapper.readValue(servletRequest.getInputStream(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException ex) {
            throw new BusinessException(40000, "请求体 JSON 格式不正确");
        }
    }

    private HomeworkRequests.AddHomeworkQuestionFromBankRequest toAddHomeworkQuestionFromBankRequest(Map<String, Object> requestBody) {
        Long questionId = toLong(requestBody.get("questionId"), "questionId");
        Integer sortOrder = toInteger(requestBody.get("sortOrder"), "sortOrder");
        Double questionScore = toDouble(requestBody.get("questionScore"), "questionScore");
        return new HomeworkRequests.AddHomeworkQuestionFromBankRequest(questionId, sortOrder, questionScore);
    }

    private Long toLong(Object value, String field) {
        if (value == null) {
            throw new BusinessException(40000, field + " 不能为空");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException(40000, field + " 格式不正确");
        }
    }

    private Integer toInteger(Object value, String field) {
        if (value == null) {
            throw new BusinessException(40000, field + " 不能为空");
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException(40000, field + " 格式不正确");
        }
    }

    private Double toDouble(Object value, String field) {
        if (value == null) {
            throw new BusinessException(40000, field + " 不能为空");
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException(40000, field + " 格式不正确");
        }
    }

    @GetMapping("/teacher/homeworks/{id}/submissions")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> listTeacherSubmissions(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.listTeacherSubmissions(currentUser, id));
    }

    @GetMapping("/teacher/homework-submissions/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> getTeacherSubmissionDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.getTeacherSubmissionDetail(currentUser, id));
    }

    @PostMapping("/teacher/homework-submissions/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> gradeHomeworkSubmission(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.GradeHomeworkRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.gradeSubmission(currentUser, id, request));
    }

    /**
     * 教师打回作业提交：状态回到可编辑草稿态，清除评分痕迹并保留作答。
     */
    @PostMapping("/teacher/homework-submissions/{id}/return")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> returnHomeworkSubmission(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.returnHomeworkSubmission(currentUser, id));
    }

    @PostMapping("/teacher/homework-answers/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> gradeHomeworkAnswer(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.GradeHomeworkAnswerRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.gradeAnswer(currentUser, id, request));
    }

    @PostMapping("/teacher/homeworks/{id}/release-scores")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<?> releaseScores(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.releaseScores(currentUser, id));
    }

    @GetMapping("/student/homeworks")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> listStudentHomeworks() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.listStudentHomeworks(currentUser));
    }

    @GetMapping("/student/homeworks/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> getStudentHomeworkDetail(@PathVariable Long id) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.getStudentHomeworkDetail(currentUser, id));
    }

    @PostMapping("/student/homeworks/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> submitHomework(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.SubmitHomeworkRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.submitHomework(currentUser, id, request));
    }

    @PutMapping("/student/homeworks/{id}/draft")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<?> saveHomeworkDraft(@PathVariable Long id, @Valid @RequestBody HomeworkRequests.SubmitHomeworkRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(homeworkService.saveHomeworkDraft(currentUser, id, request));
    }
}
