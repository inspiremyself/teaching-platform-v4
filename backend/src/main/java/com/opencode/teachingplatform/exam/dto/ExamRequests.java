package com.opencode.teachingplatform.exam.dto;

import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
            @NotNull Double questionScore
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
            @NotNull Integer durationMinutes,
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
            @NotNull Integer durationMinutes,
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

    public record GradeExamSubmissionRequest(
            @NotEmpty List<GradeExamAnswerItem> answers
    ) {}

    public record ConfirmExamAnswerRequest(
            @NotNull Long questionId,
            String teacherComment,
            Double score,
            boolean acceptSuggested
    ) {}
}
