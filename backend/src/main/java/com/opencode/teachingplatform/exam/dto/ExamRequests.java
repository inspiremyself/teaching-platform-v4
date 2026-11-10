package com.opencode.teachingplatform.exam.dto;

import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public final class ExamRequests {

    private ExamRequests() {}

    /**
     * 组卷题目入参：考试仅接受题库题目，内联题已下线。
     * sourceType 为兼容旧客户端保留，缺省仍解析为 BANK。
     */
    public record ExamQuestionItem(
            Long questionId,
            String sourceType,
            @NotNull Integer sortOrder,
            @NotNull
            @Min(value = 1, message = "分值必须为 1–100 的整数")
            @Max(value = 100, message = "分值必须为 1–100 的整数")
            @Digits(integer = 3, fraction = 0, message = "分值必须为 1–100 的整数")
            Double questionScore
    ) {
        public ExamQuestionItem(Long questionId, Integer sortOrder, Double questionScore) {
            this(questionId, "BANK", sortOrder, questionScore);
        }

        public String resolvedSourceType() {
            return sourceType == null || sourceType.isBlank() ? "BANK" : sourceType;
        }

        @AssertTrue(message = "考试题目必须来自题库")
        public boolean isValidConfig() {
            return questionId != null && "BANK".equalsIgnoreCase(resolvedSourceType());
        }
    }

    public record CreateExamRequest(
            @NotBlank String title,
            String description,
            @NotNull Long classId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            @NotNull
            @Min(value = 1, message = "考试时长必须大于 0 分钟")
            @Max(value = 600, message = "考试时长不能超过 600 分钟")
            Integer durationMinutes,
            @NotBlank String status,
            ScoreVisibilityMode scoreVisibilityMode,
            @Valid List<ExamQuestionItem> questions,
            Boolean shuffleQuestions
    ) {}

    public record UpdateExamRequest(
            @NotBlank String title,
            String description,
            @NotNull Long classId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            @NotNull
            @Min(value = 1, message = "考试时长必须大于 0 分钟")
            @Max(value = 600, message = "考试时长不能超过 600 分钟")
            Integer durationMinutes,
            ScoreVisibilityMode scoreVisibilityMode,
            @Valid List<ExamQuestionItem> questions,
            Boolean shuffleQuestions
    ) {}

    public record ChangeExamStatusRequest(
            @NotBlank String status
    ) {}

    public record SubmitAnswerItem(
            @NotNull Long questionId,
            List<String> selectedOptions,
            String answerText,
            String answerJson,
            String attachmentPath
    ) {}

    public record SubmitExamRequest(
            List<SubmitAnswerItem> answers
    ) {}

    public record GradeExamAnswerItem(
            @NotNull Long questionId,
            @NotNull Double score,
            String teacherComment
    ) {}

    /**
     * 批量人工评分入参。
     * answers 允许为空数组或缺省：纯客观题答卷由交卷链路自动结案，
     * 历史卡单可由教师触发一次空列表“保存评分”以重新计算并结案。
     */
    public record GradeExamSubmissionRequest(
            List<GradeExamAnswerItem> answers
    ) {}

    public record ConfirmExamAnswerRequest(
            @NotNull Long questionId,
            String teacherComment,
            Double score,
            boolean acceptSuggested
    ) {}
}
