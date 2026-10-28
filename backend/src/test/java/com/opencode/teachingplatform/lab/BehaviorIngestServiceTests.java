package com.opencode.teachingplatform.lab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.trace.RequestTraceContext;
import com.opencode.teachingplatform.lab.behavior.BehaviorConfig;
import com.opencode.teachingplatform.lab.behavior.dto.BehaviorRequests;
import com.opencode.teachingplatform.lab.behavior.entity.BehaviorEvent;
import com.opencode.teachingplatform.lab.behavior.enums.BehaviorEventType;
import com.opencode.teachingplatform.lab.behavior.repository.BehaviorEventRepository;
import com.opencode.teachingplatform.lab.behavior.service.BehaviorIngestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BehaviorIngestService 单测（技术方案 §8.1 用例 1-7b）。
 *
 * <p>标准 {@code @DataJpaTest} 事务回滚即可——ingest 逐条 save 走同一事务，
 * 测试结束统一回滚，无需手动清理。{@code @Import} 显式注册 BehaviorIngestService
 * 与其依赖的 {@link BehaviorConfig}（注册 BehaviorProperties）。</p>
 *
 * <p>用例覆盖：正常批量 11 类事件、空/超限、非法类型/超长 payload/错 stepId 单条丢弃、
 * 重复 uuid 幂等、时钟容差 clockAdjusted、CLOSED 拒收、角色与班级权限、PASTE blocked 回填。</p>
 */
@DataJpaTest
@Import({BehaviorIngestService.class, BehaviorConfig.class, BehaviorIngestServiceTests.TestBeans.class})
@ActiveProfiles("test")
class BehaviorIngestServiceTests {

    @Autowired
    private BehaviorIngestService behaviorIngestService;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final CurrentUser STUDENT = new CurrentUser(200L, "stu-200", "演示学生", UserRole.STUDENT);
    private static final CurrentUser TEACHER = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
    private static final CurrentUser OTHER_STUDENT = new CurrentUser(300L, "stu-300", "其他学生", UserRole.STUDENT);

    private static int generatedExperimentId = 7000;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @BeforeEach
    void setUp() {
        seedStudentAccount(STUDENT);
        seedStudentAccount(OTHER_STUDENT);
        RequestTraceContext.set("trace-test", "10.0.0.1");
    }

    @AfterEach
    void tearDown() {
        RequestTraceContext.clear();
    }

    // ---- 用例 1：正常批量 11 类事件混合 20 条 ----
    @Test
    void ingestAcceptsAllElevenEventTypesAndBackfillsFields() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        Long step1 = seedStep(labId, 1, "TEXT", true);
        Long step2 = seedStep(labId, 2, "CODE", true);
        seedMembership(classId, STUDENT.id());
        Long submissionId = seedSubmission(labId, STUDENT.id(), 0);

        List<BehaviorRequests.IngestItem> items = new ArrayList<>();
        // 11 类事件 + 额外 9 条凑足 20 条，覆盖全部事件目录（D3）
        items.add(item("LAB_ENTER", nowMinus(0), null, Map.of()));
        items.add(item("LAB_LEAVE", nowMinus(0), null, Map.of("reason", "route", "lastStepId", step1, "dwellMs", 3000)));
        items.add(item("STEP_VIEW", nowMinus(0), step1, Map.of("fromStepId", step2, "dwellMs", 2500)));
        items.add(item("PASTE", nowMinus(0), step1, Map.of("pastedLength", 120, "fieldKind", "TEXT")));
        items.add(item("VISIBILITY", nowMinus(0), null, Map.of("state", "hidden")));
        items.add(item("WINDOW_BLUR", nowMinus(0), null, Map.of()));
        items.add(item("WINDOW_FOCUS", nowMinus(0), null, Map.of("blurMs", 800)));
        items.add(item("HEARTBEAT", nowMinus(0), null, Map.of("activeMs", 60000)));
        items.add(item("SUBMIT_CLICK", nowMinus(0), null, Map.of()));
        items.add(item("SUBMIT_BLOCKED", nowMinus(0), null, Map.of("reason", "no-saved-answer")));
        items.add(item("SAVE_FAILED", nowMinus(0), step1, Map.of("stepId", step1, "silent", true)));
        // 额外 9 条心跳，总量 20
        for (int i = 0; i < 9; i++) {
            items.add(item("HEARTBEAT", nowMinus(0), null, Map.of("activeMs", 60000)));
        }

