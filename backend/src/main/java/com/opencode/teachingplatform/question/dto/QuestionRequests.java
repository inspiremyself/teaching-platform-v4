package com.opencode.teachingplatform.question.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.opencode.teachingplatform.common.exception.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public final class QuestionRequests {

    private QuestionRequests() {
    }

    /** 两个题库写入 DTO 共用：未知字段一律拒绝，文案单一来源。 */
    private static void rejectUnknown(String name) {
        throw new BusinessException(40000, "题库请求不支持字段：" + name);
    }

    /**
     * 复制到题库的请求体：编码可选，不传时由服务端按来源自动生成。
     */
    public record CopyQuestionToBankRequest(
            @Size(max = 64) String code
    ) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            rejectUnknown(name);
        }
    }

    public record SaveQuestionRequest(
            @NotBlank(message = "题目编码不能为空") String code,
            @NotBlank(message = "题目类型不能为空") String type,
            @NotBlank(message = "题干不能为空") String stem,
            @NotBlank(message = "难度不能为空") String difficulty,
            String optionsJson,
            @NotBlank(message = "答案不能为空") String answerJson,
            String analysisText
    ) {

        /**
         * 题库请求只接受 record 声明的字段：任何未知键（含题库已废止的分值字段）直接拒绝，
         * 不再为旧客户端保留静默忽略的兼容路径。
         */
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            rejectUnknown(name);
        }

        @AssertTrue(message = "选择题选项不能为空")
        public boolean isOptionsJsonValid() {
            if (type == null) {
                return true;
            }
            String normalizedType = type.trim();
            if (!"SINGLE".equals(normalizedType) && !"MULTI".equals(normalizedType) && !"JUDGE".equals(normalizedType)) {
                return true;
            }
            return optionsJson != null && !optionsJson.isBlank();
        }
    }
}
