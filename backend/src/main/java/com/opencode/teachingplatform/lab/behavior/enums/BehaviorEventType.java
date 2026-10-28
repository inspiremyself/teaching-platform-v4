package com.opencode.teachingplatform.lab.behavior.enums;

/**
 * 学生作答行为事件类型（技术方案 D3，P0 写死 11 类）。
 *
 * <p>以 String 形式落库（{@code behavior_event.event_type}）。非法值在 ingest 时
 * 单条丢弃并计入 dropped，不导致整批失败（方案 §5.1 校验表）。</p>
 */
public enum BehaviorEventType {

    /** 作答页 mounted，进入作答会话。 */
    LAB_ENTER,
    /** 离开作答：reason = route（路由离开）/ unload（直接关浏览器）/ submitted（提交成功）。 */
    LAB_LEAVE,
    /** 当前题项切换，payload 带 fromStepId 与上一题停留 dwellMs。 */
    STEP_VIEW,
    /** 编辑器容器粘贴；blocked 由服务端按 step.allowPaste 回填，客户端不可信（D13）。 */
    PASTE,
    /** 页面可见性切换；visible 时带回本次隐藏时长 hiddenMs。 */
    VISIBILITY,
    /** 窗口失焦（单独计数，不并入切屏口径，见指标矩阵）。 */
    WINDOW_BLUR,
    /** 窗口聚焦，payload 带 blurMs。 */
    WINDOW_FOCUS,
    /** 每 60s 活跃心跳（仅页面可见时累计），活跃时长的主数据源。 */
    HEARTBEAT,
    /** 点击提交按钮。 */
    SUBMIT_CLICK,
    /** 前端校验拦截提交，payload 带 reason。 */
    SUBMIT_BLOCKED,
    /** 保存接口失败，payload 带 stepId 与是否静默保存。 */
    SAVE_FAILED;

    /**
     * 容错解析：非法值返回 null 而不是抛异常，配合 ingest 的单条丢弃语义。
     *
     * @param raw 前端上报的原始事件类型字符串
     * @return 合法枚举值，非法时为 null
     */
    public static BehaviorEventType parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return BehaviorEventType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
