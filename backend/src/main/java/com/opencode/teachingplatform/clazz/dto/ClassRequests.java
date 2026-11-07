package com.opencode.teachingplatform.clazz.dto;

import com.opencode.teachingplatform.clazz.ClassStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ClassRequests {

    private ClassRequests() {
    }

    public record CreateClassRequest(
            @NotBlank(message = "班级名称不能为空")
            @Size(max = 128, message = "班级名称长度不能超过128个字符")
            String name,
            @NotBlank(message = "班级编码不能为空")
            @Size(max = 64, message = "班级编码长度不能超过64个字符")
            String code
    ) {
    }

    public record UpdateClassRequest(
            @NotBlank(message = "班级名称不能为空")
            @Size(max = 128, message = "班级名称长度不能超过128个字符")
            String name,
            @NotNull(message = "班级状态不能为空") ClassStatus status
    ) {
    }
}
