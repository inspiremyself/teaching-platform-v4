package com.opencode.teachingplatform.lab.behavior.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.trace.RequestTraceContext;
import com.opencode.teachingplatform.lab.behavior.BehaviorProperties;
import com.opencode.teachingplatform.lab.behavior.dto.BehaviorRequests;
import com.opencode.teachingplatform.lab.behavior.entity.BehaviorEvent;
import com.opencode.teachingplatform.lab.behavior.enums.BehaviorEventType;
import com.opencode.teachingplatform.lab.behavior.repository.BehaviorEventRepository;
import com.opencode.teachingplatform.lab.entity.Lab;
import com.opencode.teachingplatform.lab.entity.LabStep;
import com.opencode.teachingplatform.lab.entity.LabSubmission;
import com.opencode.teachingplatform.lab.repository.LabRepository;
import com.opencode.teachingplatform.lab.repository.LabStepRepository;
import com.opencode.teachingplatform.lab.repository.LabSubmissionRepository;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生作答行为事件接收服务（技术方案 §6.1 BehaviorIngestService）。
 *
 * <p>职责：批量接收 → 逐条容错校验 → 幂等去重 → 落库。核心语义：</p>
 * <ul>
 *   <li>逐条校验失败计入 dropped，<b>不整批 400</b>（仅"空列表 / 超批量上限"整批失败，§5.1）；</li>
 *   <li>event_uuid 幂等：重复 uuid 丢弃，配合前端失败重试（D4）；</li>
 *   <li>实验 CLOSED 后拒收新事件，返回 {@code closed:true} 让前端停采（D16）；</li>
 *   <li>PASTE 的 {@code blocked} 由服务端按 {@code step.allowPaste} 回填，客户端传入值一律覆盖（D13）；</li>
 *   <li>时钟容差：occurredAt 与服务端偏差超容差时以 receivedAt 替代并打 {@code clockAdjusted} 标记（D6）。</li>
 * </ul>
 *
 * <p><b>刻意不声明类级 {@code @Transactional}</b>：唯一约束冲突兜底需要精确到单条，
 * 让 {@code save} 走仓储自带的独立事务，冲突条 catch 后按 dropped 计、不影响同批其他条目；
 * 若整批一个事务，一条冲突会导致全批回滚。</p>
 */
@Service
public class BehaviorIngestService {

    private final BehaviorEventRepository behaviorEventRepository;
    private final LabRepository labRepository;
    private final LabStepRepository labStepRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final ClassMemberRepository classMemberRepository;
    private final BehaviorProperties behaviorProperties;
    private final ObjectMapper objectMapper;

