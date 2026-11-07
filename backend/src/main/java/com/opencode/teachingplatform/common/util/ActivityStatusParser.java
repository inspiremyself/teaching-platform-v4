package com.opencode.teachingplatform.common.util;

import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.EnumValueParser;

/**
 * 三闭环共用的活动状态解析入口。
 *
 * <p>空值一律回落到 DRAFT，非空值交给 {@link EnumValueParser} 解析；
 * label 由调用方按业务域给出，保证错误文案与整改前一致。</p>
 */
public final class ActivityStatusParser {

    private ActivityStatusParser() {
    }

    public static ActivityStatus parse(String value, String label) {
        return value == null || value.isBlank()
                ? ActivityStatus.DRAFT
                : EnumValueParser.parse(ActivityStatus.class, value, label);
    }
}
