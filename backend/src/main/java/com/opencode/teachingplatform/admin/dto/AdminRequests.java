package com.opencode.teachingplatform.admin.dto;

import com.opencode.teachingplatform.common.enums.ClassStatus;
import com.opencode.teachingplatform.common.enums.ClassTeacherRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AdminRequests {

    private AdminRequests() {
    }

    public record UpdateSchoolRequest(
            @NotBlank(message = "学校名称不能为空")
            @Size(min = 1, max = 128, message = "学校名称长度必须在1到128个字符之间")
            String name
    ) {
    }

    public record CreateTeacherRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "姓名不能为空") String displayName,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    public record UpdateTeacherRequest(
            @NotBlank(message = "姓名不能为空") String displayName
    ) {
    }

    public record ChangeTeacherStatusRequest(
            @NotBlank(message = "状态不能为空") String status
    ) {
    }

    public record ClassTeacherItem(
            @NotNull(message = "教师不能为空") Long teacherUserId,
            @NotNull(message = "角色不能为空") ClassTeacherRole role,
            Long subjectId
    ) {
    }

    public record CreateSubjectRequest(
            @NotBlank(message = "学科名称不能为空")
            @Size(min = 1, max = 128, message = "学科名称长度必须在1到128个字符之间")
            String name,
            @NotBlank(message = "学科编码不能为空")
            @Size(min = 1, max = 64, message = "学科编码长度必须在1到64个字符之间")
            String code
    ) {
    }

    public record UpdateSubjectRequest(
            @NotBlank(message = "学科名称不能为空")
            @Size(min = 1, max = 128, message = "学科名称长度必须在1到128个字符之间")
            String name,
            @NotBlank(message = "学科编码不能为空")
            @Size(min = 1, max = 64, message = "学科编码长度必须在1到64个字符之间")
            String code
    ) {
    }

    public record ChangeSubjectStatusRequest(
            @NotBlank(message = "状态不能为空") String status
    ) {
    }

    public record CreateClassRequest(
            @NotBlank(message = "班级名称不能为空") @Size(max = 128) String name,
            @NotBlank(message = "班级编码不能为空") @Size(max = 64) String code,
            @NotEmpty(message = "班级至少需要绑定一名教师") List<@Valid ClassTeacherItem> teachers
    ) {
    }

    public record UpdateClassRequest(
            @NotBlank(message = "班级名称不能为空") @Size(max = 128) String name,
            @NotNull(message = "班级状态不能为空") ClassStatus status,
            @NotEmpty(message = "班级至少需要绑定一名教师") List<@Valid ClassTeacherItem> teachers
    ) {
    }

    public record ChangeStudentStatusRequest(
            @NotBlank(message = "状态不能为空") String status
    ) {
    }

    public record CreateStudentRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "姓名不能为空") String displayName,
            @NotBlank(message = "密码不能为空") String password,
            @NotNull(message = "班级不能为空") Long classId
    ) {
    }

    public record UpdateStudentRequest(
            @NotBlank(message = "姓名不能为空") String displayName,
            @NotNull(message = "班级不能为空") Long classId
    ) {
    }

    public record ResetStudentPasswordRequest(
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 32, message = "密码长度必须在6到32个字符之间")
            String password
    ) {
    }
}
