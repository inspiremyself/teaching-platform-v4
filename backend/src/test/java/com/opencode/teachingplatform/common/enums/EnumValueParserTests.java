package com.opencode.teachingplatform.common.enums;

import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnumValueParser} 统一枚举解析单元测试。
 */
class EnumValueParserTests {

    @Test
    void parsesTrimmedUpperCaseValue() {
        assertEquals(ActivityStatus.PUBLISHED,
                EnumValueParser.parse(ActivityStatus.class, " published ", "实验状态"));
    }

    @Test
    void mixedCaseValueIsAccepted() {
        assertEquals(ActivityStatus.CLOSED,
                EnumValueParser.parse(ActivityStatus.class, "Closed", "实验状态"));
    }

    @Test
    void illegalValueThrowsBadRequestWithLabelAndRawValue() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> EnumValueParser.parse(ActivityStatus.class, "GRADEDX", "报告状态"));

        assertEquals(40000, exception.getCode());
        assertTrue(exception.getMessage().contains("报告状态"));
        assertTrue(exception.getMessage().contains("GRADEDX"));
    }

    @Test
    void nullValueThrowsBadRequest() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> EnumValueParser.parse(ActivityStatus.class, null, "考试状态"));

        assertEquals(40000, exception.getCode());
        assertTrue(exception.getMessage().contains("考试状态"));
    }
}