        Map<String, Object> result = behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(items));

        assertThat(result.get("accepted")).isEqualTo(20);
        assertThat(result.get("dropped")).isEqualTo(0);

        List<BehaviorEvent> saved = behaviorEventRepository.findByBizTypeAndLabIdAndStudentIdOrderByIdAsc(
                BehaviorEvent.BIZ_TYPE_LAB, labId, STUDENT.id());
        assertThat(saved).hasSize(20);
        // 落库字段校验：归属键、submission_id 回填、trace/ip/school 来自上下文（D1/D5）
        BehaviorEvent first = saved.get(0);
        assertThat(first.getEventType()).isEqualTo("LAB_ENTER");
        assertThat(first.getBizType()).isEqualTo(BehaviorEvent.BIZ_TYPE_LAB);
        assertThat(first.getStudentId()).isEqualTo(STUDENT.id());
        assertThat(first.getLabId()).isEqualTo(labId);
        assertThat(first.getSubmissionId()).isEqualTo(submissionId);
        assertThat(first.getSchoolId()).isEqualTo(1L);
        assertThat(first.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(first.getTraceId()).isEqualTo("trace-test");
        assertThat(first.getReceivedAt()).isNotNull();
    }

    // ---- 用例 2：空 events / 101 条 → 整批 40000 ----
    @Test
    void ingestRejectsEmptyAndOverSizedBatch() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        seedMembership(classId, STUDENT.id());

        assertThatThrownBy(() -> behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件列表不能为空");

        List<BehaviorRequests.IngestItem> oversized = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            oversized.add(item("HEARTBEAT", nowMinus(0), null, Map.of("activeMs", 1000)));
        }
        assertThatThrownBy(() -> behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(oversized)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件批量大小超限");
    }

    // ---- 用例 3：非法 type / 超长 payload / 错 stepId → 单条 dropped，其余 accepted ----
    @Test
    void ingestDropsInvalidItemsButKeepsValidOnes() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        Long step1 = seedStep(labId, 1, "TEXT", true);
        seedMembership(classId, STUDENT.id());

        Map<String, Object> hugePayload = new HashMap<>();
        hugePayload.put("activeMs", 1000);
        hugePayload.put("noise", "x".repeat(5000));

        List<BehaviorRequests.IngestItem> items = List.of(
                item("HEARTBEAT", nowMinus(0), null, Map.of("activeMs", 1000)),
                item("NOT_A_TYPE", nowMinus(0), null, Map.of()),
                item("HEARTBEAT", nowMinus(0), null, hugePayload),
                item("STEP_VIEW", nowMinus(0), 999999L, Map.of("dwellMs", 100))
        );

        Map<String, Object> result = behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(items));

        assertThat(result.get("accepted")).isEqualTo(1);
        assertThat(result.get("dropped")).isEqualTo(3);
        assertThat(behaviorEventRepository.count()).isEqualTo(1);
    }

    // ---- 用例 4：重复 uuid 重发 → accepted=0 dropped=n，行数不变（幂等） ----
    @Test
    void ingestDeduplicatesByEventUuid() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        seedMembership(classId, STUDENT.id());

        String uuid = UUID.randomUUID().toString();
        BehaviorRequests.IngestItem item = item("LAB_ENTER", nowMinus(0), null, Map.of());
        // 用反射式构造：item 方法内部生成 uuid，这里覆盖
        BehaviorRequests.IngestItem fixed = new BehaviorRequests.IngestItem(uuid, "LAB_ENTER", nowMinus(0), null, Map.of());
        List<BehaviorRequests.IngestItem> first = List.of(fixed);

        Map<String, Object> r1 = behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(first));
        assertThat(r1.get("accepted")).isEqualTo(1);
        assertThat(r1.get("dropped")).isEqualTo(0);
        assertThat(behaviorEventRepository.count()).isEqualTo(1);

        // 同批重发
        Map<String, Object> r2 = behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(first));
        assertThat(r2.get("accepted")).isEqualTo(0);
        assertThat(r2.get("dropped")).isEqualTo(1);
        assertThat(behaviorEventRepository.count()).isEqualTo(1);
    }

    // ---- 用例 5：occurredAt 超容差（未来 1 小时）→ 接收且 payload 含 clockAdjusted:true ----
    @Test
    void ingestAdjustsClockSkewAndMarksPayload() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        seedMembership(classId, STUDENT.id());

        // 客户端时间比服务端快 1 小时，超过默认 10 分钟容差
        String futureTime = OffsetDateTime.now().plusHours(1).toString();
        BehaviorRequests.IngestItem item = new BehaviorRequests.IngestItem(
                UUID.randomUUID().toString(), "LAB_ENTER", futureTime, null, Map.of());

        Map<String, Object> result = behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(List.of(item)));
        assertThat(result.get("accepted")).isEqualTo(1);

        BehaviorEvent saved = behaviorEventRepository.findAll().get(0);
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(saved.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(payload.get("clockAdjusted")).isEqualTo(Boolean.TRUE);
        // 发生时间被替换为服务端接收时间附近（±10 分钟内）
        assertThat(saved.getOccurredAt()).isBefore(OffsetDateTime.now().plusSeconds(1));
    }

    // ---- 用例 6：实验 CLOSED → {accepted:0, closed:true}，不落库 ----
    @Test
    void ingestRejectsClosedLabWithoutStoring() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.CLOSED, TEACHER.id());
        seedMembership(classId, STUDENT.id());

        Map<String, Object> result = behaviorIngestService.ingest(STUDENT, labId,
                new BehaviorRequests.IngestBatch(List.of(item("LAB_ENTER", nowMinus(0), null, Map.of()))));

        assertThat(result.get("accepted")).isEqualTo(0);
        assertThat(result.get("dropped")).isEqualTo(1);
        assertThat(result.get("closed")).isEqualTo(true);
        assertThat(behaviorEventRepository.count()).isZero();
    }

    // ---- 用例 7：教师角色 → 403；非本班学生 → 业务异常 ----
    @Test
    void ingestRejectsTeacherAndNonMemberStudent() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        seedMembership(classId, STUDENT.id());

        BehaviorRequests.IngestBatch batch = new BehaviorRequests.IngestBatch(List.of(item("LAB_ENTER", nowMinus(0), null, Map.of())));

        // 教师角色调用 ingest 服务方法本身会被 requireStudent 拦截
        assertThatThrownBy(() -> behaviorIngestService.ingest(TEACHER, labId, batch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无学生权限");

        // 非本班学生
        assertThatThrownBy(() -> behaviorIngestService.ingest(OTHER_STUDENT, labId, batch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该实验");
    }

    // ---- 用例 7b：PASTE blocked 由服务端按 step.allowPaste 回填，覆盖客户端伪造值 ----
    @Test
    void ingestOverwritesPasteBlockedByServerSideAllowPaste() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        Long allowStep = seedStep(labId, 1, "TEXT", true);
        Long denyStep = seedStep(labId, 2, "TEXT", false);
        seedMembership(classId, STUDENT.id());

        List<BehaviorRequests.IngestItem> items = List.of(
                // allowPaste=true 步骤：客户端不传 blocked → 服务端写 blocked:false
                item("PASTE", nowMinus(0), allowStep, Map.of("pastedLength", 50, "fieldKind", "TEXT")),
                // allowPaste=false 步骤：客户端伪造 blocked:false → 服务端覆盖为 true
                item("PASTE", nowMinus(0), denyStep, Map.of("pastedLength", 30, "fieldKind", "TEXT", "blocked", false)),
                // allowPaste=true 步骤：客户端伪造 blocked:true → 服务端覆盖为 false
                item("PASTE", nowMinus(0), allowStep, Map.of("pastedLength", 20, "fieldKind", "TEXT", "blocked", true))
        );

        Map<String, Object> result = behaviorIngestService.ingest(STUDENT, labId, new BehaviorRequests.IngestBatch(items));
        assertThat(result.get("accepted")).isEqualTo(3);

        List<BehaviorEvent> saved = behaviorEventRepository.findByBizTypeAndLabIdAndStudentIdOrderByIdAsc(
                BehaviorEvent.BIZ_TYPE_LAB, labId, STUDENT.id());
        assertThat(saved).hasSize(3);

        Map<Long, Map<String, Object>> payloadByStep = new HashMap<>();
        for (BehaviorEvent e : saved) {
            try {
                payloadByStep.put(e.getStepId(), objectMapper.readValue(e.getPayloadJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        // allowPaste=true → blocked:false（无论客户端传什么）
        assertThat(payloadByStep.get(allowStep).get("blocked")).isEqualTo(Boolean.FALSE);
        // allowPaste=false → blocked:true（覆盖客户端伪造的 false）
        assertThat(payloadByStep.get(denyStep).get("blocked")).isEqualTo(Boolean.TRUE);
    }

    // ---- 辅助构造 ----

    private BehaviorRequests.IngestItem item(String type, String occurredAt, Long stepId, Map<String, Object> payload) {
        return new BehaviorRequests.IngestItem(UUID.randomUUID().toString(), type, occurredAt, stepId, payload);
    }

    private String nowMinus(int minutes) {
        return OffsetDateTime.now().minusMinutes(minutes).toString();
    }

    private Long seedClass(Long teacherId) {
        long id = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO class_room (id, school_id, name, code, status, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, "实验班", "LAB-" + id, "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_id, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', (SELECT id FROM school_subject WHERE school_id = 1 AND code = 'SE-ARCH'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, teacherId);
        return id;
    }

    private void seedMembership(Long classId, Long studentId) {
        long id = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO class_member (id, class_id, student_user_id, joined_at, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, classId, studentId);
    }

    private void seedStudentAccount(CurrentUser student) {
        jdbcTemplate.update("""
                MERGE INTO sys_user (id, username, password_hash, display_name, role, status, must_change_password, school_id, created_at, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, student.id(), student.username(), "{noop}123456", student.displayName(),
                student.role().name(), "ACTIVE", false);
    }

    private Long seedLab(Long classId, ActivityStatus status, Long teacherId) {
        long id = generatedExperimentId++;
        int state = switch (status) {
            case DRAFT -> 0;
            case PUBLISHED -> 1;
            case CLOSED -> 2;
        };
        jdbcTemplate.update("""
                INSERT INTO t_experiment (experiment_id, experiment_no, experiment_name, experiment_type, instruction_type,
                                           experiment_requirement, experiment_content, state, status, material_id,
                                           class_id, created_by, score_visibility_mode, score_released, summary_required,
                                           created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, (int) id, "实验-" + id, 1, "1", "desc", "desc", state, status.name(), null,
                classId, teacherId, "AFTER_TEACHER_CONFIRM", false, false);
        return id;
    }

    private Long seedStep(Long labId, int stepNo, String questionType, boolean allowPaste) {
        long id = Math.abs(System.nanoTime());
        int questionTypeCode = switch (questionType) {
            case "SINGLE_CHOICE" -> 1;
            case "MULTIPLE_CHOICE" -> 2;
            case "FILL_BLANK" -> 3;
            case "TRUE_FALSE" -> 4;
            case "CODE" -> 6;
            default -> 5;
        };
        jdbcTemplate.update("""
                INSERT INTO t_experiment_item (id, experiment_id, experiment_item_no, experiment_item_name,
                                                experiment_item_type, experiment_item_content, experiment_item_answer,
                                                experiment_item_score, state, question_type, answer_config_json,
                                                scoring_config_json, question_id, question_snapshot_json, editor_language, allow_paste,
                                                created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, labId, stepNo, "步骤" + stepNo, questionTypeCode, "content", "{}", 10,
                questionType, "{}", "{}", null, "{\"legacyQuestionType\":\"" + questionType + "\"}",
                "CODE".equals(questionType) ? "JAVA" : "TEXT", allowPaste);
        return id;
    }

    private Long seedSubmission(Long labId, Long studentId, int totalScore) {
        long id = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, total_score, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, labId, studentId, "SAVED", totalScore);
        return id;
    }
}
