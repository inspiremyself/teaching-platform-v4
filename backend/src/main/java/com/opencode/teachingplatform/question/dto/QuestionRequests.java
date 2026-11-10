package com.opencode.teachingplatform.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public final class QuestionRequests {

    private QuestionRequests() {
    }

    /**
     * 复制到题库的请求体：编码可选，不传时由服务端按来源自动生成。
     */
    public record CopyQuestionToBankRequest(
            @Size(max = 64) String code
    ) {
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
