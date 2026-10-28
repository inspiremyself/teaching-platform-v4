package com.opencode.teachingplatform.lab.behavior.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.lab.behavior.BehaviorProperties;
import com.opencode.teachingplatform.lab.behavior.entity.BehaviorEvent;
import com.opencode.teachingplatform.lab.behavior.entity.LabSubmissionMetrics;
import com.opencode.teachingplatform.lab.behavior.enums.BehaviorEventType;
import com.opencode.teachingplatform.lab.behavior.repository.BehaviorEventRepository;
import com.opencode.teachingplatform.lab.behavior.repository.LabSubmissionMetricsRepository;
import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import com.opencode.teachingplatform.lab.entity.LabStepAnswerLog;
import com.opencode.teachingplatform.lab.entity.LabSubmission;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerLogRepository;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerRepository;
import com.opencode.teachingplatform.lab.repository.LabStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实验作答行为指标聚合与风险标记服务（技术方案 §6.1 LabBehaviorMetricsService）。
 *
 * <p>两个触发时机写死（D8）：</p>
 * <ul>
 *   <li>① {@code submitLab} 成功后在外层写事务内调用 {@link #computeForSubmission}（REQUIRED）；</li>
 *   <li>② 教师列表/详情读取时的懒算：必须经 {@link #refreshStaleMetrics}（REQUIRES_NEW）——
 *   调用方均为 {@code @Transactional(readOnly = true)}，在其中直接写库会静默失败；
 *   且重算结果以内存对象直接返回装配，不在外层事务里重查（MySQL RR 快照读不到内层新提交）。</li>
 * </ul>
 *
 * <p>指标口径唯一事实源见方案 §7 矩阵：保存次数只来自 {@code lab_step_answer_log}（D2），
 * 活跃时长只来自 HEARTBEAT.activeMs 求和，时长负值一律截断为 0。</p>
 */
@Service
public class LabBehaviorMetricsService {

    /** 风险权重（D9）：时长过短 40 / 大量粘贴 30 / 频繁离页 20。 */
    private static final int SCORE_DURATION_TOO_SHORT = 40;
    private static final int SCORE_PASTE_HEAVY = 30;
    private static final int SCORE_FOCUS_LOSS = 20;

    /** 等级分档：LOW < 30 / MEDIUM 30-59 / HIGH >= 60（D9）。 */
    private static final int MEDIUM_THRESHOLD = 30;
    private static final int HIGH_THRESHOLD = 60;

    private final BehaviorEventRepository behaviorEventRepository;
    private final LabSubmissionMetricsRepository metricsRepository;
    private final LabStepAnswerRepository labStepAnswerRepository;
    private final LabStepAnswerLogRepository labStepAnswerLogRepository;
    private final LabStepRepository labStepRepository;
    private final BehaviorProperties behaviorProperties;
    private final ObjectMapper objectMapper;

    public LabBehaviorMetricsService(BehaviorEventRepository behaviorEventRepository,
                                     LabSubmissionMetricsRepository metricsRepository,
                                     LabStepAnswerRepository labStepAnswerRepository,
                                     LabStepAnswerLogRepository labStepAnswerLogRepository,
                                     LabStepRepository labStepRepository,
                                     BehaviorProperties behaviorProperties,
                                     ObjectMapper objectMapper) {
        this.behaviorEventRepository = behaviorEventRepository;
        this.metricsRepository = metricsRepository;
        this.labStepAnswerRepository = labStepAnswerRepository;
        this.labStepAnswerLogRepository = labStepAnswerLogRepository;
        this.labStepRepository = labStepRepository;
        this.behaviorProperties = behaviorProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 懒算写路径（D8 时机②的唯一入口）。
     *
     * <p>REQUIRES_NEW：从 readOnly 的外层查询事务中挂起并新开读写事务，
     * 逐份提交重算并落库，返回内存对象供外层直接装配（不重查库）。</p>
     *
     * @param staleRows 判定为陈旧的提交行（metrics 缺失或 watermark 落后）
     * @return submissionId → 最新 metrics（内存对象，已落库）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<Long, LabSubmissionMetrics> refreshStaleMetrics(List<LabSubmission> staleRows) {
        Map<Long, LabSubmissionMetrics> fresh = new HashMap<>();
        for (LabSubmission submission : staleRows) {
            fresh.put(submission.getId(), computeForSubmission(submission));
        }
        return fresh;
    }

    /**
     * 全量计算一份提交的行为指标并落库（submission_id 主键 saveOrUpdate）。
     *
     * <p>REQUIRED：在 {@code submitLab} 外层写事务内被联动调用（D8 时机①）；
     * 也被 {@link #refreshStaleMetrics} 复用。提交瞬间前端缓冲中的尾部事件可能尚未到达，
     * 本次以已到事件为准，迟到事件由教师首读的懒算兜底重算。</p>
     */
    @Transactional
    public LabSubmissionMetrics computeForSubmission(LabSubmission submission) {
        Long labId = submission.getLabId();
        Long studentId = submission.getStudentId();

        // 1. 两源数据：行为事件（归属键顺序扫描）+ 答案保存快照（D2 单一事实源）
        List<BehaviorEvent> events = behaviorEventRepository
                .findByBizTypeAndLabIdAndStudentIdOrderByIdAsc(BehaviorEvent.BIZ_TYPE_LAB, labId, studentId);
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        List<Long> answerIds = answers.stream()
                .map(LabStepAnswer::getId)
                .filter(Objects::nonNull)
                .toList();
        List<LabStepAnswerLog> answerLogs = answerIds.isEmpty()
                ? List.of()
                : labStepAnswerLogRepository.findByLabStepAnswerIdIn(answerIds);

        // 2. 时间口径（§7 矩阵）：开始作答 = 最早 LAB_ENTER 与最早快照取 min；最近行为 = 两源取 max
        OffsetDateTime firstEnterAt = null;
        OffsetDateTime lastEventAt = null;
        long maxEventId = 0L;
        // 3. 事件流聚合：单次遍历完成全部计数
        long activeMs = 0L;
        boolean hasHeartbeat = false;
        int pasteCount = 0;
        int pastedChars = 0;
        int pasteBlockedCount = 0;
        int hiddenCount = 0;
        long hiddenMs = 0L;
        int blurCount = 0;
        int sessionCount = 0;
        Map<Long, Long> stepDwellMs = new LinkedHashMap<>();
        for (BehaviorEvent event : events) {
            if (event.getId() != null && event.getId() > maxEventId) {
                maxEventId = event.getId();
            }
            OffsetDateTime occurredAt = event.getOccurredAt();
            if (occurredAt != null && (lastEventAt == null || occurredAt.isAfter(lastEventAt))) {
                lastEventAt = occurredAt;
            }
            BehaviorEventType type = BehaviorEventType.parseOrNull(event.getEventType());
            if (type == null) {
                continue;
            }
            JsonNode payload = parsePayload(event.getPayloadJson());
            switch (type) {
                case LAB_ENTER -> {
                    sessionCount++;
                    if (occurredAt != null && (firstEnterAt == null || occurredAt.isBefore(firstEnterAt))) {
                        firstEnterAt = occurredAt;
                    }
                }
                case HEARTBEAT -> {
                    long delta = payload == null ? 0L : payload.path("activeMs").asLong(0L);
                    if (delta > 0) {
                        activeMs += delta;
                        hasHeartbeat = true;
                    }
                }
                case PASTE -> {
                    pasteCount++;
                    if (payload != null) {
                        pastedChars += (int) Math.min(Integer.MAX_VALUE, Math.max(0L, payload.path("pastedLength").asLong(0L)));
                        if (payload.path("blocked").asBoolean(false)) {
                            pasteBlockedCount++;
                        }
                    }
                }
                case VISIBILITY -> {
                    if (payload != null && "hidden".equals(payload.path("state").asText())) {
                        hiddenCount++;
                    }
                    if (payload != null && "visible".equals(payload.path("state").asText())) {
                        hiddenMs += Math.max(0L, payload.path("hiddenMs").asLong(0L));
                    }
                }
                case WINDOW_BLUR -> blurCount++;
                case STEP_VIEW -> {
                    // dwellMs 归属上一题（fromStepId 在 payload，stepId 字段是当前题）
                    Long fromStepId = payload != null && payload.hasNonNull("fromStepId")
                            ? payload.path("fromStepId").asLong()
                            : null;
                    accumulateDwell(stepDwellMs, fromStepId, payload == null ? 0L : payload.path("dwellMs").asLong(0L));
                }
                case LAB_LEAVE -> {
                    // 离开前的末段停留归属 lastStepId（payload）；事件 stepId 字段不带
                    Long lastStepId = payload != null && payload.hasNonNull("lastStepId")
                            ? payload.path("lastStepId").asLong()
                            : null;
                    accumulateDwell(stepDwellMs, lastStepId, payload == null ? 0L : payload.path("dwellMs").asLong(0L));
                }
                default -> {
                    // SUBMIT_CLICK / SUBMIT_BLOCKED / SAVE_FAILED / WINDOW_FOCUS：本期不进指标
                }
            }
        }

        // 4. 快照时间线合并：保存时间兜底学生机时钟异常（D6 三层防护之一）
        OffsetDateTime firstFillAt = null;
        OffsetDateTime lastFillAt = null;
        for (LabStepAnswerLog answerLog : answerLogs) {
            OffsetDateTime fillTime = answerLog.getFillTime();
            if (fillTime == null) {
                continue;
            }
            if (firstFillAt == null || fillTime.isBefore(firstFillAt)) {
                firstFillAt = fillTime;
            }
            if (lastFillAt == null || fillTime.isAfter(lastFillAt)) {
                lastFillAt = fillTime;
            }
        }
        OffsetDateTime firstStartedAt = minNonNull(firstEnterAt, firstFillAt);
        if (lastFillAt != null && (lastEventAt == null || lastFillAt.isAfter(lastEventAt))) {
            lastEventAt = lastFillAt;
        }

        // 5. 总时长：已提交以 submittedAt 为止；SAVED 行以最近事件为止（durationOpen 口径由装配层推导）
        OffsetDateTime durationEnd = submission.getSubmittedAt() != null
                ? submission.getSubmittedAt()
                : lastEventAt;
        Long totalDurationMs = firstStartedAt == null || durationEnd == null
                ? null
                : Math.max(0L, Duration.between(firstStartedAt, durationEnd).toMillis());

        // 6. 风险规则（D9）：阈值全部来自 lab.behavior.risk.* 配置
        BehaviorProperties.Risk risk = behaviorProperties.getRisk();
        List<String> flags = new ArrayList<>();
        int riskScore = 0;
        if (events.isEmpty()) {
            // 无事件行：仅标记说明，不提升等级
            flags.add(LabSubmissionMetrics.FLAG_NO_BEHAVIOR_DATA);
        } else {
            int stepCount = labStepRepository.findByLabIdOrderByStepNoAsc(labId).size();
            if (totalDurationMs != null && totalDurationMs < stepCount * risk.getPerStepMinMs()) {
                flags.add(LabSubmissionMetrics.FLAG_DURATION_TOO_SHORT);
                riskScore += SCORE_DURATION_TOO_SHORT;
            }
            long finalTextLength = answers.stream()
                    .map(LabStepAnswer::getAnswerText)
                    .filter(Objects::nonNull)
                    .mapToLong(String::length)
                    .sum();
            if (pastedChars > risk.getPasteCharsThreshold()
                    && finalTextLength > 0
                    && (double) pastedChars / finalTextLength > risk.getPasteRatioThreshold()) {
                flags.add(LabSubmissionMetrics.FLAG_PASTE_HEAVY);
                riskScore += SCORE_PASTE_HEAVY;
            }
            boolean focusLossByCount = hiddenCount >= risk.getHiddenCountThreshold();
            boolean focusLossByRatio = totalDurationMs != null && totalDurationMs > 0
                    && (double) hiddenMs / totalDurationMs > risk.getHiddenRatioThreshold();
            if (focusLossByCount || focusLossByRatio) {
                flags.add(LabSubmissionMetrics.FLAG_FOCUS_LOSS);
                riskScore += SCORE_FOCUS_LOSS;
            }
        }
        String riskLevel = riskScore >= HIGH_THRESHOLD
                ? LabSubmissionMetrics.RISK_HIGH
                : riskScore >= MEDIUM_THRESHOLD
                        ? LabSubmissionMetrics.RISK_MEDIUM
                        : LabSubmissionMetrics.RISK_LOW;

        // 7. 落库（saveOrUpdate：主键已存在时 JPA 走 merge）
        LabSubmissionMetrics metrics = new LabSubmissionMetrics();
        metrics.setSubmissionId(submission.getId());
        metrics.setLabId(labId);
        metrics.setStudentId(studentId);
        metrics.setFirstStartedAt(firstStartedAt);
        metrics.setLastEventAt(lastEventAt);
        metrics.setTotalDurationMs(totalDurationMs);
        // 无心跳行 → null，前端显示 "-"（§7 口径）
        metrics.setActiveDurationMs(hasHeartbeat ? activeMs : null);
        metrics.setSaveCount(answerLogs.size());
        metrics.setPasteCount(pasteCount);
        metrics.setPastedChars(pastedChars);
        metrics.setPasteBlockedCount(pasteBlockedCount);
        metrics.setVisibilityHiddenCount(hiddenCount);
        metrics.setHiddenMs(hiddenMs);
        metrics.setBlurCount(blurCount);
        metrics.setSessionCount(sessionCount);
        metrics.setStepDwellJson(writeJson(stepDwellMs));
        metrics.setRiskScore(riskScore);
        metrics.setRiskLevel(riskLevel);
        metrics.setRiskFlagsJson(writeJson(flags));
        metrics.setEventWatermark(maxEventId);
        metrics.setComputedAt(OffsetDateTime.now());
        return metricsRepository.save(metrics);
    }

    private void accumulateDwell(Map<Long, Long> stepDwellMs, Long stepId, long dwellMs) {
        if (stepId == null || dwellMs <= 0) {
            return;
        }
        stepDwellMs.merge(stepId, dwellMs, Long::sum);
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private OffsetDateTime minNonNull(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }
}
