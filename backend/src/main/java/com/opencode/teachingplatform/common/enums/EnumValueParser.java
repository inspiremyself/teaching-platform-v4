package com.opencode.teachingplatform.common.enums;

import com.opencode.teachingplatform.common.exception.BusinessException;

import java.util.Locale;

/**
 * 枚举值解析工具：将外部传入的状态字符串统一解析为枚举。
 *
 * <p>非法值统一抛出 {@code 40000} 业务异常，文案包含字段标签与原始值，
 * 避免各 Service 各自 {@code valueOf} 导致 {@code IllegalArgumentException} 变成 500。</p>
 */
public final class EnumValueParser {

    private EnumValueParser() {
    }

    /**
     * 解析枚举值；入参先 trim 再转大写，兼容前端传入的大小写混写。
     *
     * @param type       目标枚举类型
     * @param raw        原始字符串
     * @param fieldLabel 字段中文标签，用于错误文案
     * @return 解析后的枚举值
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String raw, String fieldLabel) {
        if (raw == null) {
            throw new BusinessException(40000, fieldLabel + "不合法: null");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40000, fieldLabel + "不合法: " + raw);
        }
    }
}
