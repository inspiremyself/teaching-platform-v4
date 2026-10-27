package com.opencode.teachingplatform.lab.dto;

import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class LabRequests {

    private LabRequests() {
    }

    public record CreateLabRequest(
            @NotBlank String title,
            String description,
            String experimentContent,
            Integer experimentType,
            @NotNull Long classId,
            String status,
            Long materialId,
            ScoreVisibilityMode scoreVisibilityMode,
            Boolean summaryRequired
    ) {
    }

    /**
     * 从已有实验深度拷贝的请求体。
     *
     * <p>只承载「目标班级 / 新实验名称 / 发布状态」三个可选项，
     * 其余字段（实验要求、内容、类型、题项、填空答案等）由服务端从源实验复制，前端不传入。</p>
     */
    public record CopyLabRequest(
            @NotNull(message = "请选择目标班级") Long classId,
            @Size(max = 30, message = "实验名称不能超过 30 个字符") String title,
            @Pattern(regexp = "DRAFT|PUBLISHED|CLOSED", message = "实验状态非法") String status
    ) {
    }

    public record UpdateLabRequest(
            @NotBlank String title,
            String description,
            String experimentContent,
            Integer experimentType,
            @NotNull Long classId,
            String status,
            Long materialId,
            ScoreVisibilityMode scoreVisibilityMode,
            Boolean summaryRequired
    ) {
    }

    public record ChangeLabStatusRequest(
            @NotBlank String status
    ) {
    }

    /**
     * 教师端实验报告队列的查询条件。
     *
     * <p>除筛选条件外还承载分页参数：{@code page} 为空时按 1 处理，{@code pageSize} 为空时按 10 处理，
     * {@code pageSize} 上限 100。{@code labId} 用于把原先前端内存里的单实验筛选下沉到数据库。</p>
     */
    public record TeacherLabReportQuery(
            String keyword,
            String status,
            Long labId,
            Integer page,
            Integer pageSize
    ) {
    }

    public record CreateStepRequest(
            @NotNull Integer stepNo,
            @NotBlank String title,
            @NotBlank String questionType,
            @NotBlank String content,
            String answerConfigJson,
            @NotNull @Min(0) @Max(100) Integer stepScore,
            boolean allowPaste
    ) {
    }

    public record UpdateStepRequest(
            @NotNull Integer stepNo,
            @NotBlank String title,
            @NotBlank String questionType,
            @NotBlank String content,
            String answerConfigJson,
            @NotNull @Min(0) @Max(100) Integer stepScore,
            boolean allowPaste
    ) {
    }

    public record SaveStepAnswerRequest(
            @NotBlank String answerText,
            String answerPayloadJson
    ) {
    }

    public record SubmitLabRequest(
            String summaryText
    ) {
    }

    public record GradeLabReportRequest(
            String teacherComment,
            @NotEmpty List<@Valid GradeItem> items
    ) {
    }

    public record GradeItem(
            @NotNull Long answerId,
            @NotNull Double score,
            String teacherComment
    ) {
    }

    public record ConfirmStepScoreRequest(
            @NotNull Long answerId,
            Double score,
            String teacherComment,
            boolean acceptSuggested
    ) {
    }

    public record SaveBlankAcceptedAnswersRequest(
            @NotNull Long experimentItemId,
            @NotNull List<String> acceptedAnswers
    ) {
    }
}
