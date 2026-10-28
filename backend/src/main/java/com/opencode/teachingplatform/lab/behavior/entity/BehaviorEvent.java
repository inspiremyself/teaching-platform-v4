package com.opencode.teachingplatform.lab.behavior.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 学生作答行为事件（{@code behavior_event}，V19）。
 *
 * <p>高频批量写入的独立采集表（技术方案 D1）：与面向管理操作的 audit_log 分离，
 * 可扩展字段一律进 {@link #payloadJson}。无外键约束，允许事件在 submission
 * 创建前到达，归属键为 {@code (bizType, labId, studentId)}（D5）。</p>
 *
 * <p>不继承 BaseEntity：本表无 created_at/updated_at 列，发生时间与接收时间
 * 分别由 {@link #occurredAt}（客户端时钟，容差修正）与 {@link #receivedAt}
 * （服务端时钟）表达。</p>
 */
@Entity
@Table(name = "behavior_event")
public class BehaviorEvent {

    /** 行为采集当前唯一业务类型：实验作答。HOMEWORK/EXAM 预留，本期不埋点。 */
    public static final String BIZ_TYPE_LAB = "LAB";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户端生成的幂等键（crypto.randomUUID），全表唯一，重试复用（D4）。 */
    @Column(name = "event_uuid", nullable = false, length = 36)
    private String eventUuid;

    @Column(name = "biz_type", nullable = false, length = 16)
    private String bizType = BIZ_TYPE_LAB;

    /** 事件类型字符串（BehaviorEventType.name()），String 存储便于扩展。 */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** 客户端发生时间；与服务端偏差超容差时以 receivedAt 替代（D6）。 */
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    /** 服务端接收时间，代码显式赋值（DB DEFAULT 仅为兜底）。 */
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "lab_id", nullable = false)
    private Long labId;

    /** 冗余回填：该生该实验已存在 submission 时写入，便于按提交维度检索。 */
    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "step_id")
    private Long stepId;

    /** 事件扩展字段（JSON 文本）：粘贴长度、停留毫秒、时钟修正标记等。 */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "trace_id", length = 36)
    private String traceId;

    public Long getId() {
        return id;
    }

    public String getEventUuid() {
        return eventUuid;
    }

    public void setEventUuid(String eventUuid) {
        this.eventUuid = eventUuid;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
