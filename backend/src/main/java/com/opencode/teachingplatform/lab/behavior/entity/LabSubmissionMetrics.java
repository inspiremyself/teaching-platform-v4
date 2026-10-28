package com.opencode.teachingplatform.lab.behavior.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 实验提交行为指标预聚合（{@code lab_submission_metrics}，V20，技术方案 D7）。
 *
 * <p>指标与风险标记随提交记录一对一落库：列表页批量读本表而不是现算事件表，
 * 避免"每行一次事件聚合"的新型 N+1。{@link #eventWatermark} 记录已计入的最大
 * 事件 id，懒算据此判定是否有迟到事件需要重算（D8）。</p>
 *
 * <p>时长类字段为 null 表示"无数据源"（如未收到任何事件与保存日志），
 * 前端统一显示 {@code -}。</p>
 */
@Entity
@Table(name = "lab_submission_metrics")
public class LabSubmissionMetrics {

    /** 风险等级：LOW < 30 / MEDIUM 30-59 / HIGH >= 60（D9）。 */
    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    /** 无事件行的标记：不提升等级，仅向教师说明"历史提交无行为数据"（D9）。 */
    public static final String FLAG_NO_BEHAVIOR_DATA = "NO_BEHAVIOR_DATA";
    public static final String FLAG_DURATION_TOO_SHORT = "DURATION_TOO_SHORT";
    public static final String FLAG_PASTE_HEAVY = "PASTE_HEAVY";
    public static final String FLAG_FOCUS_LOSS = "FOCUS_LOSS";

    @Id
    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "lab_id", nullable = false)
    private Long labId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 开始作答时间：LAB_ENTER 最早 occurredAt 与最早答案快照 fillTime 取 min。 */
    @Column(name = "first_started_at")
    private OffsetDateTime firstStartedAt;

    /** 最近行为时间：全部事件 occurredAt 与最后快照 fillTime 取 max。 */
    @Column(name = "last_event_at")
    private OffsetDateTime lastEventAt;

    /** 总作答时长（墙钟口径）：submittedAt - firstStartedAt；SAVED 行以 lastEventAt 为止。 */
    @Column(name = "total_duration_ms")
    private Long totalDurationMs;

    /** 活跃时长：HEARTBEAT.activeMs 求和；无心跳行为 null（显示 "-"）。 */
    @Column(name = "active_duration_ms")
    private Long activeDurationMs;

    /** 保存次数：lab_step_answer_log 行数（含图片上传快照，D17③）。 */
    @Column(name = "save_count", nullable = false)
    private int saveCount;

    @Column(name = "paste_count", nullable = false)
    private int pasteCount;

    @Column(name = "pasted_chars", nullable = false)
    private int pastedChars;

    /** 粘贴被禁题上的粘贴尝试次数（blocked=true 的 PASTE 事件计数）。 */
    @Column(name = "paste_blocked_count", nullable = false)
    private int pasteBlockedCount;

    @Column(name = "visibility_hidden_count", nullable = false)
    private int visibilityHiddenCount;

    /** 页面隐藏总时长：VISIBILITY visible 事件带回的 hiddenMs 求和。 */
    @Column(name = "hidden_ms", nullable = false)
    private long hiddenMs;

    /** 窗口失焦次数（单独计数，不并入切屏口径）。 */
    @Column(name = "blur_count", nullable = false)
    private int blurCount;

    /** 作答会话数：LAB_ENTER 计数，识别断续作答（D17①）。 */
    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    /** 逐题停留毫秒 Map（stepId → dwellMs 累计）的 JSON 文本。 */
    @Column(name = "step_dwell_json", columnDefinition = "TEXT")
    private String stepDwellJson;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "risk_level", nullable = false, length = 8)
    private String riskLevel = RISK_LOW;

    /** 风险标记列表（FLAG_* 常量）的 JSON 文本。 */
    @Column(name = "risk_flags_json", columnDefinition = "TEXT")
    private String riskFlagsJson;

    /** 已计入的最大事件 id：迟到事件使最新 id 超过它时触发懒算重算（D8）。 */
    @Column(name = "event_watermark", nullable = false)
    private long eventWatermark;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public OffsetDateTime getFirstStartedAt() {
        return firstStartedAt;
    }

    public void setFirstStartedAt(OffsetDateTime firstStartedAt) {
        this.firstStartedAt = firstStartedAt;
    }

    public OffsetDateTime getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(OffsetDateTime lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Long getActiveDurationMs() {
        return activeDurationMs;
    }

    public void setActiveDurationMs(Long activeDurationMs) {
        this.activeDurationMs = activeDurationMs;
    }

    public int getSaveCount() {
        return saveCount;
    }

    public void setSaveCount(int saveCount) {
        this.saveCount = saveCount;
    }

    public int getPasteCount() {
        return pasteCount;
    }

    public void setPasteCount(int pasteCount) {
        this.pasteCount = pasteCount;
    }

    public int getPastedChars() {
        return pastedChars;
    }

    public void setPastedChars(int pastedChars) {
        this.pastedChars = pastedChars;
    }

    public int getPasteBlockedCount() {
        return pasteBlockedCount;
    }

    public void setPasteBlockedCount(int pasteBlockedCount) {
        this.pasteBlockedCount = pasteBlockedCount;
    }

    public int getVisibilityHiddenCount() {
        return visibilityHiddenCount;
    }

    public void setVisibilityHiddenCount(int visibilityHiddenCount) {
        this.visibilityHiddenCount = visibilityHiddenCount;
    }

    public long getHiddenMs() {
        return hiddenMs;
    }

    public void setHiddenMs(long hiddenMs) {
        this.hiddenMs = hiddenMs;
    }

    public int getBlurCount() {
        return blurCount;
    }

    public void setBlurCount(int blurCount) {
        this.blurCount = blurCount;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public String getStepDwellJson() {
        return stepDwellJson;
    }

    public void setStepDwellJson(String stepDwellJson) {
        this.stepDwellJson = stepDwellJson;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRiskFlagsJson() {
        return riskFlagsJson;
    }

    public void setRiskFlagsJson(String riskFlagsJson) {
        this.riskFlagsJson = riskFlagsJson;
    }

    public long getEventWatermark() {
        return eventWatermark;
    }

    public void setEventWatermark(long eventWatermark) {
        this.eventWatermark = eventWatermark;
    }

    public OffsetDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(OffsetDateTime computedAt) {
        this.computedAt = computedAt;
    }
}