    public BehaviorIngestService(BehaviorEventRepository behaviorEventRepository,
                                 LabRepository labRepository,
                                 LabStepRepository labStepRepository,
                                 LabSubmissionRepository labSubmissionRepository,
                                 ClassMemberRepository classMemberRepository,
                                 BehaviorProperties behaviorProperties,
                                 ObjectMapper objectMapper) {
        this.behaviorEventRepository = behaviorEventRepository;
        this.labRepository = labRepository;
        this.labStepRepository = labStepRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.classMemberRepository = classMemberRepository;
        this.behaviorProperties = behaviorProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量接收行为事件。
     *
     * @return {@code {accepted, dropped, closed?}}；closed 仅在实验已关闭拒收时出现且为 true
     */
    public Map<String, Object> ingest(CurrentUser currentUser, Long labId, BehaviorRequests.IngestBatch batch) {
        // 1. 身份与实验可及性：与学生作答同一口径（方案 D16）
        requireStudent(currentUser);
        Lab lab = accessiblePublishedLab(currentUser, labId);

        // 2. 整批级校验：空列表 / 超上限 → 40000（§5.1 校验表第一行）
        List<BehaviorRequests.IngestItem> items = batch == null || batch.events() == null
                ? List.of()
                : batch.events();
        if (items.isEmpty()) {
            throw new BusinessException(40000, "事件列表不能为空");
        }
        if (items.size() > behaviorProperties.getIngest().getMaxBatchSize()) {
            throw new BusinessException(40000, "事件批量大小超限");
        }

        // 3. 实验已关闭：拒收但不抛异常，前端凭 closed:true 停采（D16）
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("accepted", 0);
            rejected.put("dropped", items.size());
            rejected.put("closed", true);
            return rejected;
        }

        // 4. 批量预取：step 校验与 PASTE 回填用；submission 仅作冗余回填（D5）
        Map<Long, LabStep> stepById = labStepRepository.findByLabIdOrderByStepNoAsc(labId).stream()
                .collect(Collectors.toMap(LabStep::getId, Function.identity()));
        LabSubmission submission = labSubmissionRepository
                .findByLabIdAndStudentId(labId, currentUser.id())
                .orElse(null);

        // 5. 幂等去重：已存在的 uuid 整批过滤（D4）
        List<String> uuids = items.stream()
                .map(BehaviorRequests.IngestItem::eventUuid)
                .filter(uuid -> uuid != null && !uuid.isBlank() && uuid.length() <= 36)
                .toList();
        Set<String> existingUuids = uuids.isEmpty()
                ? Set.of()
                : behaviorEventRepository.findByEventUuidIn(uuids).stream()
                        .map(BehaviorEvent::getEventUuid)
                        .collect(Collectors.toSet());

        // 6. 逐条校验 + 构造实体
        OffsetDateTime now = OffsetDateTime.now();
        long toleranceMs = behaviorProperties.getIngest().getClockSkewToleranceMs();
        int maxPayloadBytes = behaviorProperties.getIngest().getMaxPayloadBytes();
        List<BehaviorEvent> toSave = new ArrayList<>(items.size());
        int dropped = 0;
        for (BehaviorRequests.IngestItem item : items) {
            BehaviorEvent event = buildEvent(item, currentUser, labId, submission, stepById,
                    existingUuids, now, toleranceMs, maxPayloadBytes);
            if (event == null) {
                dropped++;
            } else {
                toSave.add(event);
                // 同批内重复 uuid 也按幂等丢弃
                existingUuids.add(event.getEventUuid());
            }
        }

        // 7. 逐条落库：唯一约束并发冲突时该条按 dropped 计，不抛出（§6.1 步骤 6 兜底）
        int accepted = 0;
        for (BehaviorEvent event : toSave) {
            try {
                behaviorEventRepository.save(event);
            } catch (DataIntegrityViolationException ex) {
                dropped++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // dropped 已含校验失败与落库冲突条，accepted 为其余全部
        result.put("accepted", items.size() - dropped);
        result.put("dropped", dropped);
        return result;
    }

    /**
     * 构造单条事件实体；任一条校验失败返回 null（该条计入 dropped）。
     */
    private BehaviorEvent buildEvent(BehaviorRequests.IngestItem item,
                                     CurrentUser currentUser,
                                     Long labId,
                                     LabSubmission submission,
                                     Map<Long, LabStep> stepById,
                                     Set<String> existingUuids,
                                     OffsetDateTime now,
                                     long toleranceMs,
                                     int maxPayloadBytes) {
        if (item == null) {
            return null;
        }
        // uuid：非空 ≤36 字符且未出现（含同批重复）
        String uuid = item.eventUuid();
        if (uuid == null || uuid.isBlank() || uuid.length() > 36 || existingUuids.contains(uuid)) {
            return null;
        }
        // 事件类型：非法值该条丢弃（D3）
        BehaviorEventType type = BehaviorEventType.parseOrNull(item.eventType());
        if (type == null) {
            return null;
        }
        // occurredAt：可解析；超容差以 receivedAt 替代并打标记（D6）
        OffsetDateTime occurredAt = parseOccurredAt(item.occurredAt());
        if (occurredAt == null) {
            return null;
        }
        Map<String, Object> payload = item.payload() == null
                ? new HashMap<>()
                : new HashMap<>(item.payload());
        boolean clockAdjusted = Math.abs(occurredAt.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()) > toleranceMs;
        if (clockAdjusted) {
            occurredAt = now;
            payload.put("clockAdjusted", true);
        }
        // stepId：非空时必须属于该实验
        Long stepId = item.stepId();
        LabStep step = null;
        if (stepId != null) {
            step = stepById.get(stepId);
            if (step == null) {
                return null;
            }
        }
        // PASTE：blocked 由服务端按 step.allowPaste 回填，覆盖客户端伪造值（D13）
        if (type == BehaviorEventType.PASTE && step != null) {
            payload.put("blocked", !step.isAllowPaste());
        }
        // payload 序列化体积上限；空 payload 合法（落库为 null），超限或不可序列化该条丢弃
        String payloadJson = null;
        if (!payload.isEmpty()) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException ex) {
                return null;
            }
            if (payloadJson.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes) {
                return null;
            }
        }

        BehaviorEvent event = new BehaviorEvent();
        event.setEventUuid(uuid);
        event.setEventType(type.name());
        event.setOccurredAt(occurredAt);
        // receivedAt 代码显式赋值，DB DEFAULT 仅为兜底（§6.1 步骤 5）
        event.setReceivedAt(now);
        event.setStudentId(currentUser.id());
        event.setLabId(labId);
        event.setSubmissionId(submission == null ? null : submission.getId());
        event.setStepId(stepId);
        event.setPayloadJson(payloadJson);
        event.setSchoolId(currentUser.schoolId());
        // trace / ip 与 AuditLogService 同源：统一走 RequestTraceContext
        event.setTraceId(RequestTraceContext.getTraceId());
        event.setClientIp(RequestTraceContext.getClientIp());
        return event;
    }

    private OffsetDateTime parseOccurredAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    // ---- 与 LabService 同口径的访问校验（自包含实现，避免 behavior 包反向依赖庞大的 LabService） ----

    private void requireStudent(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.STUDENT) {
            throw new BusinessException(40300, "无学生权限");
        }
    }

    private Lab accessiblePublishedLab(CurrentUser currentUser, Long labId) {
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new BusinessException(40400, "实验不存在"));
        if (lab.getStatus() == ActivityStatus.DRAFT) {
            throw new BusinessException(40300, "无权限访问该实验");
        }
        boolean enrolled = classMemberRepository
                .findByClassIdAndStudentUserId(lab.getClassId(), currentUser.id())
                .isPresent();
        if (!enrolled) {
            throw new BusinessException(40300, "无权限访问该实验");
        }
        return lab;
    }
}
