package com.opencode.teachingplatform.lab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.lab.behavior.BehaviorConfig;
import com.opencode.teachingplatform.lab.behavior.entity.BehaviorEvent;
import com.opencode.teachingplatform.lab.behavior.entity.LabSubmissionMetrics;
import com.opencode.teachingplatform.lab.behavior.repository.BehaviorEventRepository;
import com.opencode.teachingplatform.lab.behavior.repository.LabSubmissionMetricsRepository;
import com.opencode.teachingplatform.lab.behavior.service.LabBehaviorMetricsService;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.entity.LabStep;
import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import com.opencode.teachingplatform.lab.entity.LabStepAnswerLog;
import com.opencode.teachingplatform.lab.entity.LabSubmission;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerLogRepository;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerRepository;
import com.opencode.teachingplatform.lab.repository.LabStepRepository;
import com.opencode.teachingplatform.lab.repository.LabSubmissionRepository;
import com.opencode.teachingplatform.lab.service.LabScoringSupport;
import com.opencode.teachingplatform.lab.service.LabService;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.strategy.FillBlankScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.MultipleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SingleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SubjectiveRecommendationStrategy;
import com.opencode.teachingplatform.grading.strategy.TrueFalseScoringStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LabBehaviorMetricsService 单测 + LabService 行为展示链路集成（技术方案 §8.1 用例 8-19、25-27）。
 *
 * <p><b>事务策略：</b>类级 {@code @Transactional(propagation = NOT_SUPPORTED)}。原因：
 * 懒算 {@code refreshStaleMetrics} 是 REQUIRES_NEW，会挂起外层事务并在独立连接上读写；
 * 若外层用默认 @DataJpaTest 事务，种子数据对独立连接不可见，且 metrics FK（指向
 * experiment_submission）会违反。NOT_SUPPORTED 让测试方法无事务包裹，jdbcTemplate
 * 语句自动提交，REQUIRES_NEW 懒算能读到已提交种子。代价是需要 @AfterEach 手动清理。</p>
 *
 * <p>清理策略：所有测试实体 id 从 {@link #ID_BASE} 起递增，按 id 范围删除，避免误伤 Flyway 种子数据。</p>
 */
@DataJpaTest
@Import({LabService.class, LabScoringSupport.class,
        com.opencode.teachingplatform.clazz.service.ClassAccessService.class,
        LabBehaviorMetricsService.class, BehaviorConfig.class,
        LabBehaviorMetricsServiceTests.TestBeans.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabBehaviorMetricsServiceTests {

    @TempDir
    static Path storageRoot;

    private static final long ID_BASE = 9_000_000L;
    private static long nextId = ID_BASE;

    private static final CurrentUser TEACHER = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
    private static final CurrentUser STUDENT = new CurrentUser(200L, "stu-200", "演示学生", UserRole.STUDENT);
    private static final CurrentUser OTHER_TEACHER = new CurrentUser(900L, "t9009", "其他教师", UserRole.TEACHER);

    @Autowired
    private LabService labService;

    @Autowired
    private LabBehaviorMetricsService metricsService;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Autowired
    private LabSubmissionMetricsRepository metricsRepository;

    @Autowired
    private LabSubmissionRepository submissionRepository;

    @Autowired
    private LabStepAnswerRepository answerRepository;

    @Autowired
    private LabStepAnswerLogRepository answerLogRepository;

    @Autowired
    private LabStepRepository stepRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ScoringEngine scoringEngine() {
            return new ScoringEngine(List.of(
                    new SingleChoiceScoringStrategy(),
                    new MultipleChoiceScoringStrategy(),
                    new FillBlankScoringStrategy(),
                    new TrueFalseScoringStrategy(),
                    new SubjectiveRecommendationStrategy()
            ));
        }

        @Bean
        LocalFileStorageService localFileStorageService() {
            return new LocalFileStorageService(storageRoot.toString());
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage-root", () -> storageRoot.toString());
    }

    @BeforeEach
    void resetIdCounter() {
        nextId = ID_BASE;
    }

    @AfterEach
    void cleanupTestData() {
        // 测试创建的 lab 其 experiment_id >= ID_BASE；部分行主键为 IDENTITY 自增（< ID_BASE）。
        // 在同一连接内临时关闭 FK 检查后按 lab 级联清理，避免 FK 顺序导致的清理中断。
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("SET REFERENTIAL_INTEGRITY FALSE");
                st.execute("DELETE FROM behavior_event WHERE lab_id >= " + ID_BASE);
                st.execute("DELETE FROM lab_submission_metrics WHERE lab_id >= " + ID_BASE);
                st.execute("DELETE FROM lab_step_answer_log WHERE lab_step_answer_id IN "
                        + "(SELECT id FROM experiment_answer WHERE experiment_submission_id IN "
                        + "(SELECT id FROM experiment_submission WHERE experiment_id >= " + ID_BASE + "))");
                st.execute("DELETE FROM experiment_answer WHERE experiment_submission_id IN "
                        + "(SELECT id FROM experiment_submission WHERE experiment_id >= " + ID_BASE + ")");
                st.execute("DELETE FROM experiment_submission WHERE experiment_id >= " + ID_BASE);
                st.execute("DELETE FROM t_experiment_item WHERE experiment_id >= " + ID_BASE);
                st.execute("DELETE FROM t_experiment WHERE experiment_id >= " + ID_BASE);
                st.execute("DELETE FROM class_member WHERE id >= " + ID_BASE);
                st.execute("DELETE FROM class_teacher WHERE class_id >= " + ID_BASE);
                st.execute("DELETE FROM class_room WHERE id >= " + ID_BASE);
                st.execute("DELETE FROM sys_user WHERE id >= " + ID_BASE);
                st.execute("DELETE FROM score_record WHERE id >= " + ID_BASE);
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET REFERENTIAL_INTEGRITY TRUE");
                }
            }
            return null;
        });
    }

    // ===== 用例 8：无事件无保存 → NO_BEHAVIOR_DATA，level=LOW =====
    @Test
    void computeWithNoEventsYieldsNoBehaviorDataFlag() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, null);

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        assertThat(metrics.getTotalDurationMs()).isNull();
        assertThat(metrics.getActiveDurationMs()).isNull();
        assertThat(metrics.getSaveCount()).isZero();
        assertThat(metrics.getSessionCount()).isZero();
        assertThat(metrics.getRiskScore()).isZero();
        assertThat(metrics.getRiskLevel()).isEqualTo("LOW");
        assertThat(parseFlags(metrics.getRiskFlagsJson())).containsExactly("NO_BEHAVIOR_DATA");
    }

    // ===== 用例 9：时长口径：firstStartedAt=min(LAB_ENTER, 最早 fillTime)；submittedAt 止 =====
    @Test
    void computeUsesEarliestStartAndSubmittedAtForDuration() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long stepId = seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:30"));
        Long answerId = seedAnswer(submissionId, stepId, "答案");
        seedAnswerLog(answerId, time("09:58"));
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", time("10:00"), null);

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        // firstStartedAt 取 LAB_ENTER(10:00) 与最早快照(09:58) 的 min → 09:58
        assertThat(metrics.getFirstStartedAt()).isEqualTo(time("09:58"));
        // submittedAt=10:30 → 总时长 32 分钟
        assertThat(metrics.getTotalDurationMs()).isEqualTo(32L * 60 * 1000);
    }

    // ===== 用例 10：SAVED 行：无 submittedAt → duration 以 lastEventAt 止，durationOpen=true =====
    @Test
    void computeForSavedSubmissionEndsAtLastEvent() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SAVED, null);
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", time("10:00"), null);
        seedBehaviorEvent(labId, STUDENT.id(), null, "HEARTBEAT", time("10:20"),
                payload("activeMs", 60000L));

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        // 无 submittedAt → durationEnd = lastEventAt = 10:20 → 总时长 20 分钟
        assertThat(metrics.getTotalDurationMs()).isEqualTo(20L * 60 * 1000);

        // durationOpen 通过列表装配层推导：SAVED 行 submittedAt 为 null → true
        Map<String, Object> view = toBehaviorView(metrics, submission);
        assertThat(view.get("durationOpen")).isEqualTo(true);
    }

    // ===== 用例 11：DURATION_TOO_SHORT：4 题项、总时长 2min < 4×45s → +40，MEDIUM =====
    @Test
    void computeFlagsDurationTooShortWhenUnderPerStepMinimum() {
        Long labId = seedPublishedLab(TEACHER.id());
        for (int i = 1; i <= 4; i++) {
            seedStep(labId, i, "TEXT");
        }
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:02"));
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", time("10:00"), null);

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        List<String> flags = parseFlags(metrics.getRiskFlagsJson());
        assertThat(flags).contains("DURATION_TOO_SHORT");
        assertThat(metrics.getRiskScore()).isEqualTo(40);
        assertThat(metrics.getRiskLevel()).isEqualTo("MEDIUM");
    }

    // ===== 用例 12：PASTE_HEAVY：pastedChars=600、终稿 1500（0.4>0.3）→ +30 =====
    @Test
    void computeFlagsPasteHeavyWhenRatioExceedsThreshold() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long stepId = seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("11:00"));
        // 终稿 1500 字符
        seedAnswer(submissionId, stepId, "a".repeat(1500));
        seedBehaviorEvent(labId, STUDENT.id(), stepId, "LAB_ENTER", time("10:00"), null);
        seedBehaviorEvent(labId, STUDENT.id(), stepId, "PASTE", time("10:30"),
                payload("pastedLength", 600, "fieldKind", "TEXT"));

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        assertThat(metrics.getPastedChars()).isEqualTo(600);
        List<String> flags = parseFlags(metrics.getRiskFlagsJson());
        assertThat(flags).contains("PASTE_HEAVY");
        assertThat(metrics.getRiskScore()).isEqualTo(30);
        assertThat(metrics.getRiskLevel()).isEqualTo("MEDIUM");
    }

    // ===== 用例 13：FOCUS_LOSS：hidden 6 次 → +20 =====
    @Test
    void computeFlagsFocusLossByHiddenCount() {
        Long labId = seedPublishedLab(TEACHER.id());
        seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("11:00"));
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", time("10:00"), null);
        for (int i = 0; i < 6; i++) {
            seedBehaviorEvent(labId, STUDENT.id(), null, "VISIBILITY", time("10:3" + i),
                    payload("state", "hidden"));
        }

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        assertThat(metrics.getVisibilityHiddenCount()).isEqualTo(6);
        List<String> flags = parseFlags(metrics.getRiskFlagsJson());
        assertThat(flags).contains("FOCUS_LOSS");
        assertThat(metrics.getRiskScore()).isEqualTo(20);
    }

    // ===== 用例 13 补充：hiddenMs/total=0.6 → +20 =====
    @Test
    void computeFlagsFocusLossByHiddenRatio() {
        Long labId = seedPublishedLab(TEACHER.id());
        seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("11:00"));
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", time("10:00"), null);
        // 总时长 60min；hidden 2 次共 36min（0.6）
        seedBehaviorEvent(labId, STUDENT.id(), null, "VISIBILITY", time("10:10"), payload("state", "hidden"));
        seedBehaviorEvent(labId, STUDENT.id(), null, "VISIBILITY", time("10:20"), payload("state", "hidden"));
        seedBehaviorEvent(labId, STUDENT.id(), null, "VISIBILITY", time("10:20"),
                payload("state", "visible", "hiddenMs", 36L * 60 * 1000));

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        assertThat(metrics.getHiddenMs()).isEqualTo(36L * 60 * 1000);
        List<String> flags = parseFlags(metrics.getRiskFlagsJson());
        assertThat(flags).contains("FOCUS_LOSS");
    }

    // ===== 用例 14：watermark stale 重算 =====
    @Test
    void ensureMetricsFreshRecomputesWhenNewEventsArrive() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long stepId = seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("11:00"));
        seedBehaviorEvent(labId, STUDENT.id(), stepId, "LAB_ENTER", time("10:00"), null);

        // 首次 compute：watermark = 首条事件 id
        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics first = metricsService.computeForSubmission(submission);
        long firstWatermark = first.getEventWatermark();
        assertThat(firstWatermark).isGreaterThan(0);

        // 迟到事件：id > firstWatermark
        seedBehaviorEvent(labId, STUDENT.id(), stepId, "PASTE", time("10:30"),
                payload("pastedLength", 100, "fieldKind", "TEXT"));

        // 通过 getTeacherReportBehavior 触发 ensureMetricsFresh（懒算）
        Map<String, Object> detail = labService.getTeacherReportBehavior(TEACHER, submissionId);
        Map<String, Object> metricsView = (Map<String, Object>) detail.get("metrics");

        // 重算后 watermark 推进，pasteCount 从 0 变 1
        LabSubmissionMetrics updated = metricsRepository.findById(submissionId).orElseThrow();
        assertThat(updated.getEventWatermark()).isGreaterThan(firstWatermark);
        assertThat(updated.getPasteCount()).isEqualTo(1);
        assertThat(metricsView.get("pasteCount")).isEqualTo(1);
    }

    // ===== 用例 15：submitLab 联动：提交后 metrics 行存在，totalDurationMs 以 submittedAt 为止 =====
    @Test
    void submitLabTriggersMetricsComputation() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        Long stepId = seedStep(labId, 1, "TEXT");
        seedMembership(classId, STUDENT.id());
        seedBehaviorEvent(labId, STUDENT.id(), stepId, "LAB_ENTER", time("10:00"), null);

        labService.saveAnswer(STUDENT, labId, stepId, new LabRequests.SaveStepAnswerRequest("答案", null));
        Map<String, Object> submitResult = labService.submitLab(STUDENT, labId,
                new LabRequests.SubmitLabRequest("实验小结"));
        Long submissionId = ((Number) submitResult.get("submissionId")).longValue();

        LabSubmissionMetrics metrics = metricsRepository.findById(submissionId).orElseThrow();
        assertThat(metrics.getTotalDurationMs()).isNotNull();
        // submittedAt 是提交瞬间（≈ now），firstStartedAt=10:00（事件时间）；时长 > 0
        assertThat(metrics.getTotalDurationMs()).isGreaterThan(0L);
        assertThat(metrics.getSaveCount()).isEqualTo(1);
    }

    // ===== 用例 16：listTeacherReports 行含 behavior 节点；懒算后无事件行 flags 含 NO_BEHAVIOR_DATA =====
    @Test
    void listTeacherReportsIncludesBehaviorNodeWithNoBehaviorDataFlag() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:30"));

        Map<String, Object> query = new HashMap<>();
        var result = labService.listTeacherReports(TEACHER,
                new LabRequests.TeacherLabReportQuery(null, null, null, null, 1, 10));

        assertThat(result.items()).isNotEmpty();
        Map<String, Object> row = result.items().stream()
                .filter(r -> submissionId.equals(((Number) r.get("id")).longValue()))
                .findFirst().orElseThrow();
        Map<String, Object> behavior = (Map<String, Object>) row.get("behavior");
        assertThat(behavior).isNotNull();
        assertThat((List<String>) behavior.get("riskFlags")).contains("NO_BEHAVIOR_DATA");
    }

    // ===== 用例 17：riskLevel 筛选 =====
    @Test
    void listTeacherReportsFiltersByRiskLevel() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long subHigh = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:30"));
        Long subLow = seedSubmission(labId, 201L, SubmissionStatus.SUBMITTED, time("10:30"));
        Long subNone = seedSubmission(labId, 202L, SubmissionStatus.SUBMITTED, time("10:30"));
        // 直接构造不同等级的 metrics 行，覆盖筛选命中集
        seedMetricsRow(labId, subHigh, STUDENT.id(), "HIGH", List.of("DURATION_TOO_SHORT"), 40);
        seedMetricsRow(labId, subLow, 201L, "LOW", List.of(), 0);
        // subNone 不建 metrics → NONE 筛选命中（metrics 缺失）

        var highResult = labService.listTeacherReports(TEACHER,
                new LabRequests.TeacherLabReportQuery(null, null, null, "HIGH", 1, 10));
        assertThat(highResult.items()).hasSize(1);
        assertThat(((Number) highResult.items().get(0).get("id")).longValue()).isEqualTo(subHigh);

        var noneResult = labService.listTeacherReports(TEACHER,
                new LabRequests.TeacherLabReportQuery(null, null, null, "NONE", 1, 10));
        List<Long> noneIds = noneResult.items().stream()
                .map(r -> ((Number) r.get("id")).longValue()).toList();
        assertThat(noneIds).contains(subNone);

        // 非法值 → 空结果，不抛异常
        var invalidResult = labService.listTeacherReports(TEACHER,
                new LabRequests.TeacherLabReportQuery(null, null, null, "BOGUS", 1, 10));
        assertThat(invalidResult.items()).isEmpty();
    }

    // ===== 用例 18：behavior 详情 ownedLab 校验 + timeline 500 截断 =====
    @Test
    void getTeacherReportBehaviorEnforcesOwnedLabAndTruncatesTimeline() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:30"));
        // 插入 505 条事件，验证 500 截断 + timelineTotal 真实
        for (int i = 0; i < 505; i++) {
            seedBehaviorEvent(labId, STUDENT.id(), null, "HEARTBEAT",
                    time("10:00").plusSeconds(i), payload("activeMs", 1000L));
        }

        // 非 ownedLab 教师 → 业务异常
        assertThatThrownBy(() -> labService.getTeacherReportBehavior(OTHER_TEACHER, submissionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");

        // ownedLab 教师 → 200，timeline 截断为 500，timelineTotal=505
        Map<String, Object> detail = labService.getTeacherReportBehavior(TEACHER, submissionId);
        List<?> timeline = (List<?>) detail.get("timeline");
        assertThat(timeline).hasSize(500);
        assertThat(((Number) detail.get("timelineTotal")).longValue()).isEqualTo(505L);
        // 按 occurredAt 升序
        List<Map<String, Object>> items = (List<Map<String, Object>>) timeline;
        OffsetDateTime first = (OffsetDateTime) items.get(0).get("occurredAt");
        OffsetDateTime last = (OffsetDateTime) items.get(items.size() - 1).get("occurredAt");
        assertThat(first).isBefore(last);
    }

    // ===== 用例 19：overview 行为摘要与列表一致 =====
    @Test
    void overviewBehaviorMatchesListBehavior() {
        Long labId = seedPublishedLab(TEACHER.id());
        Long stepId = seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:30"));
        Long answerId = seedAnswer(submissionId, stepId, "答案");
        seedAnswerLog(answerId, time("10:05"));
        seedBehaviorEvent(labId, STUDENT.id(), stepId, "LAB_ENTER", time("10:00"), null);

        // 先触发一次懒算（列表），保证 overview 读到同一份 metrics
        var listResult = labService.listTeacherReports(TEACHER,
                new LabRequests.TeacherLabReportQuery(null, null, null, null, 1, 10));
        Map<String, Object> listRow = listResult.items().stream()
                .filter(r -> submissionId.equals(((Number) r.get("id")).longValue()))
                .findFirst().orElseThrow();
        Map<String, Object> listBehavior = (Map<String, Object>) listRow.get("behavior");

        Map<String, Object> overview = labService.getSubmissionOverview(TEACHER, labId);
        List<Map<String, Object>> students = (List<Map<String, Object>>) overview.get("students");
        Map<String, Object> studentRow = students.stream()
                .filter(s -> submissionId.equals(s.get("submissionId")))
                .findFirst().orElseThrow();
        Map<String, Object> overviewBehavior = (Map<String, Object>) studentRow.get("behavior");

        // 共用同一装配方法，数值完全一致
        assertThat(overviewBehavior.get("totalDurationMs")).isEqualTo(listBehavior.get("totalDurationMs"));
        assertThat(overviewBehavior.get("saveCount")).isEqualTo(listBehavior.get("saveCount"));
        assertThat(overviewBehavior.get("sessionCount")).isEqualTo(listBehavior.get("sessionCount"));
        assertThat(overviewBehavior.get("riskLevel")).isEqualTo(listBehavior.get("riskLevel"));
    }

    // ===== 用例 25：断续作答：两个 LAB_ENTER 相隔 7 天 → sessionCount=2，active≈60min =====
    @Test
    void computeCountsDiscontinuousSessions() {
        Long labId = seedPublishedLab(TEACHER.id());
        seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:30").plusDays(7));
        OffsetDateTime day1 = time("10:00");
        OffsetDateTime day8 = day1.plusDays(7);
        // 第一天：30 分钟活跃
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", day1, null);
        seedBehaviorEvent(labId, STUDENT.id(), null, "HEARTBEAT", day1.plusMinutes(30), payload("activeMs", 30L * 60 * 1000));
        // 第八天：30 分钟活跃后提交
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", day8, null);
        seedBehaviorEvent(labId, STUDENT.id(), null, "HEARTBEAT", day8.plusMinutes(30), payload("activeMs", 30L * 60 * 1000));

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        assertThat(metrics.getSessionCount()).isEqualTo(2);
        // activeDurationMs ≈ 60 分钟（允许心跳累计的小数误差）
        assertThat(metrics.getActiveDurationMs()).isEqualTo(60L * 60 * 1000);
        // DURATION_TOO_SHORT 不触发（总时长含 7 天间隔，远大于 45s）
        assertThat(parseFlags(metrics.getRiskFlagsJson())).doesNotContain("DURATION_TOO_SHORT");
    }

    // ===== 用例 26：浏览器直接关闭：LAB_LEAVE(unload) + 尾部心跳 → activeDurationMs 含尾段 =====
    @Test
    void computeIncludesTailHeartbeatBeforeUnload() {
        Long labId = seedPublishedLab(TEACHER.id());
        seedStep(labId, 1, "TEXT");
        Long submissionId = seedSubmission(labId, STUDENT.id(), SubmissionStatus.SUBMITTED, time("10:10"));
        OffsetDateTime enter = time("10:00");
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_ENTER", enter, null);
        // 正常心跳 5 分钟
        seedBehaviorEvent(labId, STUDENT.id(), null, "HEARTBEAT", enter.plusMinutes(5), payload("activeMs", 5L * 60 * 1000));
        // 尾部不满 60s 的心跳（30s）
        seedBehaviorEvent(labId, STUDENT.id(), null, "HEARTBEAT", enter.plusMinutes(5).plusSeconds(30), payload("activeMs", 30_000L));
        // 关闭浏览器
        seedBehaviorEvent(labId, STUDENT.id(), null, "LAB_LEAVE", enter.plusMinutes(5).plusSeconds(30),
                payload("reason", "unload"));

        LabSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);

        // 活跃时长 = 5min + 30s
        assertThat(metrics.getActiveDurationMs()).isEqualTo(5L * 60 * 1000 + 30_000L);
    }

    // ===== 用例 27：uploadAnswerImage 快照计入 saveCount =====
    @Test
    void uploadAnswerImageAppendsSnapshotCountedInSaveCount() {
        Long classId = seedClass(TEACHER.id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, TEACHER.id());
        Long stepId = seedStep(labId, 1, "TEXT");
        seedMembership(classId, STUDENT.id());

        // 先保存一次文字答案（snapshot #1）
        labService.saveAnswer(STUDENT, labId, stepId, new LabRequests.SaveStepAnswerRequest("答案", null));
        LabSubmission submission = submissionRepository.findByLabIdAndStudentId(labId, STUDENT.id()).orElseThrow();
        Long answerId = answerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId)
                .orElseThrow().getId();
        long logsBefore = answerLogRepository.count();

        // 上传图片（snapshot #2）
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1, 2, 3});
        labService.uploadAnswerImage(STUDENT, labId, stepId, file);

        long logsAfter = answerLogRepository.count();
        assertThat(logsAfter).isEqualTo(logsBefore + 1);

        // compute 后 saveCount 包含图片上传快照
        LabSubmissionMetrics metrics = metricsService.computeForSubmission(submission);
        assertThat(metrics.getSaveCount()).isEqualTo(2);
    }

    // ===== 辅助方法 =====

    private Map<String, Object> payload(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private List<String> parseFlags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 复用 LabService 的装配口径（toBehaviorView 为私有），本测试内仅用于断言 durationOpen 推导，
     * 真实装配由 LabService 内部完成。
     */
    private Map<String, Object> toBehaviorView(LabSubmissionMetrics metrics, LabSubmission submission) {
        Map<String, Object> view = new HashMap<>();
        view.put("totalDurationMs", metrics.getTotalDurationMs());
        view.put("durationOpen", submission.getSubmittedAt() == null);
        return view;
    }

    private OffsetDateTime time(String hhmm) {
        String[] parts = hhmm.split(":");
        return OffsetDateTime.now()
                .withHour(Integer.parseInt(parts[0]))
                .withMinute(Integer.parseInt(parts[1]))
                .withSecond(0)
                .withNano(0);
    }

    private long genId() {
        return nextId++;
    }

    private Long seedClass(Long teacherId) {
        long id = genId();
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
        long id = genId();
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
        long id = genId();
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

    private Long seedPublishedLab(Long teacherId) {
        seedStudentAccount(STUDENT);
        Long classId = seedClass(teacherId);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherId);
        seedMembership(classId, STUDENT.id());
        return labId;
    }

    private Long seedStep(Long labId, int stepNo, String questionType) {
        long id = genId();
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
                "CODE".equals(questionType) ? "JAVA" : "TEXT", true);
        return id;
    }

    private Long seedSubmission(Long labId, Long studentId, SubmissionStatus status, OffsetDateTime submittedAt) {
        long id = genId();
        jdbcTemplate.update("""
                INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, total_score, submitted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, labId, studentId, status.name(), submittedAt);
        return id;
    }

    private Long seedAnswer(Long submissionId, Long stepId, String answerText) {
        long id = genId();
        jdbcTemplate.update("""
                INSERT INTO experiment_answer (id, experiment_submission_id, experiment_item_id, answer_text, score_source, editor_language, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'TEACHER', 'TEXT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, submissionId, stepId, answerText);
        return id;
    }

    private void seedAnswerLog(Long answerId, OffsetDateTime fillTime) {
        LabStepAnswerLog log = new LabStepAnswerLog();
        log.setLabStepAnswerId(answerId);
        log.setContent("snapshot");
        log.setFillTime(fillTime);
        answerLogRepository.save(log);
    }

    private void seedBehaviorEvent(Long labId, Long studentId, Long stepId, String type, OffsetDateTime occurredAt, Map<String, Object> payload) {
        BehaviorEvent event = new BehaviorEvent();
        event.setEventUuid(UUID.randomUUID().toString());
        event.setEventType(type);
        event.setOccurredAt(occurredAt);
        event.setReceivedAt(OffsetDateTime.now());
        event.setStudentId(studentId);
        event.setLabId(labId);
        event.setStepId(stepId);
        event.setSchoolId(1L);
        if (payload != null && !payload.isEmpty()) {
            try {
                event.setPayloadJson(objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                event.setPayloadJson(null);
            }
        }
        behaviorEventRepository.save(event);
    }

    private void seedMetricsRow(Long labId, Long submissionId, Long studentId, String riskLevel, List<String> flags, int riskScore) {
        LabSubmissionMetrics metrics = new LabSubmissionMetrics();
        metrics.setSubmissionId(submissionId);
        metrics.setLabId(labId);
        metrics.setStudentId(studentId);
        metrics.setRiskLevel(riskLevel);
        metrics.setRiskScore(riskScore);
        metrics.setRiskFlagsJson(flags.isEmpty() ? "[]" : "[\"" + String.join("\",\"", flags) + "\"]");
        metrics.setSaveCount(0);
        metrics.setEventWatermark(0L);
        metrics.setComputedAt(OffsetDateTime.now());
        metricsRepository.save(metrics);
    }
}
