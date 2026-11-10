package com.opencode.teachingplatform.homework.dto;

import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.List;

public final class HomeworkRequests {

    private HomeworkRequests() {
    }

    public record CreateHomeworkRequest(
            @NotBlank String title,
            String description,
            @NotNull Long classId,
            String status,
            ScoreVisibilityMode scoreVisibilityMode,
            String attachmentPath,
            OffsetDateTime startAt,
            OffsetDateTime dueAt
    ) {
    }

    public record UpdateHomeworkRequest(
            @NotBlank String title,
            String description,
            @NotNull Long classId,
            String status,
            ScoreVisibilityMode scoreVisibilityMode,
            String attachmentPath,
            OffsetDateTime startAt,
            OffsetDateTime dueAt
    ) {
    }

    public record ChangeHomeworkStatusRequest(
            @NotBlank String status
    ) {
    }

    public record SubmitHomeworkRequest(
            String answerText,
            String attachment,
            String attachmentPath,
            @Valid List<SubmitHomeworkAnswerItem> answers
    ) {
        public SubmitHomeworkRequest(List<SubmitHomeworkAnswerItem> answers) {
            this(null, null, null, answers);
        }

        public SubmitHomeworkRequest(String answerText, String attachment, String attachmentPath) {
            this(answerText, attachment, attachmentPath, List.of());
        }

        public String resolvedAttachmentPath() {
            if (attachmentPath != null && !attachmentPath.isBlank()) {
                return attachmentPath;
            }
            return attachment;
        }

        public boolean questionBased() {
            return answers != null && !answers.isEmpty();
        }
    }

    public static final class AddHomeworkQuestionFromBankRequest {
        @NotNull
        private Long questionId;

        @NotNull
        private Integer sortOrder;

        @NotNull
        @Min(value = 1, message = "分值必须为 1–100 的整数")
        @Max(value = 100, message = "分值必须为 1–100 的整数")
        @Digits(integer = 3, fraction = 0, message = "分值必须为 1–100 的整数")
        private Double questionScore;

        public AddHomeworkQuestionFromBankRequest() {
        }

        public AddHomeworkQuestionFromBankRequest(Long questionId, Integer sortOrder, Double questionScore) {
            this.questionId = questionId;
            this.sortOrder = sortOrder;
            this.questionScore = questionScore;
        }

        public Long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(Long questionId) {
            this.questionId = questionId;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        public Double getQuestionScore() {
            return questionScore;
        }

        public void setQuestionScore(Double questionScore) {
            this.questionScore = questionScore;
        }
    }

    /**
     * 作业题目新增/更新共用的请求体。
     *
     * <p>新增与更新字段完全一致，因此合并为一个 DTO，避免两套结构漂移。
     * 选项与答案独立成字段，评分参数只承载 mode / keywords / blanks 等评分键。</p>
     */
    public record SaveHomeworkQuestionRequest(
            @NotBlank String type,
            @NotBlank String stem,
            @NotNull
            @Min(value = 1, message = "分值必须为 1–100 的整数")
            @Max(value = 100, message = "分值必须为 1–100 的整数")
            @Digits(integer = 3, fraction = 0, message = "分值必须为 1–100 的整数")
            Double questionScore,
            String optionsJson,
            @NotBlank String answerJson,
            String analysisText,
            String scoringConfigJson,
            @NotNull @Positive Integer sortOrder
    ) {
    }

    public record SubmitHomeworkAnswerItem(
            @NotNull Long homeworkQuestionId,
            List<String> selectedOptions,
            String answerText,
            String answerJson,
            String attachmentPath
    ) {
    }

    public record GradeHomeworkRequest(
            @NotNull Double totalScore,
            String teacherComment
    ) {
    }

    public record GradeHomeworkAnswerRequest(
            Double score,
            String teacherComment,
            boolean acceptSuggested
    ) {
    }
}
