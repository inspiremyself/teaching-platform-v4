package com.opencode.teachingplatform.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.service.QuestionContentValidator;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 题目内容契约校验器用例，覆盖五类题型的合法与非法输入。
 *
 * <p>题库不保存分值，校验器不再接收分值参数，因此用例只覆盖题型、题干、选项、答案与评分参数。</p>
 */
class QuestionContentValidatorTests {

    private final QuestionContentValidator validator = new QuestionContentValidator(new ObjectMapper());

    private static final String TWO_OPTIONS = "[{\"key\":\"A\",\"content\":\"选项A\"},{\"key\":\"B\",\"content\":\"选项B\"}]";

    @Test
    void singleChoiceAcceptsAnswerInsideOptions() {
        assertDoesNotThrow(() -> validator.validate("SINGLE", "题干", TWO_OPTIONS, "[\"A\"]", null));
    }

    @Test
    void singleChoiceRejectsAnswerOutsideOptions() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("SINGLE", "题干", TWO_OPTIONS, "[\"C\"]", null));
        assertEquals(40000, ex.getCode());
        assertEquals("单选答案必须是选项之一", ex.getMessage());
    }

    @Test
    void singleChoiceRejectsMultipleAnswers() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("SINGLE", "题干", TWO_OPTIONS, "[\"A\",\"B\"]", null));
        assertEquals(40000, ex.getCode());
        assertEquals("请设置单选题正确答案", ex.getMessage());
    }

    @Test
    void singleChoiceRejectsLessThanTwoOptions() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("SINGLE", "题干", "[{\"key\":\"A\",\"content\":\"选项A\"}]", "[\"A\"]", null));
        assertEquals(40000, ex.getCode());
    }

    @Test
    void multipleChoiceRejectsPartialOutOfRangeAnswer() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("MULTI", "题干", TWO_OPTIONS, "[\"A\",\"C\"]", null));
        assertEquals("多选答案必须全部来自选项", ex.getMessage());
        assertDoesNotThrow(() -> validator.validate("MULTI", "题干", TWO_OPTIONS, "[\"A\",\"B\"]", null));
    }

    @Test
    void judgeNormalizesBooleanAliases() {
        assertDoesNotThrow(() -> validator.validate("JUDGE", "题干", null, "[\"true\"]", null));
        assertDoesNotThrow(() -> validator.validate("JUDGE", "题干", null, "[\"T\"]", null));
        assertDoesNotThrow(() -> validator.validate("JUDGE", "题干", null, "[\"错误\"]", null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("JUDGE", "题干", null, "[\"未知\"]", null));
        assertEquals(40000, ex.getCode());
    }

    @Test
    void fillBlankRejectsBlankAnswerList() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("FILL", "题干", null, "[{\"index\":1,\"answers\":[]}]", null));
        assertEquals("第 1 个空至少需要 1 个答案", ex.getMessage());
        assertDoesNotThrow(() -> validator.validate(
                "FILL", "题干", null, "[{\"index\":1,\"answers\":[\"答案\"]}]", null));
    }

    @Test
    void shortAnswerRequiresReferenceAnswerOrKeywords() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("SHORT", "题干", null, "[]", null));
        assertEquals(40000, ex.getCode());
        assertDoesNotThrow(() -> validator.validate("SHORT", "题干", null, "[\"参考\"]", null));
        assertDoesNotThrow(() -> validator.validate("SHORT", "题干", null, "[]",
                "{\"keywords\":[{\"term\":\"策略\",\"weight\":5}]}"));
    }

    @Test
    void commonFieldsAndJsonSyntaxAreValidated() {
        assertEquals("题干不能为空", assertThrows(BusinessException.class,
                () -> validator.validate("SINGLE", " ", TWO_OPTIONS, "[\"A\"]", null)).getMessage());
        assertEquals("不支持的题型：UNKNOWN", assertThrows(BusinessException.class,
                () -> validator.validate("UNKNOWN", "题干", TWO_OPTIONS, "[\"A\"]", null)).getMessage());
        assertEquals("题目选项 JSON 格式不正确", assertThrows(BusinessException.class,
                () -> validator.validate("SINGLE", "题干", "[\"A\",]", "[\"A\"]", null)).getMessage());
        assertEquals("题目答案 JSON 格式不正确", assertThrows(BusinessException.class,
                () -> validator.validate("SINGLE", "题干", TWO_OPTIONS, "[1,]", null)).getMessage());
    }
}
