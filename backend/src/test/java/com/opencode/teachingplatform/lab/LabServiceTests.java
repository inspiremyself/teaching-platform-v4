package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.common.api.PagedResult;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.lab.behavior.service.LabBehaviorMetricsService;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.entity.Lab;
import com.opencode.teachingplatform.lab.entity.LabStep;
import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import com.opencode.teachingplatform.lab.entity.LabStepAnswerLog;
import com.opencode.teachingplatform.lab.entity.LabSubmission;
import com.opencode.teachingplatform.lab.entity.ExperimentBlankAnswerOverride;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerLogRepository;
import com.opencode.teachingplatform.lab.repository.ExperimentBlankAnswerOverrideRepository;
import com.opencode.teachingplatform.lab.repository.LabRepository;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerRepository;
import com.opencode.teachingplatform.lab.repository.LabStepRepository;
import com.opencode.teachingplatform.lab.repository.LabSubmissionRepository;
import com.opencode.teachingplatform.lab.service.LabScoringSupport;
import com.opencode.teachingplatform.lab.service.LabService;
import com.opencode.teachingplatform.lab.service.LabSubmissionClaimService;
import com.opencode.teachingplatform.grading.strategy.FillBlankScoringStrategy;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.strategy.MultipleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SingleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SubjectiveRecommendationStrategy;
import com.opencode.teachingplatform.grading.strategy.TrueFalseScoringStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({LabService.class, LabScoringSupport.class,
        com.opencode.teachingplatform.lab.service.LabConfigService.class,
        com.opencode.teachingplatform.lab.service.LabStepService.class,
        com.opencode.teachingplatform.lab.service.LabAnswerService.class,
        com.opencode.teachingplatform.lab.service.LabReportService.class,
        com.opencode.teachingplatform.lab.service.LabServiceSupport.class,
        com.opencode.teachingplatform.clazz.service.ClassAccessService.class,
        com.opencode.teachingplatform.grading.service.ScoreRecordWriter.class,
        com.opencode.teachingplatform.plagiarism.service.LocalPlagiarismService.class,
        com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter.class,
        LabServiceTests.TestBeans.class})
@ActiveProfiles("test")
class LabServiceTests {

    @TempDir
    static Path storageRoot;

    private static final long JS_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;
    private static final long SEEDED_PLAYWRIGHT_LAB_ID = 1003L;
    private static final long SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID = 2005L;
    private static int generatedExperimentId = 6000;
    private static int generatedReportStudentId = 80000;
    private static int generatedReportSubmissionId = 90000;

    @Autowired
    private LabService labService;

    /**
     * 行为指标服务在本类中走 mock（方案用例 20）：
     * 懒算 refreshStaleMetrics 是 REQUIRES_NEW 写库，@DataJpaTest 事务内种子对独立连接不可见，
     * 真实执行会触发 metrics 表 FK 违反；真实聚合与懒算由 LabBehaviorMetricsServiceTests 覆盖。
     */
    @MockBean
    private LabBehaviorMetricsService labBehaviorMetricsService;

    /**
     * 首存 claim 走 REQUIRES_NEW 独立连接，在 @DataJpaTest 事务内看不到未提交的实验种子数据，
     * 会触发 experiment_submission 的外键冲突；此处用委托桩替代，语义与真实实现一致（无则建 SAVED 行）。
     */
    @MockBean
    private LabSubmissionClaimService labSubmissionClaimService;

    /**
     * 查重服务默认走真实算法（spy 委托真实实现），仅降级用例通过 doThrow 注入异常；
     * Spring Boot 会在每个用例结束后自动重置 spy 桩，不影响其他用例。
     */
    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.opencode.teachingplatform.plagiarism.service.LocalPlagiarismService localPlagiarismService;

    @org.junit.jupiter.api.BeforeEach
    void stubBehaviorMetricsService() {
        // 懒算默认返回空集（全部行视为无 metrics → behavior 为 null，不影响既有断言）；verify 场景单独覆盖
        org.mockito.Mockito.lenient()
                .when(labBehaviorMetricsService.refreshStaleMetrics(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        // 委托桩：找到即复用，找不到补 SAVED 行（与真实 claim 一致，只是不另开事务）
        org.mockito.Mockito.lenient()
                .doAnswer(invocation -> {
                    Long labId = invocation.getArgument(0);
                    Long studentId = invocation.getArgument(1);
                    if (labSubmissionRepository.findByLabIdAndStudentId(labId, studentId).isEmpty()) {
                        LabSubmission created = new LabSubmission();
                        created.setLabId(labId);
                        created.setStudentId(studentId);
                        created.setSubmitStatus(SubmissionStatus.SAVED);
                        created.setPlagiarismRate(0D);
                        created.setTotalScore(0D);
                        labSubmissionRepository.saveAndFlush(created);
                    }
                    return null;
                })
                .when(labSubmissionClaimService).claim(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Autowired
    private LabRepository labRepository;

    @Autowired
    private com.opencode.teachingplatform.lab.service.LabServiceSupport labServiceSupport;

    @Autowired
    private LabStepRepository labStepRepository;

    @Autowired
    private LabSubmissionRepository labSubmissionRepository;

    @Autowired
    private LabStepAnswerRepository labStepAnswerRepository;

    @Autowired
    private LabStepAnswerLogRepository labStepAnswerLogRepository;

    @Autowired
    private ExperimentBlankAnswerOverrideRepository experimentBlankAnswerOverrideRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private ClassMemberRepository classMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScoreRecordRepository scoreRecordRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage-root", () -> storageRoot.toString());
    }

    @Test
    void uploadAnswerImageStoresFileAndReturnsMetadata() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "shot1.png",
                "image/png",
                new byte[] {1, 2, 3, 4}
        );

        Map<String, Object> result = labService.uploadAnswerImage(studentUser(), labId, stepId, file);

        assertThat(result.get("path")).asString().matches("lab-answers/\\d{4}-\\d{2}-\\d{2}/\\d+/.+");
        assertThat(result.get("name")).isEqualTo("shot1.png");
        assertThat(result.get("contentType")).isEqualTo("image/png");
        assertThat(result.get("size")).isEqualTo(4L);
    }

    @Test
    void uploadAnswerImageRejectsOversizedFile() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        byte[] oversized = new byte[1_048_577];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", oversized);

        assertThatThrownBy(() -> labService.uploadAnswerImage(studentUser(), labId, stepId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片过大");
    }

    @Test
    void uploadAnswerImageRejectsWhenAnswerAlreadyHasFiveImages() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        for (int index = 0; index < 5; index++) {
            MockMultipartFile file = new MockMultipartFile("file", "shot" + index + ".png", "image/png", new byte[] {(byte) index});
            labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        }

        MockMultipartFile sixthFile = new MockMultipartFile("file", "shot6.png", "image/png", new byte[] {6});
        assertThatThrownBy(() -> labService.uploadAnswerImage(studentUser(), labId, stepId, sixthFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单题最多上传 5 张图片");
    }

    @Test
    void saveAnswerRejectsMismatchedImageSize() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));

        assertThatThrownBy(() -> labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "",
                        "{\"kind\":\"text\",\"text\":\"\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":999}]}"
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void saveAnswerRejectsDisallowedImageContentType() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));

        assertThatThrownBy(() -> labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "",
                        "{\"kind\":\"text\",\"text\":\"\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.gif\",\"contentType\":\"image/gif\",\"size\":3}]}"
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 png、jpeg、webp 图片");
    }

    @Test
    void previewAnswerImageRejectsTeacherWhoDidNotCreateLab() {
        Long ownerTeacherId = teacherUser().id();
        Long coTeacherId = ownerTeacherId + 100L;
        CurrentUser coTeacher = new CurrentUser(coTeacherId, "co-teacher", "共班教师", UserRole.TEACHER);
        seedStudentAccount(coTeacher);

        Long classId = seedClass(ownerTeacherId);
        jdbcTemplate.update(
                """
                INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at)
                SELECT 1, '共班学科', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM school_subject WHERE school_id = 1 AND code = ?)
                """,
                "CO-TEACH-" + classId,
                "CO-TEACH-" + classId
        );
        jdbcTemplate.update(
                """
                INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_id, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', (SELECT id FROM school_subject WHERE school_id = 1 AND code = ?), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                classId,
                coTeacherId,
                "CO-TEACH-" + classId
        );
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, ownerTeacherId);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "",
                        "{\"kind\":\"text\",\"text\":\"\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );

        assertThatThrownBy(() -> labService.previewAnswerImage(coTeacher, path))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");
    }

    @Test
    void saveAnswerCanonicalizesTextPayloadAndPersistsImages() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {9, 8, 7});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));

        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "最终文字",
                        "{\"kind\":\"text\",\"text\":\"旧文字\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        assertThat(answer.getAnswerText()).isEqualTo("最终文字");
        assertThat(answer.getAnswerJson()).contains("\"text\":\"最终文字\"");
        assertThat(answer.getAnswerJson()).contains(path);
        assertThat(answer.getAnswerFilePath()).isNull();
    }

    @Test
    void saveAnswerRejectsForgedImagePath() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        assertThatThrownBy(() -> labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "说明",
                        "{\"kind\":\"text\",\"text\":\"说明\",\"images\":[{\"path\":\"lab-answers/2026-09-04/missing.png\",\"name\":\"missing.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void saveAnswerRejectsImagesOnNonTextQuestionType() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", 10);
        seedMembership(classId, studentUser().id());

        assertThatThrownBy(() -> labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "A",
                        "{\"kind\":\"text\",\"text\":\"A\",\"images\":[{\"path\":\"lab-answers/2026-09-04/a.png\",\"name\":\"a.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持图片");
    }

    @Test
    void saveAnswerAcceptsEmptyAnswerTextAndClearsAnswer() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());
        labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest("先写点内容", "{\"kind\":\"text\",\"text\":\"先写点内容\"}"));

        // 清空：answerText 为空且 payload 无图片，作为清空动作正常落盘
        assertThatCode(() -> labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest("", "{\"kind\":\"text\",\"text\":\"\"}")))
                .doesNotThrowAnyException();

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
        assertThat(items.getFirst().get("answerText")).isEqualTo("");
    }

    @Test
    void saveAnswerAcceptsImageOnlyAnswerWithoutText() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));

        assertThatCode(() -> labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "",
                        "{\"kind\":\"text\",\"text\":\"\",\"images\":[{\"path\":\"" + path
                                + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}")))
                .doesNotThrowAnyException();

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository
                .findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        assertThat(answer.getAnswerText()).isEqualTo("");
        assertThat(answer.getAnswerJson()).contains(path);
    }

    @Test
    void previewAnswerImageAllowsOwnerStudentAndClassTeacher() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "",
                        "{\"kind\":\"text\",\"text\":\"\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );

        LabService.PreviewAnswerImageResult studentPreview = labService.previewAnswerImage(studentUser(), path);
        assertThat(studentPreview.bytes()).hasSize(3);

        LabService.PreviewAnswerImageResult teacherPreview = labService.previewAnswerImage(teacherUser(), path);
        assertThat(teacherPreview.bytes()).hasSize(3);
    }

    @Test
    void previewAnswerImageRejectsUnauthorizedUser() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "",
                        "{\"kind\":\"text\",\"text\":\"\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );

        CurrentUser otherStudent = new CurrentUser(9999L, "other", "其他学生", UserRole.STUDENT);
        seedStudentAccount(otherStudent);
        seedMembership(classId, otherStudent.id());

        assertThatThrownBy(() -> labService.previewAnswerImage(otherStudent, path))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");
    }

    @Test
    void submissionOverviewIncludesAllClassMembersAndAnswerCells() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedStep(labId, 2, "TEXT", "{}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        Long otherStudentId = 201L;
        CurrentUser otherStudent = new CurrentUser(otherStudentId, "student-201", "未交学生", UserRole.STUDENT);
        seedStudentAccount(otherStudent);
        seedMembership(classId, otherStudentId);

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "文字说明",
                        "{\"kind\":\"text\",\"text\":\"文字说明\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );

        Map<String, Object> overview = labService.getSubmissionOverview(teacherUser(), labId);

        assertThat(overview.get("labId")).isEqualTo(labId);
        assertThat(overview.get("items")).asList().hasSize(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) overview.get("students");
        assertThat(students).hasSize(2);

        Map<String, Object> answeredStudent = students.stream()
                .filter(row -> java.util.Objects.equals(row.get("studentId"), studentUser().id()))
                .findFirst()
                .orElseThrow();
        assertThat(answeredStudent.get("submissionId")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answeredCells = (List<Map<String, Object>>) answeredStudent.get("cells");
        assertThat(answeredCells.get(0).get("answered")).isEqualTo(true);
        assertThat(answeredCells.get(0).get("hasText")).isEqualTo(true);
        assertThat(answeredCells.get(0).get("imageCount")).isEqualTo(1);
        assertThat(answeredCells.get(1).get("answered")).isEqualTo(false);

        Map<String, Object> absentStudent = students.stream()
                .filter(row -> java.util.Objects.equals(row.get("studentId"), otherStudentId))
                .findFirst()
                .orElseThrow();
        assertThat(absentStudent.get("submissionId")).isNull();
        assertThat(absentStudent.get("submitStatus")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> absentCells = (List<Map<String, Object>>) absentStudent.get("cells");
        assertThat(absentCells).allSatisfy(cell -> {
            assertThat(cell.get("answered")).isEqualTo(false);
            assertThat(cell.get("imageCount")).isEqualTo(0);
        });
    }

    @Test
    void submissionOverviewWorksWhenNoStudentHasSubmitted() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedStep(labId, 1, "TEXT", "{}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        Map<String, Object> overview = labService.getSubmissionOverview(teacherUser(), labId);

        assertThat(overview.get("labId")).isEqualTo(labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) overview.get("students");
        assertThat(students).hasSize(1);
        assertThat(students.get(0).get("submissionId")).isNull();
        assertThat(students.get(0).get("submitStatus")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cells = (List<Map<String, Object>>) students.get(0).get("cells");
        assertThat(cells).allSatisfy(cell -> assertThat(cell.get("answered")).isEqualTo(false));
    }

    @Test
    void listStudentLabSubmissionsReturnsTeacherVisibleRecords() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());
        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("答案", null));

        List<Map<String, Object>> submissions = labService.listStudentLabSubmissions(teacherUser(), studentUser().id());

        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).get("labId")).isEqualTo(labId);
        assertThat(submissions.get(0).get("submissionId")).isNotNull();
    }

    @Test
    void listStudentLabSubmissionsRejectsUnauthorizedTeacher() {
        Long classId = seedClass(teacherUser().id());
        seedMembership(classId, studentUser().id());
        CurrentUser outsider = new CurrentUser(888L, "outsider", "外部教师", UserRole.TEACHER);
        seedStudentAccount(outsider);

        assertThatThrownBy(() -> labService.listStudentLabSubmissions(outsider, studentUser().id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");
    }

    @Test
    void getLabItemAnswersReturnsClassAnswersWithImages() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "截图题",
                        "{\"kind\":\"text\",\"text\":\"截图题\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );

        Map<String, Object> wall = labService.getLabItemAnswers(teacherUser(), labId, stepId);

        assertThat(wall.get("itemId")).isEqualTo(stepId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answers = (List<Map<String, Object>>) wall.get("answers");
        assertThat(answers).hasSize(1);
        assertThat(answers.get(0).get("answerText")).isEqualTo("截图题");
        assertThat(answers.get(0).get("images")).asList().hasSize(1);
    }

    @Test
    void experimentTablePreservesLegacyColumnsAndOnlyAddsNewColumns() throws SQLException {
        try (var connection = jdbcTemplate.getDataSource().getConnection();
             var resultSet = connection.getMetaData().getColumns(null, null, "T_EXPERIMENT", null)) {
            Map<String, String> columns = new java.util.LinkedHashMap<>();
            while (resultSet.next()) {
                columns.put(resultSet.getString("COLUMN_NAME"), resultSet.getString("TYPE_NAME"));
            }

            assertThat(columns)
                    .containsKeys(
                            "EXPERIMENT_ID",
                            "EXPERIMENT_NO",
                            "EXPERIMENT_NAME",
                            "EXPERIMENT_TYPE",
                            "INSTRUCTION_TYPE",
                            "EXPERIMENT_REQUIREMENT",
                            "EXPERIMENT_CONTENT",
                            "STATE"
                    )
                    .doesNotContainKey("ID");

            assertThat(columns.get("EXPERIMENT_NO")).containsIgnoringCase("INTEGER");
            assertThat(columns.get("INSTRUCTION_TYPE")).containsIgnoringCase("CHARACTER");
        }
    }

    @Test
    void saveAnswerCreatesSubmissionInSavedStatus() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());

        Map<String, Object> result = labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("步骤答案", null));

        assertThat(result.get("submissionStatus")).isEqualTo("SAVED");
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId)).isPresent();
    }

    @Test
    void saveAnswerPersistsStructuredPayloadJsonWhilePreservingAnswerText() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", 10);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest("A", "{\"kind\":\"single\",\"selectedKey\":\"A\"}")
        );

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();

        assertThat(answer.getAnswerText()).isEqualTo("A");
        assertThat(answer.getAnswerJson()).isEqualTo("{\"kind\":\"single\",\"selectedKey\":\"A\"}");
    }

    @Test
    void saveAnswerWritesSnapshotLogRecord() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("第一次快照", null));

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT lab_step_answer_id, content, fill_time
                FROM lab_step_answer_log
                WHERE lab_step_answer_id = ?
                """,
                answer.getId()
        );

        assertThat(row)
                .containsEntry("LAB_STEP_ANSWER_ID", answer.getId())
                .containsEntry("CONTENT", "第一次快照");
        assertThat(row.get("FILL_TIME")).isNotNull();
    }

    @Test
    void saveAnswerAppendsMultipleSnapshotLogsForSameStepAnswer() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("第一次快照", null));
        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("第二次快照", null));

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_step_answer_log WHERE lab_step_answer_id = ?",
                Integer.class,
                answer.getId()
        );
        String latestContent = jdbcTemplate.queryForObject(
                "SELECT content FROM lab_step_answer_log WHERE lab_step_answer_id = ? ORDER BY fill_time DESC, id DESC LIMIT 1",
                String.class,
                answer.getId()
        );

        assertThat(logCount).isEqualTo(2);
        assertThat(latestContent).isEqualTo("第二次快照");
    }

    @Test
    void studentLabDetailFallsBackToLatestSnapshotWhenCurrentAnswerIsEmpty() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("可恢复答案", null));

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        answer.setAnswerText(null);
        labStepAnswerRepository.saveAndFlush(answer);

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");

        assertThat(items.getFirst().get("answerText")).isEqualTo("可恢复答案");
    }

    @Test
    void studentLabDetailPrefersCurrentAnswerOverSnapshotLog() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("第一次快照", null));

        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        answer.setAnswerText("当前答案");
        labStepAnswerRepository.saveAndFlush(answer);

        LabStepAnswerLog oldLog = new LabStepAnswerLog();
        oldLog.setLabStepAnswerId(answer.getId());
        oldLog.setContent("旧快照");
        oldLog.setFillTime(java.time.OffsetDateTime.now().minusMinutes(1));
        labStepAnswerLogRepository.saveAndFlush(oldLog);

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");

        assertThat(items.getFirst().get("answerText")).isEqualTo("当前答案");
    }

    @Test
    void studentLabDetailProjectsChoiceMetadataAndSavedAnswerPayload() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(
                labId,
                1,
                "SINGLE_CHOICE",
                """
                {"options":[{"key":"A","label":"分层架构"},{"key":"B","label":"单体架构"}]}
                """,
                "{}",
                10,
                null,
                """
                {"questionType":"SINGLE_CHOICE","options":[{"key":"A","label":"分层架构"},{"key":"B","label":"单体架构"}]}
                """
        );
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest("A", "{\"kind\":\"single\",\"selectedKey\":\"A\"}")
        );

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) items.getFirst().get("options");

        assertThat(items.getFirst().get("answerText")).isEqualTo("A");
        assertThat(items.getFirst().get("answerPayloadJson")).isEqualTo("{\"kind\":\"single\",\"selectedKey\":\"A\"}");
        assertThat(options)
                .extracting(option -> option.get("key"), option -> option.get("label"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A", "分层架构"),
                        org.assertj.core.groups.Tuple.tuple("B", "单体架构")
                );
    }

    @Test
    void studentLabDetailProjectsFillBlankMetadataAndKeepsLegacyAnswerTextWhenAnswerJsonMissing() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(
                labId,
                1,
                "FILL_BLANK",
                """
                {"blanks":[{"index":0,"token":"Blank 1"},{"index":1,"token":"Blank 2"}]}
                """,
                "{}",
                10,
                null,
                """
                {"questionType":"FILL_BLANK","blanks":[{"index":0,"token":"Blank 1"},{"index":1,"token":"Blank 2"}]}
                """
        );
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("spring,boot", null));

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blanks = (List<Map<String, Object>>) items.getFirst().get("blanks");

        assertThat(items.getFirst().get("answerText")).isEqualTo("spring,boot");
        assertThat(items.getFirst().get("answerPayloadJson")).isNull();
        assertThat(blanks)
                .extracting(blank -> blank.get("index"), blank -> blank.get("token"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "Blank 1"),
                        org.assertj.core.groups.Tuple.tuple(1, "Blank 2")
                );
    }

    @Test
    void studentLabDetailProjectsCodeEditorLanguage() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(
                labId,
                1,
                "CODE",
                "{}",
                "{}",
                20,
                null,
                "{\"questionType\":\"CODE\"}"
        );
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("System.out.println(1);", "{\"kind\":\"code\",\"code\":\"System.out.println(1);\"}"));

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");

        assertThat(items.getFirst().get("editorLanguage")).isEqualTo("JAVA");
        assertThat(items.getFirst().get("answerPayloadJson")).isEqualTo("{\"kind\":\"code\",\"code\":\"System.out.println(1);\"}");
    }

    @Test
    void studentOnlySeesPublishedLabsForOwnClasses() {
        Long classId = seedClass(1L);
        Long otherClassId = seedClass(1L);
        Long publishedLabId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        seedMembership(classId, studentUser().id());
        seedLab(classId, ActivityStatus.DRAFT, 1L);
        seedLab(otherClassId, ActivityStatus.PUBLISHED, 1L);

        List<Map<String, Object>> labs = labService.listStudentLabs(studentUser());

        assertThat(labs).hasSize(1);
        assertThat(labs.getFirst().get("id")).isEqualTo(publishedLabId);
        assertThat(labs.getFirst().get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    void submittingClosedLabIsRejected() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.CLOSED, 1L);
        seedMembership(classId, studentUser().id());

        assertThatThrownBy(() -> labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("实验小结")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已关闭");
    }

    @Test
    void submitLabStoresSummaryTextOnSubmission() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 10);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("本次实验总结"))
                .get("submissionId")).longValue();

        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(submission.getSummaryText()).isEqualTo("本次实验总结");
        // 用例 21：提交成功后同步触发一次行为指标全量计算（D8 时机①），不断言指标数值
        verify(labBehaviorMetricsService, times(1)).computeForSubmission(any(LabSubmission.class));
    }

    @Test
    void submitLabRejectsBlankSummaryWhenLabRequiresSummary() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 10);
        seedMembership(classId, studentUser().id());
        jdbcTemplate.update("UPDATE t_experiment SET summary_required = ? WHERE experiment_id = ?", true, labId);

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));

        assertThatThrownBy(() -> labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验小结");
    }

    @Test
    void submitLabMarksSavedSubmissionAsSubmitted() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        // 主观题只产生建议分（score 为空），提交后应停留在 SUBMITTED 而不是直接 GRADED
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("实验作答", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("实验小结"))
                .get("submissionId")).longValue();

        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getSubmittedAt()).isNotNull();
        assertThat(submission.getTotalScore()).isNotNull();
        verify(labBehaviorMetricsService, times(1)).computeForSubmission(any(LabSubmission.class));
    }

    @Test
    void submitLabRejectsRepeatedSubmitWhenAlreadySubmitted() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        // 主观题保持 SUBMITTED，才能命中“重复提交”分支（客观题会直接进入 GRADED）
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("实验作答", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("首次提交"))
                .get("submissionId")).longValue();
        LabSubmission afterFirstSubmit = labSubmissionRepository.findById(submissionId).orElseThrow();
        var submittedAt = afterFirstSubmit.getSubmittedAt();
        Double totalScore = afterFirstSubmit.getTotalScore();
        String answerBefore = labStepAnswerRepository
                .findByLabSubmissionIdAndLabStepId(submissionId, stepId)
                .orElseThrow()
                .getAnswerText();

        assertThatThrownBy(() -> labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("重复提交")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已提交，不能重复提交");

        LabSubmission unchanged = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(unchanged.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(unchanged.getSummaryText()).isEqualTo("首次提交");
        assertThat(unchanged.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(unchanged.getTotalScore()).isEqualTo(totalScore);
        // 拒绝路径不得重写答案
        assertThat(labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId)
                .orElseThrow()
                .getAnswerText()).isEqualTo(answerBefore);
    }

    @Test
    void submitLabRejectsSubmitWhenAlreadyGraded() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 10);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("提交"))
                .get("submissionId")).longValue();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setTotalScore(8.0);
        submission.setTeacherComment("教师评语");
        submission.setGradedAt(java.time.OffsetDateTime.now());
        labSubmissionRepository.save(submission);
        int scoreRecordCount = countLabScoreRecords(labId);

        assertThatThrownBy(() -> labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("批改后重交")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已批改，如需修改请联系教师打回");

        LabSubmission unchanged = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(unchanged.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(unchanged.getTotalScore()).isEqualTo(8.0);
        assertThat(unchanged.getTeacherComment()).isEqualTo("教师评语");
        assertThat(countLabScoreRecords(labId)).isEqualTo(scoreRecordCount);
    }

    @Test
    void submitLabWithoutSavedSubmissionAsksToSaveFirst() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 10);
        seedMembership(classId, studentUser().id());

        assertThatThrownBy(() -> labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("实验小结")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先保存实验答案");
    }

    /**
     * 统计指定实验的成绩记录数，用于断言拒绝路径不写成绩表。
     */
    private int countLabScoreRecords(Long labId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'LAB' AND business_id = ?",
                Integer.class,
                labId
        );
        return count != null ? count : 0;
    }

    @Test
    void teacherCanUpdateOwnedLab() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.DRAFT, 1L);
        seedStep(labId, 1);

        Map<String, Object> result = labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("更新后的实验", "新的说明", "新的实验内容", 2, classId, "PUBLISHED", null, null, true, null, null)
        );

        assertThat(result.get("title")).isEqualTo("更新后的实验");
        assertThat(result.get("status")).isEqualTo("PUBLISHED");
        assertThat(result.get("summaryRequired")).isEqualTo(true);
        Lab updated = labRepository.findById(labId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("更新后的实验");
        assertThat(updated.getDescription()).isEqualTo("新的说明");
        assertThat(updated.getExperimentContent()).isEqualTo("新的实验内容");
        assertThat(updated.getExperimentType()).isEqualTo(2);
        assertThat(updated.getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
        assertThat(updated.isSummaryRequired()).isTrue();
    }

    @Test
    void teacherCanChangeOwnedLabStatus() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.DRAFT, 1L);

        Map<String, Object> result = labService.changeLabStatus(
                teacherUser(),
                labId,
                new LabRequests.ChangeLabStatusRequest("CLOSED")
        );

        assertThat(result.get("status")).isEqualTo("CLOSED");
        assertThat(labRepository.findById(labId).orElseThrow().getStatus()).isEqualTo(ActivityStatus.CLOSED);
    }

    @Test
    void changeLabStatusToPublishedWithoutItemsShouldFail() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.DRAFT, 1L);

        assertThatThrownBy(() -> labService.changeLabStatus(
                teacherUser(),
                labId,
                new LabRequests.ChangeLabStatusRequest("PUBLISHED")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未配置题项");
    }

    @Test
    void changeLabStatusToPublishedWithItemsShouldSucceed() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.DRAFT, 1L);
        seedStep(labId, 1);

        Map<String, Object> result = labService.changeLabStatus(
                teacherUser(),
                labId,
                new LabRequests.ChangeLabStatusRequest("PUBLISHED")
        );

        assertThat(result.get("status")).isEqualTo("PUBLISHED");
        assertThat(labRepository.findById(labId).orElseThrow().getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    void createLabAsPublishedWithoutItemsShouldFail() {
        Long classId = seedClass(teacherUser().id());

        assertThatThrownBy(() -> labService.createLab(
                teacherUser(),
                new LabRequests.CreateLabRequest("直接发布实验", "说明", "内容", 1, classId, "PUBLISHED", null, null, false, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未配置题项");

        assertThat(labRepository.findAll()).noneMatch(lab -> "直接发布实验".equals(lab.getTitle()));
    }

    @Test
    void updateLabToPublishedWithoutItemsShouldFail() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.DRAFT, 1L);

        assertThatThrownBy(() -> labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("更新实验", "说明", "内容", 1, classId, "PUBLISHED", null, null, false, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未配置题项");

        assertThat(labRepository.findById(labId).orElseThrow().getStatus()).isEqualTo(ActivityStatus.DRAFT);
    }

    @Test
    void closeOrDraftLabWithoutItemsShouldSucceed() {
        Long classId = seedClass(1L);
        Long draftLabId = seedLab(classId, ActivityStatus.DRAFT, 1L);
        Long closedLabId = seedLab(classId, ActivityStatus.CLOSED, 1L);

        Map<String, Object> closedResult = labService.changeLabStatus(
                teacherUser(),
                draftLabId,
                new LabRequests.ChangeLabStatusRequest("CLOSED")
        );
        Map<String, Object> draftResult = labService.changeLabStatus(
                teacherUser(),
                closedLabId,
                new LabRequests.ChangeLabStatusRequest("DRAFT")
        );

        assertThat(closedResult.get("status")).isEqualTo("CLOSED");
        assertThat(draftResult.get("status")).isEqualTo("DRAFT");
    }

    @Test
    void generatedLabAndLabStepIdsStayWithinJavaScriptSafeIntegerRange() {
        Long classId = seedClass(teacherUser().id());

        Lab lab = new Lab();
        lab.setTitle("JS 安全整数实验");
        lab.setDescription("验证实验 id 范围");
        lab.setClassId(classId);
        lab.setCreatedBy(teacherUser().id());
        lab.setStatus(ActivityStatus.DRAFT);
        lab = labRepository.saveAndFlush(lab);

        LabStep step = new LabStep();
        step.setLabId(lab.getId());
        step.setStepNo(1);
        step.setTitle("JS 安全整数题项");
        step.setQuestionType("FILL_BLANK");
        step.setContent("填写答案");
        step.setAnswerConfigJson("{\"blanks\":[{\"answers\":[\"A\"],\"score\":10}]}");
        step.setStepScore(10);
        step = labStepRepository.saveAndFlush(step);

        assertThat(lab.getId()).isPositive().isLessThanOrEqualTo(JS_SAFE_INTEGER_MAX);
        assertThat(step.getId()).isPositive().isLessThanOrEqualTo(JS_SAFE_INTEGER_MAX);
    }

    @Test
    void teacherCannotMoveLabToUnownedClass() {
        Long ownedClassId = seedClass(1L);
        Long otherTeacherClassId = seedClass(9L);
        Long labId = seedLab(ownedClassId, ActivityStatus.DRAFT, 1L);

        assertThatThrownBy(() -> labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("越权更新", "desc", "content", 1, otherTeacherClassId, "DRAFT", null, null, false, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该资源");
    }

    // ------------------------------------------------------------------
    // 实验题项结构变更、状态回退、所属班级变更的守卫（TD8–TD12）
    // ------------------------------------------------------------------

    @Test
    void draftLabWithoutSubmissionsAllowsStepCreateUpdateDelete() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        Long stepId = seedStep(labId, 1);

        Map<String, Object> created = labService.createStep(teacherUser(), labId, new LabRequests.CreateStepRequest(
                2, "新增步骤", "TEXT", "新增内容", "{}", 10, true));
        Map<String, Object> updated = labService.updateStep(teacherUser(), stepId, new LabRequests.UpdateStepRequest(
                1, "更新后的步骤", "TEXT", "更新内容", "{}", 20, false));
        Map<String, Object> deleted = labService.deleteStep(teacherUser(), stepId);

        // 草稿 + 零提交：三类结构变更都必须放行
        assertThat(created.get("stepNo")).isEqualTo(2);
        assertThat(updated.get("title")).isEqualTo("更新后的步骤");
        assertThat(deleted.get("deleted")).isEqualTo(true);
        assertThat(labSubmissionRepository.countByLabId(labId)).isZero();
        assertThat(labStepRepository.findById(stepId)).isEmpty();
        assertThat(labStepRepository.findByLabIdOrderByStepNoAsc(labId)).hasSize(1);
    }

    @Test
    void publishedLabRejectsStepCreateUpdateDelete() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);

        // 已发布实验：题项增删改一律拒绝，且拒绝路径不落库
        assertThatThrownBy(() -> labService.createStep(teacherUser(), labId, new LabRequests.CreateStepRequest(
                2, "发布后新增", "TEXT", "内容", "{}", 10, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已发布或已关闭，不能修改题项");
        assertThatThrownBy(() -> labService.updateStep(teacherUser(), stepId, new LabRequests.UpdateStepRequest(
                1, "发布后修改", "TEXT", "内容", "{}", 10, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已发布或已关闭，不能修改题项");
        assertThatThrownBy(() -> labService.deleteStep(teacherUser(), stepId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已发布或已关闭，不能修改题项");

        assertThat(labStepRepository.findByLabIdOrderByStepNoAsc(labId)).hasSize(1);
        assertThat(labStepRepository.findById(stepId).orElseThrow().getTitle()).isEqualTo("步骤1");
    }

    @Test
    void draftLabWithStudentSubmissionRejectsStepCreateUpdateDelete() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());
        // 复用既有 helper：发布 → 学生保存并提交 → 直接改实体回草稿，
        // 模拟修复前遗留的「草稿态但已有学生提交」数据，用于校验结构变更的第二道守卫
        seedSubmittedReportOnPublishedLabThenDraft(labId, stepId, "实验小结");

        assertThatThrownBy(() -> labService.createStep(teacherUser(), labId, new LabRequests.CreateStepRequest(
                2, "有提交后新增", "TEXT", "内容", "{}", 10, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生作答，不能修改题项");
        assertThatThrownBy(() -> labService.updateStep(teacherUser(), stepId, new LabRequests.UpdateStepRequest(
                1, "有提交后修改", "TEXT", "内容", "{}", 10, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生作答，不能修改题项");
        assertThatThrownBy(() -> labService.deleteStep(teacherUser(), stepId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生作答，不能修改题项");

        assertThat(labSubmissionRepository.countByLabId(labId)).isEqualTo(1);
        assertThat(labStepRepository.findByLabIdOrderByStepNoAsc(labId)).hasSize(1);
    }

    @Test
    void changeLabStatusRejectsDraftWhenLabHasStudentSubmission() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedSubmittedStudent(classId, labId);

        assertThatThrownBy(() -> labService.changeLabStatus(
                teacherUser(),
                labId,
                new LabRequests.ChangeLabStatusRequest("DRAFT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生作答，不能退回草稿");

        assertThat(labRepository.findById(labId).orElseThrow().getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    void updateLabRejectsExplicitDraftWhenLabHasStudentSubmission() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedSubmittedStudent(classId, labId);

        assertThatThrownBy(() -> labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("退回草稿", "desc", "content", 1, classId, "DRAFT", null, null, false, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生作答，不能退回草稿");

        Lab unchanged = labRepository.findById(labId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
        assertThat(unchanged.getTitle()).isEqualTo("实验-" + labId);
    }

    @Test
    void updateLabWithoutStatusKeepsCurrentStatusWhenLabHasStudentSubmission() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedSubmittedStudent(classId, labId);

        // status 为 null：视为保持当前状态，不触发回退校验，即使已有提交也必须成功
        Map<String, Object> nullStatus = labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("空状态更新", "desc", "content", 1, classId, null, null, null, false, null, null));
        // status 为空白串：同样视为保持当前状态
        Map<String, Object> blankStatus = labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("空白状态更新", "desc", "content", 1, classId, "   ", null, null, false, null, null));

        assertThat(nullStatus.get("status")).isEqualTo("PUBLISHED");
        assertThat(blankStatus.get("status")).isEqualTo("PUBLISHED");
        Lab preserved = labRepository.findById(labId).orElseThrow();
        assertThat(preserved.getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
        assertThat(preserved.getTitle()).isEqualTo("空白状态更新");
    }

    @Test
    void updateLabRejectsClassChangeWhenLabHasStudentSubmission() {
        Long classId = seedClass(teacherUser().id());
        Long anotherClassId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedSubmittedStudent(classId, labId);

        assertThatThrownBy(() -> labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("改班级", "desc", "content", 1, anotherClassId, null, null, null, false, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生作答，不能变更所属班级");

        assertThat(labRepository.findById(labId).orElseThrow().getClassId()).isEqualTo(classId);
    }

    @Test
    void updateLabAllowsSameClassIdWhenLabHasStudentSubmission() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedSubmittedStudent(classId, labId);

        // 传入相同 classId 不构成「变更所属班级」，不得拦截
        Map<String, Object> result = labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("同班更新", "desc", "content", 1, classId, null, null, null, false, null, null));

        assertThat(result.get("classId")).isEqualTo(classId);
        assertThat(labRepository.findById(labId).orElseThrow().getClassId()).isEqualTo(classId);
    }

    // ------------------------------------------------------------------
    // 实验时间窗门禁 / 发布门禁 / 并发首存快路径（技术方案 TD25–TD29）
    // ------------------------------------------------------------------

    @Test
    void saveAnswerRejectsBeforeStartAt() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());
        updateLabStartAt(labId, OffsetDateTime.now().plusHours(2));

        assertThatThrownBy(() -> labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest("答案", "{\"kind\":\"text\",\"text\":\"答案\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验尚未开始");

        // 门禁必须拦在写库之前：不得留下任何提交行
        assertThat(labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id())).isEmpty();
    }

    @Test
    void uploadAnswerImageRejectsAfterDeadline() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());
        updateLabEndAt(labId, OffsetDateTime.now().minusMinutes(1));

        MockMultipartFile file = new MockMultipartFile("file", "late.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> labService.uploadAnswerImage(studentUser(), labId, stepId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已截止");
    }

    @Test
    void submitLabRejectsBeforeStartAt() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedStep(labId, 1);
        seedMembership(classId, studentUser().id());
        updateLabStartAt(labId, OffsetDateTime.now().plusHours(2));

        assertThatThrownBy(() -> labService.submitLab(studentUser(), labId,
                new LabRequests.SubmitLabRequest("实验小结")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验尚未开始");
    }

    @Test
    void createLabRejectsStartAtNotBeforeDueAt() {
        Long classId = seedClass(teacherUser().id());
        OffsetDateTime now = OffsetDateTime.now();

        assertThatThrownBy(() -> labService.createLab(teacherUser(),
                new LabRequests.CreateLabRequest("倒置时间窗", "desc", "content", 1, classId, "DRAFT",
                        null, null, false, now, now)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始时间必须早于结束时间");
    }

    @Test
    void updateLabRejectsPublishWithDeadlineVisibilityWithoutDueAt() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        seedStep(labId, 1);

        assertThatThrownBy(() -> labService.updateLab(teacherUser(), labId,
                new LabRequests.UpdateLabRequest("发布", "desc", "content", 1, classId, "PUBLISHED",
                        null, ScoreVisibilityMode.AFTER_DEADLINE, false, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("截止后可见模式必须设置结束时间");

        // 发布校验先于 applyLab：异常后实体状态不得被污染
        assertThat(labRepository.findById(labId).orElseThrow().getStatus()).isEqualTo(ActivityStatus.DRAFT);
    }

    @Test
    void changeLabStatusRejectsDeadlineVisibilityWithoutDueAt() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        seedStep(labId, 1);
        jdbcTemplate.update(
                "UPDATE t_experiment SET score_visibility_mode = 'AFTER_DEADLINE', end_at = NULL WHERE experiment_id = ?",
                labId);

        assertThatThrownBy(() -> labService.changeLabStatus(teacherUser(), labId,
                new LabRequests.ChangeLabStatusRequest("PUBLISHED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("截止后可见模式必须设置结束时间");

        assertThat(labRepository.findById(labId).orElseThrow().getStatus()).isEqualTo(ActivityStatus.DRAFT);
    }

    @Test
    void studentLabDetailEchoesTimeWindow() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedStep(labId, 1);
        seedMembership(classId, studentUser().id());
        OffsetDateTime startAt = OffsetDateTime.now().minusHours(1).withNano(0);
        OffsetDateTime dueAt = OffsetDateTime.now().plusHours(1).withNano(0);
        updateLabTimeWindow(labId, startAt, dueAt);

        Map<String, Object> detail = labService.getStudentLabDetail(studentUser(), labId);

        assertThat(((OffsetDateTime) detail.get("startAt")).toInstant()).isEqualTo(startAt.toInstant());
        assertThat(((OffsetDateTime) detail.get("dueAt")).toInstant()).isEqualTo(dueAt.toInstant());
    }

    @Test
    void teacherLabViewEchoesTimeWindow() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        seedStep(labId, 1);
        OffsetDateTime startAt = OffsetDateTime.now().minusHours(1).withNano(0);
        OffsetDateTime dueAt = OffsetDateTime.now().plusHours(1).withNano(0);

        Map<String, Object> view = labService.updateLab(teacherUser(), labId,
                new LabRequests.UpdateLabRequest("回显实验", "desc", "content", 1, classId, "DRAFT",
                        null, null, false, startAt, dueAt));

        assertThat(((OffsetDateTime) view.get("startAt")).toInstant()).isEqualTo(startAt.toInstant());
        assertThat(((OffsetDateTime) view.get("dueAt")).toInstant()).isEqualTo(dueAt.toInstant());
    }

    @Test
    void saveAnswerSkipsClaimWhenSubmissionAlreadyExists() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());
        LabSubmission existing = new LabSubmission();
        existing.setLabId(labId);
        existing.setStudentId(studentUser().id());
        existing.setSubmitStatus(SubmissionStatus.SAVED);
        existing.setPlagiarismRate(0D);
        existing.setTotalScore(0D);
        labSubmissionRepository.saveAndFlush(existing);

        labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest("答案", "{\"kind\":\"text\",\"text\":\"答案\"}"));

        // 快路径：已有提交行时不得再走独立事务 claim
        verify(labSubmissionClaimService, never()).claim(anyLong(), anyLong());
    }

    @Test
    void repeatedSaveAnswerReusesSingleSubmissionAndAnswerRow() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest("第一次", "{\"kind\":\"text\",\"text\":\"第一次\"}"));
        labService.saveAnswer(studentUser(), labId, stepId,
                new LabRequests.SaveStepAnswerRequest("第二次", "{\"kind\":\"text\",\"text\":\"第二次\"}"));

        assertThat(labSubmissionRepository.countByLabId(labId)).isEqualTo(1);
        LabSubmission submission = labSubmissionRepository
                .findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        assertThat(answers).hasSize(1);
        assertThat(answers.getFirst().getAnswerText()).isEqualTo("第二次");
    }

    @Test
    void teacherCanReadSeededLabReports() {
        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, null, null, null, null)
        );
        List<Map<String, Object>> reports = page.items();

        assertThat(reports).isNotEmpty();
        assertThat(page.total()).isGreaterThanOrEqualTo(reports.size());
        assertThat(reports).anySatisfy(report -> {
            if ("分层架构分析实验".equals(report.get("labTitle"))) {
                assertThat(report.get("className")).isNotNull().asString().isNotBlank();
                assertThat(report.get("studentNo")).isEqualTo("20260001");
                assertThat(report.get("submitStatus")).isEqualTo("GRADED");
            }
        });
    }

    @Test
    void teacherCanFilterLabReportsByKeywordAndStatus() {
        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery("20260001", "GRADED", null, null, null, null)
        );
        List<Map<String, Object>> reports = page.items();

        assertThat(reports).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(reports.getFirst().get("labTitle")).isEqualTo("分层架构分析实验");
        assertThat(reports.getFirst().get("studentNo")).isEqualTo("20260001");
        assertThat(reports.getFirst().get("submitStatus")).isEqualTo("GRADED");
    }

    @Test
    void teacherReportListAppliesDefaultPagination() {
        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, null, null, null, null)
        );

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.items()).hasSizeLessThanOrEqualTo(10);
        assertThat(page.total()).isGreaterThanOrEqualTo(page.items().size());
    }

    @Test
    void teacherReportListSlicesPagesWithoutOverlap() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long firstSubmissionId = seedReportSubmission(labId, seedReportStudent("paging-a"), SubmissionStatus.SAVED);
        Long secondSubmissionId = seedReportSubmission(labId, seedReportStudent("paging-b"), SubmissionStatus.SUBMITTED);

        PagedResult<Map<String, Object>> firstPage = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, labId, null, 1, 1)
        );
        PagedResult<Map<String, Object>> secondPage = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, labId, null, 2, 1)
        );

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(secondPage.total()).isEqualTo(2);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(firstPage.items().getFirst().get("id")).isNotEqualTo(secondPage.items().getFirst().get("id"));
        // 排序按 id DESC：后插入的报告排在第一页
        assertThat(firstPage.items().getFirst().get("id")).isEqualTo(secondSubmissionId);
        assertThat(secondPage.items().getFirst().get("id")).isEqualTo(firstSubmissionId);
    }

    @Test
    void teacherReportKeywordMatchesLabTitleOrStudentName() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        seedReportSubmission(labId, seedReportStudent("or-match"), SubmissionStatus.SUBMITTED);
        String labTitle = labRepository.findById(labId).orElseThrow().getTitle();

        // 仅实验名命中（学生姓名不含该关键词）也必须返回，验证五字段是 OR 而不是 AND
        PagedResult<Map<String, Object>> byLabTitle = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(labTitle, null, labId, null, null, null)
        );
        // 仅学生姓名命中（实验名不含该关键词）同样必须返回
        PagedResult<Map<String, Object>> byStudentName = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery("关键词学生", null, labId, null, null, null)
        );

        assertThat(byLabTitle.items()).hasSize(1);
        assertThat(byStudentName.items()).hasSize(1);
    }

    @Test
    void teacherReportKeywordMatchesClassName() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        seedReportSubmission(labId, seedReportStudent("class-match"), SubmissionStatus.SAVED);

        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery("实验班", null, labId, null, null, null)
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().get("className")).isEqualTo("实验班");
    }

    @Test
    void teacherReportKeywordWithoutHitReturnsEmptyPage() {
        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery("绝对不存在的关键词-zzz", null, null, null, null, null)
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void teacherReportStatusFilterIgnoresCase() {
        PagedResult<Map<String, Object>> upperCase = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, "GRADED", null, null, null, null)
        );
        PagedResult<Map<String, Object>> lowerCase = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, "graded", null, null, null, null)
        );

        assertThat(upperCase.total()).isPositive();
        assertThat(lowerCase.total()).isEqualTo(upperCase.total());
        assertThat(lowerCase.items()).extracting(item -> item.get("submitStatus")).containsOnly("GRADED");
    }

    @Test
    void teacherReportInvalidStatusThrowsBadRequest() {
        // FR-10：非法枚举值统一 40000，不再静默返回空页
        assertThatThrownBy(() -> labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, "GRADEDX", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报告状态不合法: GRADEDX");
    }

    @Test
    void teacherReportRejectsIllegalPagination() {
        assertThatThrownBy(() -> labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, null, null, 0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数不合法");
        assertThatThrownBy(() -> labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, null, null, 1, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数不合法");
        assertThatThrownBy(() -> labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, null, null, 1, 101)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数不合法");
    }

    @Test
    void teacherReportListFiltersByLabId() {
        Long classId = seedClass(1L);
        Long firstLabId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long secondLabId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        seedReportSubmission(firstLabId, seedReportStudent("lab-a"), SubmissionStatus.SAVED);
        seedReportSubmission(secondLabId, seedReportStudent("lab-b"), SubmissionStatus.SAVED);

        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery(null, null, firstLabId, null, null, null)
        );

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().get("labId")).isEqualTo(firstLabId);
    }

    @Test
    void teacherWithoutLabsGetsEmptyReportStructures() {
        CurrentUser otherTeacher = new CurrentUser(987654L, "t-nolab", "无实验教师", UserRole.TEACHER);

        PagedResult<Map<String, Object>> page = labService.listTeacherReports(
                otherTeacher,
                new LabRequests.TeacherLabReportQuery(null, null, null, null, null, null)
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(labService.listTeacherReportQueue(otherTeacher)).isEmpty();
        assertThat(labService.getTeacherReportStats(otherTeacher))
                .containsEntry("total", 0L)
                .containsEntry("submitted", 0L)
                .containsEntry("graded", 0L);
    }

    @Test
    void teacherReportQueueReturnsAllReportsOrderByIdDesc() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long firstSubmissionId = seedReportSubmission(labId, seedReportStudent("queue-a"), SubmissionStatus.SAVED);
        Long secondSubmissionId = seedReportSubmission(labId, seedReportStudent("queue-b"), SubmissionStatus.SUBMITTED);

        List<Map<String, Object>> queue = labService.listTeacherReportQueue(teacherUser());

        assertThat(queue).isNotEmpty();
        List<Object> submissionIds = queue.stream().map(row -> row.get("id")).toList();
        assertThat(submissionIds).contains(firstSubmissionId, secondSubmissionId);
        assertThat(submissionIds.indexOf(secondSubmissionId)).isLessThan(submissionIds.indexOf(firstSubmissionId));

        Map<String, Object> row = queue.stream()
                .filter(item -> secondSubmissionId.equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(row).containsKeys(
                "id", "labId", "labTitle", "classId", "className", "studentId", "studentName",
                "studentUsername", "studentNo", "submitStatus", "totalScore", "summaryRequired",
                "submittedAt", "gradedAt");
        assertThat(row.get("studentNo")).asString().startsWith("kw-queue-b-");
    }

    @Test
    void teacherReportStatsAggregateAllStatuses() {
        Map<String, Object> before = labService.getTeacherReportStats(teacherUser());

        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        seedReportSubmission(labId, seedReportStudent("stats-saved"), SubmissionStatus.SAVED);
        seedReportSubmission(labId, seedReportStudent("stats-submitted"), SubmissionStatus.SUBMITTED);
        seedReportSubmission(labId, seedReportStudent("stats-graded"), SubmissionStatus.GRADED);

        Map<String, Object> after = labService.getTeacherReportStats(teacherUser());

        assertThat(longValue(after, "total") - longValue(before, "total")).isEqualTo(3L);
        assertThat(longValue(after, "submitted") - longValue(before, "submitted")).isEqualTo(1L);
        assertThat(longValue(after, "graded") - longValue(before, "graded")).isEqualTo(1L);
    }

    @Test
    void experimentSchemaUsesTeacherCompatibleTablesAndSeedData() {
        List<String> legacyViews = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.views
                WHERE table_name IN ('LAB', 'LAB_STEP')
                """,
                String.class
        );

        List<String> tableNames = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_name IN (
                    'T_EXPERIMENT',
                    'T_EXPERIMENT_ITEM',
                    'EXPERIMENT_SUBMISSION',
                    'EXPERIMENT_ANSWER',
                    'EXPERIMENT_BLANK_ANSWER_OVERRIDE'
                )
                """,
                String.class
        );

        List<String> experimentColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'T_EXPERIMENT'
                """,
                String.class
        );

        List<String> experimentItemColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'T_EXPERIMENT_ITEM'
                """,
                String.class
        );

        Map<String, Object> seedExperiment = jdbcTemplate.queryForMap(
                """
                SELECT experiment_name, experiment_type, instruction_type, score_visibility_mode, score_released, summary_required
                FROM t_experiment
                WHERE experiment_id = 1001
                """
        );

        Map<String, Object> seedExperimentItem = jdbcTemplate.queryForMap(
                """
                SELECT experiment_item_name, experiment_item_type, question_id, question_snapshot_json, editor_language, state
                FROM t_experiment_item
                WHERE id = 2001
                """
        );

        assertThat(tableNames).contains(
                "T_EXPERIMENT",
                "T_EXPERIMENT_ITEM",
                "EXPERIMENT_SUBMISSION",
                "EXPERIMENT_ANSWER",
                "EXPERIMENT_BLANK_ANSWER_OVERRIDE"
        );
        assertThat(legacyViews).isEmpty();
        assertThat(experimentColumns).contains(
                "EXPERIMENT_NO",
                "EXPERIMENT_NAME",
                "EXPERIMENT_TYPE",
                "INSTRUCTION_TYPE",
                "EXPERIMENT_REQUIREMENT",
                "EXPERIMENT_CONTENT",
                "STATE",
                "SCORE_VISIBILITY_MODE",
                "SCORE_RELEASED",
                "SUMMARY_REQUIRED"
        );
        assertThat(experimentItemColumns).contains(
                "EXPERIMENT_ITEM_NO",
                "EXPERIMENT_ITEM_NAME",
                "EXPERIMENT_ITEM_TYPE",
                "EXPERIMENT_ITEM_CONTENT",
                "EXPERIMENT_ITEM_ANSWER",
                "EXPERIMENT_ITEM_SCORE",
                "STATE",
                "QUESTION_ID",
                "QUESTION_SNAPSHOT_JSON",
                "EDITOR_LANGUAGE"
        );
        assertThat(seedExperiment)
                .containsEntry("EXPERIMENT_NAME", "分层架构分析实验")
                .containsEntry("EXPERIMENT_TYPE", 1)
                .containsEntry("INSTRUCTION_TYPE", "1")
                .containsEntry("SCORE_VISIBILITY_MODE", "AFTER_TEACHER_CONFIRM")
                .containsEntry("SCORE_RELEASED", false)
                .containsEntry("SUMMARY_REQUIRED", true);
        assertThat(seedExperimentItem)
                .containsEntry("EXPERIMENT_ITEM_NAME", "识别系统分层")
                .containsEntry("EXPERIMENT_ITEM_TYPE", 1)
                .containsEntry("QUESTION_ID", 1L)
                .containsEntry("EDITOR_LANGUAGE", "TEXT")
                .containsEntry("STATE", 1);
        assertThat(seedExperimentItem.get("QUESTION_SNAPSHOT_JSON")).asString().contains("SINGLE_CHOICE");
    }

    @Test
    void gradedLabDowngradeRemovesScoreRecord() {
        Lab lab = labRepository.findById(1001L).orElseThrow();
        LabSubmission submission = new LabSubmission();
        submission.setLabId(lab.getId());
        submission.setStudentId(studentUser().id());
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setTotalScore(12D);
        submission.setGradedAt(OffsetDateTime.now());
        submission = labSubmissionRepository.saveAndFlush(submission);
        Long submissionId = submission.getId();

        jdbcTemplate.update(
                "INSERT INTO score_record (business_type, business_id, student_id, class_id, score, graded_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                "LAB", lab.getId(), studentUser().id(), 12D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, lab.getId(), studentUser().id()))
                .isPresent();

        labServiceSupport.refreshSubmissionAfterScoring(lab, submission);
        labSubmissionRepository.flush();

        assertThat(labSubmissionRepository.findById(submissionId).orElseThrow().getSubmitStatus())
                .isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, lab.getId(), studentUser().id()))
                .isNotPresent();
    }

    @Test
    void repositoriesReadAndUpdateTeacherCompatibleEntitiesFromExperimentSchema() {
        Lab lab = labRepository.findById(1001L).orElseThrow();
        LabStep step = labStepRepository.findByIdAndLabId(2001L, 1001L).orElseThrow();

        LabSubmission submission = new LabSubmission();
        submission.setLabId(lab.getId());
        submission.setStudentId(studentUser().id());
        submission.setSubmitStatus(SubmissionStatus.SAVED);
        submission.setTeacherComment("等待提交");
        submission = labSubmissionRepository.saveAndFlush(submission);

        LabStepAnswer answer = new LabStepAnswer();
        answer.setLabSubmissionId(submission.getId());
        answer.setLabStepId(step.getId());
        answer.setAnswerText("这是实验答案");
        answer.setScore(12D);
        answer.setAutoJudgeDetail("教师兼容映射");
        answer = labStepAnswerRepository.saveAndFlush(answer);

        lab.setDescription("映射说明已更新");
        labRepository.saveAndFlush(lab);

        step.setAnswerConfigJson("{\"editorLanguage\":\"TEXT\",\"gradingMode\":\"manual\"}");
        step.setStepScore(35);
        labStepRepository.saveAndFlush(step);

        Map<String, Object> experimentRow = jdbcTemplate.queryForMap(
                "SELECT experiment_name, experiment_requirement, class_id, created_by FROM t_experiment WHERE experiment_id = ?",
                lab.getId()
        );
        Map<String, Object> experimentItemRow = jdbcTemplate.queryForMap(
                """
                SELECT experiment_id, experiment_item_no, experiment_item_name, question_type, experiment_item_type, experiment_item_score, experiment_item_answer
                FROM t_experiment_item
                WHERE id = ?
                """,
                step.getId()
        );
        Map<String, Object> submissionRow = jdbcTemplate.queryForMap(
                "SELECT experiment_id, student_id, submit_status, teacher_comment FROM experiment_submission WHERE id = ?",
                submission.getId()
        );
        Map<String, Object> answerRow = jdbcTemplate.queryForMap(
                """
                SELECT experiment_submission_id, experiment_item_id, answer_text, score, auto_judge_detail
                FROM experiment_answer
                WHERE id = ?
                """,
                answer.getId()
        );

        assertThat(experimentRow)
                .containsEntry("EXPERIMENT_NAME", "分层架构分析实验")
                .containsEntry("EXPERIMENT_REQUIREMENT", "映射说明已更新")
                .containsEntry("CLASS_ID", 1L)
                .containsEntry("CREATED_BY", 1L);
        assertThat(experimentItemRow)
                .containsEntry("EXPERIMENT_ID", lab.getId())
                .containsEntry("EXPERIMENT_ITEM_NO", 1)
                .containsEntry("EXPERIMENT_ITEM_NAME", "识别系统分层")
                .containsEntry("QUESTION_TYPE", "SINGLE_CHOICE")
                .containsEntry("EXPERIMENT_ITEM_TYPE", 1)
                .containsEntry("EXPERIMENT_ITEM_SCORE", 35)
                .containsEntry("EXPERIMENT_ITEM_ANSWER", "{\"editorLanguage\":\"TEXT\",\"gradingMode\":\"manual\"}");
        assertThat(submissionRow)
                .containsEntry("EXPERIMENT_ID", lab.getId())
                .containsEntry("STUDENT_ID", studentUser().id())
                .containsEntry("SUBMIT_STATUS", "SAVED")
                .containsEntry("TEACHER_COMMENT", "等待提交");
        assertThat(answerRow)
                .containsEntry("EXPERIMENT_SUBMISSION_ID", submission.getId())
                .containsEntry("EXPERIMENT_ITEM_ID", step.getId())
                .containsEntry("ANSWER_TEXT", "这是实验答案")
                .containsEntry("SCORE", 12D)
                .containsEntry("AUTO_JUDGE_DETAIL", "教师兼容映射");
    }

    @Test
    void experimentBlankAnswerOverrideRepositoryPersistsExperimentItemAcceptedAnswers() {
        ExperimentBlankAnswerOverride override = new ExperimentBlankAnswerOverride();
        override.setExperimentId(1001L);
        override.setExperimentItemId(2001L);
        override.setAcceptedAnswer("教师覆盖答案");
        override.setNormalizedAnswer("教师覆盖答案");
        override.setBlankAnswersJson("[\"教师覆盖答案\"]");
        override.setCreatedBy(teacherUser().id());

        ExperimentBlankAnswerOverride saved = experimentBlankAnswerOverrideRepository.saveAndFlush(override);
        List<ExperimentBlankAnswerOverride> loaded = experimentBlankAnswerOverrideRepository
                .findByExperimentIdAndExperimentItemIdOrderByIdAsc(1001L, 2001L);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT experiment_id, experiment_item_id, accepted_answer, normalized_answer, blank_answers_json, created_by FROM experiment_blank_answer_override WHERE id = ?",
                saved.getId()
        );

        assertThat(loaded).singleElement().satisfies(item -> {
            assertThat(item.getAcceptedAnswer()).isEqualTo("教师覆盖答案");
            assertThat(item.getNormalizedAnswer()).isEqualTo("教师覆盖答案");
            assertThat(item.getBlankAnswersJson()).isEqualTo("[\"教师覆盖答案\"]");
            assertThat(item.getCreatedBy()).isEqualTo(teacherUser().id());
        });
        assertThat(row)
                .containsEntry("EXPERIMENT_ID", 1001L)
                .containsEntry("EXPERIMENT_ITEM_ID", 2001L)
                .containsEntry("ACCEPTED_ANSWER", "教师覆盖答案")
                .containsEntry("NORMALIZED_ANSWER", "教师覆盖答案")
                .containsEntry("BLANK_ANSWERS_JSON", "[\"教师覆盖答案\"]")
                .containsEntry("CREATED_BY", teacherUser().id());
    }

    @Test
    void createLabDoesNotDependOnManualInitializationHook() {
        Long classId = seedClass(teacherUser().id());

        Map<String, Object> result = labService.createLab(
                teacherUser(),
                new LabRequests.CreateLabRequest("自动初始化实验", "自动初始化说明", "自动初始化内容", 1, classId, "DRAFT", null, null, true, null, null)
        );

        Long labId = ((Number) result.get("id")).longValue();
        labRepository.flush();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT experiment_id, experiment_no, experiment_content, state, summary_required FROM t_experiment WHERE experiment_id = ?",
                labId
        );

        assertThat(((Number) row.get("EXPERIMENT_ID")).longValue()).isEqualTo(labId);
        assertThat(row.get("EXPERIMENT_NO")).isEqualTo(Math.toIntExact(labId));
        assertThat(row.get("EXPERIMENT_CONTENT")).isEqualTo("自动初始化内容");
        assertThat(row.get("STATE")).isEqualTo(0);
        assertThat(row.get("SUMMARY_REQUIRED")).isEqualTo(true);
        assertThat(result.get("summaryRequired")).isEqualTo(true);
    }

    @Test
    void createStepDoesNotDependOnManualInitializationHook() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());

        Map<String, Object> result = labService.createStep(
                teacherUser(),
                labId,
                new LabRequests.CreateStepRequest(2, "自动初始化步骤", "CODE", "编写代码", "{\"template\":\"class Demo {}\"}", 25, true)
        );

        Long stepId = ((Number) result.get("id")).longValue();
        labStepRepository.flush();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT id, experiment_item_type, experiment_item_answer, question_snapshot_json, editor_language
                FROM t_experiment_item
                WHERE id = ?
                """,
                stepId
        );

        assertThat(row.get("ID")).isEqualTo(stepId);
        assertThat(row).containsEntry("EXPERIMENT_ITEM_TYPE", 6);
        assertThat(row).containsEntry("EXPERIMENT_ITEM_ANSWER", "{\"template\":\"class Demo {}\"}");
        assertThat(row).containsEntry("EDITOR_LANGUAGE", "JAVA");
        assertThat(row.get("QUESTION_SNAPSHOT_JSON")).asString().contains("CODE");
    }

    @Test
    void labStepAnswerSchemaSupportsFutureAutoGradingWithoutChangingCurrentEntity() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1);

        long submissionId = Math.abs(System.nanoTime());
        long answerId = Math.abs(System.nanoTime());

        jdbcTemplate.update(
                """
                INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                submissionId,
                labId,
                studentUser().id(),
                SubmissionStatus.SAVED.name()
        );

        jdbcTemplate.update(
                """
                INSERT INTO experiment_answer (id, experiment_submission_id, experiment_item_id, answer_text, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                answerId,
                submissionId,
                stepId,
                "等待自动评分能力接入"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT auto_score, suggested_score, score_source, auto_judge_detail
                FROM experiment_answer
                WHERE id = ?
                """,
                answerId
        );

        assertThat(row)
                .containsEntry("AUTO_SCORE", null)
                .containsEntry("SUGGESTED_SCORE", null)
                .containsEntry("AUTO_JUDGE_DETAIL", null)
                .containsEntry("SCORE_SOURCE", "TEACHER");
    }

    @Test
    void submitLabWritesAutoAndSuggestedScoringMetadata() {
        Long classId = seedClass(1L);
        Long autoLabId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long autoStepId = seedStep(autoLabId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 30);
        Long suggestedLabId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long suggestedStepId = seedStep(suggestedLabId, 1, "TEXT", "{}", "{\"keywords\":[{\"term\":\"实验管理\",\"weight\":15}],\"commentTemplate\":\"请教师确认\"}", 20);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), autoLabId, autoStepId, new LabRequests.SaveStepAnswerRequest("A", null));
        labService.saveAnswer(studentUser(), suggestedLabId, suggestedStepId, new LabRequests.SaveStepAnswerRequest("实验管理流程完整", null));

        Map<String, Object> autoSubmitResult = labService.submitLab(studentUser(), autoLabId, new LabRequests.SubmitLabRequest("自动评分小结"));
        Map<String, Object> suggestedSubmitResult = labService.submitLab(studentUser(), suggestedLabId, new LabRequests.SubmitLabRequest("推荐评分小结"));

        LabSubmission autoSubmission = labSubmissionRepository.findById(((Number) autoSubmitResult.get("submissionId")).longValue()).orElseThrow();
        LabSubmission suggestedSubmission = labSubmissionRepository.findById(((Number) suggestedSubmitResult.get("submissionId")).longValue()).orElseThrow();
        LabStepAnswer autoAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(autoSubmission.getId(), autoStepId).orElseThrow();
        LabStepAnswer suggestedAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(suggestedSubmission.getId(), suggestedStepId).orElseThrow();

        assertThat(autoAnswer.getAutoScore()).isEqualTo(30D);
        assertThat(autoAnswer.getSuggestedScore()).isNull();
        assertThat(autoAnswer.getScore()).isEqualTo(30D);
        assertThat(autoAnswer.getScoreSource()).isEqualTo("AUTO");
        assertThat(autoAnswer.getAutoJudgeDetail()).contains("matched");

        assertThat(suggestedAnswer.getAutoScore()).isNull();
        assertThat(suggestedAnswer.getSuggestedScore()).isEqualTo(15D);
        assertThat(suggestedAnswer.getScore()).isNull();
        assertThat(suggestedAnswer.getScoreSource()).isEqualTo("SUGGESTED");
        assertThat(suggestedAnswer.getAutoJudgeDetail()).contains("实验管理");
        assertThat(suggestedAnswer.getTeacherComment()).isNull();
    }

    @Test
    void submitShortAnswerLabStaysSubmittedWithSuggestedScoreOnly() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(
                labId,
                1,
                "SHORT_ANSWER",
                "{\"keywords\":[{\"term\":\"mkdir\",\"weight\":10}]}",
                "{}",
                10
        );
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("使用 mkdir 创建目录", null));
        LabStepAnswer savedAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(
                labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow().getId(),
                stepId
        ).orElseThrow();
        assertThat(savedAnswer.getScore()).isNull();

        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("简答命中小结"))
                .get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(answer.getScoreSource()).isEqualTo("SUGGESTED");
        assertThat(answer.getSuggestedScore()).isEqualTo(10D);
        assertThat(answer.getAutoScore()).isNull();
        assertThat(answer.getScore()).isNull();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getGradedAt()).isNull();
        assertThat(submission.getTotalScore()).isEqualTo(0D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        )).isNotPresent();
    }

    @Test
    void submitShortAnswerLabDoesNotAutoGradeWhenKeywordsMiss() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(
                labId,
                1,
                "SHORT_ANSWER",
                "{\"keywords\":[{\"term\":\"mkdir\",\"weight\":10}]}",
                "{}",
                10
        );
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("仅描述文件复制操作", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("简答未命中小结"))
                .get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(answer.getScoreSource()).isEqualTo("SUGGESTED");
        assertThat(answer.getSuggestedScore()).isEqualTo(0D);
        assertThat(answer.getAutoScore()).isNull();
        assertThat(answer.getScore()).isNull();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getGradedAt()).isNull();
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        )).isNotPresent();
    }

    @Test
    void submitLabUsesQuestionSnapshotAndOverrideAnswersForFillBlankScoring() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedFillBlankQuestionBankStep(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("Cloud,boot", null));
        ExperimentBlankAnswerOverride override = new ExperimentBlankAnswerOverride();
        override.setExperimentId(labId);
        override.setExperimentItemId(stepId);
        override.setAcceptedAnswer("cloud,boot");
        override.setNormalizedAnswer("cloud,boot");
        override.setBlankAnswersJson("[\"cloud\",\"boot\"]");
        override.setCreatedBy(teacherUser().id());
        experimentBlankAnswerOverrideRepository.saveAndFlush(override);

        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("填空题小结")).get("submissionId")).longValue();

        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        assertThat(answer.getAutoScore()).isEqualTo(10D);
        assertThat(answer.getSuggestedScore()).isNull();
        assertThat(answer.getScore()).isEqualTo(10D);
        assertThat(answer.getScoreSource()).isEqualTo("AUTO");
        assertThat(answer.getAutoJudgeDetail()).contains("blank 1: matched");
    }

    @Test
    void listBlankItemsOnlyReturnsFillBlankItemsForOwnedLab() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long blankStepId = seedFillBlankQuestionBankStep(labId, 1);
        seedStep(labId, 2, "TEXT", "{}", "{}", 5);
        seedStep(labId, 3, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 5);

        List<Map<String, Object>> result = labService.listBlankItems(teacherUser(), labId);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.get("id")).isEqualTo(blankStepId);
            assertThat(item.get("questionType")).isEqualTo("FILL_BLANK");
        });
    }

    @Test
    void getBlankItemAnswerDistributionAggregatesAnswersWithinCurrentLabAndItem() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long itemId = seedFillBlankQuestionBankStep(labId, 1);
        Long otherItemId = seedFillBlankQuestionBankStep(labId, 2);
        Long otherLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long otherLabItemId = seedFillBlankQuestionBankStep(otherLabId, 1);

        ExperimentBlankAnswerOverride override = new ExperimentBlankAnswerOverride();
        override.setExperimentId(labId);
        override.setExperimentItemId(itemId);
        override.setAcceptedAnswer("spring");
        override.setNormalizedAnswer("spring");
        override.setCreatedBy(teacherUser().id());
        experimentBlankAnswerOverrideRepository.saveAndFlush(override);

        seedSubmissionAnswer(labId, itemId, 201L, " Spring ");
        seedSubmissionAnswer(labId, itemId, 202L, "spring");
        seedSubmissionAnswer(labId, itemId, 203L, "Spring Boot");
        seedSubmissionAnswer(labId, otherItemId, 204L, "spring");
        seedSubmissionAnswer(otherLabId, otherLabItemId, 205L, "spring");

        Map<String, Object> result = labService.getBlankItemAnswerDistribution(teacherUser(), labId, itemId);

        assertThat(result).containsKeys("item", "acceptedAnswers", "answerDistribution");
        assertThat((Map<String, Object>) result.get("item"))
                .containsEntry("id", itemId)
                .containsEntry("labId", labId)
                .containsEntry("questionType", "FILL_BLANK")
                .containsKeys("stepNo", "stepScore", "questionId", "content");
        assertThat((List<String>) result.get("acceptedAnswers")).containsExactly("spring");
        assertThat((List<Map<String, Object>>) result.get("answerDistribution")).hasSize(2);
        assertThat((List<Map<String, Object>>) result.get("answerDistribution")).anySatisfy(item -> {
            if ("spring".equals(item.get("normalizedAnswer"))) {
                assertThat(item.get("count")).isEqualTo(2L);
                assertThat(item.get("accepted")).isEqualTo(false);
            }
        });
        assertThat((List<Map<String, Object>>) result.get("answerDistribution")).anySatisfy(item -> {
            if ("spring boot".equals(item.get("normalizedAnswer"))) {
                assertThat(item.get("count")).isEqualTo(1L);
                assertThat(item.get("accepted")).isEqualTo(true);
            }
        });
    }

    @Test
    void saveBlankAcceptedAnswersRegradesCurrentLabItemIncludingSavedSubmissionsWithoutPromotingDraftStatus() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long targetItemId = seedFillBlankQuestionBankStep(labId, 1);
        Long otherItemId = seedStep(labId, 2, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 10);
        Long otherLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long otherLabItemId = seedFillBlankQuestionBankStep(otherLabId, 1);

        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, targetItemId, new LabRequests.SaveStepAnswerRequest("cloud,boot", null));
        labService.saveAnswer(studentUser(), labId, otherItemId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("重判前小结")).get("submissionId")).longValue();

        CurrentUser savedStudent = new CurrentUser(202L, "student-202", "草稿学生", UserRole.STUDENT);
        seedStudentAccount(savedStudent);
        seedMembership(classId, savedStudent.id());
        labService.saveAnswer(savedStudent, labId, targetItemId, new LabRequests.SaveStepAnswerRequest("cloud,boot", null));
        Long savedSubmissionId = labSubmissionRepository.findByLabIdAndStudentId(labId, savedStudent.id()).orElseThrow().getId();

        CurrentUser otherStudent = new CurrentUser(201L, "student-201", "其他学生", UserRole.STUDENT);
        seedStudentAccount(otherStudent);
        seedMembership(classId, otherStudent.id());
        labService.saveAnswer(otherStudent, otherLabId, otherLabItemId, new LabRequests.SaveStepAnswerRequest("cloud,boot", null));
        Long otherSubmissionId = ((Number) labService.submitLab(otherStudent, otherLabId, new LabRequests.SubmitLabRequest("其他实验小结")).get("submissionId")).longValue();

        LabStepAnswer targetAnswerBefore = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, targetItemId).orElseThrow();
        LabStepAnswer otherAnswerBefore = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, otherItemId).orElseThrow();
        LabStepAnswer savedAnswerBefore = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(savedSubmissionId, targetItemId).orElseThrow();
        LabStepAnswer externalAnswerBefore = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(otherSubmissionId, otherLabItemId).orElseThrow();
        assertThat(targetAnswerBefore.getScore()).isEqualTo(6D);
        assertThat(otherAnswerBefore.getScore()).isEqualTo(10D);
        assertThat(savedAnswerBefore.getScore()).isNull();
        assertThat(externalAnswerBefore.getScore()).isEqualTo(6D);

        Map<String, Object> result = labService.saveBlankAcceptedAnswers(
                teacherUser(),
                labId,
                new LabRequests.SaveBlankAcceptedAnswersRequest(targetItemId, List.of("cloud,boot"))
        );

        LabStepAnswer targetAnswerAfter = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, targetItemId).orElseThrow();
        LabStepAnswer otherAnswerAfter = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, otherItemId).orElseThrow();
        LabStepAnswer savedAnswerAfter = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(savedSubmissionId, targetItemId).orElseThrow();
        LabStepAnswer externalAnswerAfter = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(otherSubmissionId, otherLabItemId).orElseThrow();
        LabSubmission submissionAfter = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabSubmission savedSubmissionAfter = labSubmissionRepository.findById(savedSubmissionId).orElseThrow();
        LabSubmission externalSubmissionAfter = labSubmissionRepository.findById(otherSubmissionId).orElseThrow();
        List<ExperimentBlankAnswerOverride> overrides = experimentBlankAnswerOverrideRepository
                .findByExperimentIdAndExperimentItemIdOrderByIdAsc(labId, targetItemId);

        assertThat(result).containsEntry("saved", true).containsEntry("experimentItemId", targetItemId).containsEntry("regradedCount", 2);
        assertThat(result.get("acceptedAnswers")).isEqualTo(List.of("cloud,boot"));
        assertThat(overrides).singleElement().satisfies(item -> {
            assertThat(item.getAcceptedAnswer()).isEqualTo("cloud,boot");
            assertThat(item.getNormalizedAnswer()).isEqualTo("cloud,boot");
        });
        // AFTER_TEACHER_CONFIRM + GRADED：成绩已对学生可见，重判只刷新建议字段，冻结最终分与总分。
        assertThat(targetAnswerAfter.getScore()).isEqualTo(6D);
        assertThat(targetAnswerAfter.getAutoScore()).isEqualTo(10D);
        // TD18：冻结分支只刷新建议字段，填空题建议分为空时保持原值。
        assertThat(targetAnswerAfter.getSuggestedScore()).isEqualTo(targetAnswerBefore.getSuggestedScore());
        // TD18：冻结分支不写 scoreSource，保持原值。
        assertThat(targetAnswerAfter.getScoreSource()).isEqualTo(targetAnswerBefore.getScoreSource());
        assertThat(otherAnswerAfter.getScore()).isEqualTo(10D);
        assertThat(savedAnswerAfter.getScore()).isEqualTo(10D);
        assertThat(savedAnswerAfter.getAutoScore()).isEqualTo(10D);
        assertThat(externalAnswerAfter.getScore()).isEqualTo(6D);
        assertThat(submissionAfter.getTotalScore()).isEqualTo(16D);
        assertThat(submissionAfter.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(savedSubmissionAfter.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(savedSubmissionAfter.getTotalScore()).isEqualTo(10D);
        assertThat(savedSubmissionAfter.getGradedAt()).isNull();
        assertThat(externalSubmissionAfter.getTotalScore()).isEqualTo(6D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()))
                .isPresent()
                .get()
                .extracting(com.opencode.teachingplatform.analysis.entity.ScoreRecord::getScore)
                .isEqualTo(16D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(com.opencode.teachingplatform.grading.BusinessType.LAB, labId, savedStudent.id()))
                .isNotPresent();
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(com.opencode.teachingplatform.grading.BusinessType.LAB, otherLabId, otherStudent.id()))
                .isPresent()
                .get()
                .extracting(com.opencode.teachingplatform.analysis.entity.ScoreRecord::getScore)
                .isEqualTo(6D);
    }

    @Test
    void saveBlankAcceptedAnswersShouldNotBroadcastAcceptedWholeAnswerAcrossBlankSlots() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long targetItemId = seedFillBlankQuestionBankStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, targetItemId, new LabRequests.SaveStepAnswerRequest("cloud,cloud", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("防扩散小结")).get("submissionId")).longValue();

        Map<String, Object> saveResult = labService.saveBlankAcceptedAnswers(
                teacherUser(),
                labId,
                new LabRequests.SaveBlankAcceptedAnswersRequest(targetItemId, List.of("cloud"))
        );

        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, targetItemId).orElseThrow();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(saveResult.get("acceptedAnswers")).isEqualTo(List.of("cloud"));
        assertThat(answer.getScore()).isEqualTo(0D);
        assertThat(answer.getAutoScore()).isEqualTo(0D);
        assertThat(submission.getTotalScore()).isEqualTo(0D);
    }

    @Test
    void submitLabTreatsCodeStepAsSubjectiveRecommendation() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedCodeStepWithSnapshot(labId, 1);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("通过 scoring engine 复用评分策略", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("代码题小结")).get("submissionId")).longValue();

        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(answer.getAutoScore()).isNull();
        assertThat(answer.getSuggestedScore()).isEqualTo(18D);
        assertThat(answer.getScore()).isNull();
        assertThat(answer.getScoreSource()).isEqualTo("SUGGESTED");
        assertThat(answer.getAutoJudgeDetail()).contains("scoring");
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getTotalScore()).isEqualTo(0D);
    }

    @Test
    void teacherGradeOverridesAutoScoreAndMarksTeacherSource() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 30);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("教师覆盖小结")).get("submissionId")).longValue();
        LabStepAnswer autoGradedAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        Map<String, Object> result = labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest(
                        "教师最终评分",
                        List.of(new LabRequests.GradeItem(autoGradedAnswer.getId(), 26D, "教师覆盖自动评分"))
                )
        );

        LabStepAnswer gradedAnswer = labStepAnswerRepository.findById(autoGradedAnswer.getId()).orElseThrow();
        LabSubmission gradedSubmission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(result.get("totalScore")).isEqualTo(26D);
        assertThat(gradedAnswer.getScore()).isEqualTo(26D);
        assertThat(gradedAnswer.getTeacherComment()).isEqualTo("教师覆盖自动评分");
        assertThat(gradedAnswer.getAutoScore()).isEqualTo(30D);
        assertThat(gradedAnswer.getScoreSource()).isEqualTo("TEACHER");
        assertThat(gradedSubmission.getTotalScore()).isEqualTo(26D);
        assertThat(gradedSubmission.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
    }

    @Test
    void gradeReportRejectsPartialTeacherGradingForAnsweredItems() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long firstStepId = seedStep(labId, 1, "TEXT", "{}", "{\"keywords\":[{\"term\":\"架构\",\"weight\":10}],\"commentTemplate\":\"请教师确认\"}", 10);
        Long secondStepId = seedStep(labId, 2, "TEXT", "{}", "{\"keywords\":[{\"term\":\"分层\",\"weight\":10}],\"commentTemplate\":\"请教师确认\"}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, firstStepId, new LabRequests.SaveStepAnswerRequest("架构分析", null));
        labService.saveAnswer(studentUser(), labId, secondStepId, new LabRequests.SaveStepAnswerRequest("分层设计", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("部分评分小结")).get("submissionId")).longValue();
        LabStepAnswer firstAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, firstStepId).orElseThrow();

        assertThatThrownBy(() -> labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest(
                        "只评一题",
                        List.of(new LabRequests.GradeItem(firstAnswer.getId(), 8D, "仅第一题"))
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成");

        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'LAB' AND business_id = ? AND student_id = ?",
                Integer.class,
                labId,
                studentUser().id()
        );

        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getGradedAt()).isNull();
        assertThat(scoreRecordCount).isZero();
    }

    @Test
    void gradeReportRejectsSavedSubmission() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedFillBlankQuestionBankStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("cloud,boot", null));
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);

        assertThatThrownBy(() -> labService.gradeReport(
                teacherUser(),
                submission.getId(),
                new LabRequests.GradeLabReportRequest(
                        "草稿批改",
                        List.of(new LabRequests.GradeItem(answer.getId(), 8D, "不该写入"))
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生尚未提交实验报告，不能批改");

        LabSubmission unchangedSubmission = labSubmissionRepository.findById(submission.getId()).orElseThrow();
        LabStepAnswer unchangedAnswer = labStepAnswerRepository.findById(answer.getId()).orElseThrow();
        assertThat(unchangedSubmission.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(unchangedSubmission.getGradedAt()).isNull();
        assertThat(unchangedAnswer.getScore()).isNull();
        assertThat(countLabScoreRecords(labId, studentUser().id())).isZero();
    }

    @Test
    void confirmStepScoreRejectsSavedSubmission() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedFillBlankQuestionBankStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("cloud,boot", null));
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), stepId).orElseThrow();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);

        assertThatThrownBy(() -> labService.confirmStepScore(
                teacherUser(),
                submission.getId(),
                new LabRequests.ConfirmStepScoreRequest(answer.getId(), 9D, "不该写入", false)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生尚未提交实验报告，不能批改");

        LabStepAnswer unchangedAnswer = labStepAnswerRepository.findById(answer.getId()).orElseThrow();
        assertThat(unchangedAnswer.getScore()).isNull();
        assertThat(countLabScoreRecords(labId, studentUser().id())).isZero();
    }

    @Test
    void gradeReportStillAllowsSubmittedAndGraded() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 30);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("门禁放行小结")).get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest("首次批改", List.of(new LabRequests.GradeItem(answer.getId(), 25D, "首次")))
        );
        LabSubmission submittedToGraded = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(submittedToGraded.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(submittedToGraded.getTotalScore()).isEqualTo(25D);

        labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest("重判批改", List.of(new LabRequests.GradeItem(answer.getId(), 28D, "重判")))
        );
        LabSubmission regraded = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer regradedAnswer = labStepAnswerRepository.findById(answer.getId()).orElseThrow();
        assertThat(regraded.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(regraded.getTotalScore()).isEqualTo(28D);
        assertThat(regradedAnswer.getScore()).isEqualTo(28D);
    }

    private int countLabScoreRecords(Long labId, Long studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'LAB' AND business_id = ? AND student_id = ?",
                Integer.class,
                labId,
                studentId
        );
        return count == null ? 0 : count;
    }

    @Test
    void gradeReportRejectsDuplicateAnswerItems() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 30);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("重复项小结")).get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        LabSubmission before = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThatThrownBy(() -> labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest(
                        "重复评分",
                        List.of(
                                new LabRequests.GradeItem(answer.getId(), 10D, "第一次"),
                                new LabRequests.GradeItem(answer.getId(), 12D, "第二次")
                        )
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评分项重复");

        LabSubmission after = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer unchanged = labStepAnswerRepository.findById(answer.getId()).orElseThrow();
        // 客观题自动评分已把提交推到 GRADED，重复项校验失败后不得改变任何状态。
        assertThat(after.getSubmitStatus()).isEqualTo(before.getSubmitStatus());
        assertThat(after.getTotalScore()).isEqualTo(before.getTotalScore());
        assertThat(unchanged.getScore()).isEqualTo(30D);
        assertThat(unchanged.getScoreSource()).isEqualTo("AUTO");
    }

    @Test
    void gradeReportRejectsScoresOutsideStepRange() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 30);
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("越界小结")).get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        int scoreRecordCountBefore = countLabScoreRecords(labId, studentUser().id());

        assertThatThrownBy(() -> labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest("负分", List.of(new LabRequests.GradeItem(answer.getId(), -1D, "负分")))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数必须在 0 到该题分值之间");

        assertThatThrownBy(() -> labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest("超分", List.of(new LabRequests.GradeItem(answer.getId(), 31D, "超分")))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数必须在 0 到该题分值之间");

        LabStepAnswer unchanged = labStepAnswerRepository.findById(answer.getId()).orElseThrow();
        assertThat(unchanged.getScore()).isEqualTo(30D);
        assertThat(countLabScoreRecords(labId, studentUser().id())).isEqualTo(scoreRecordCountBefore);
    }

    @Test
    void saveBlankAcceptedAnswersFreezesTeacherScoreButRefreshesAutoAnswerWhenScoreNotVisible() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        // MANUAL_RELEASE 且未发布：即使提交已 GRADED，成绩对学生仍不可见，重判不应冻结自动分。
        jdbcTemplate.update("UPDATE t_experiment SET score_visibility_mode = 'MANUAL_RELEASE' WHERE experiment_id = ?", labId);
        // 直改数据库后必须清一级缓存，否则后续读取仍拿到旧的可见性模式。
        entityManager.clear();
        Long manualItemId = seedFillBlankQuestionBankStep(labId, 1);
        Long autoItemId = seedFillBlankQuestionBankStep(labId, 2);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, manualItemId, new LabRequests.SaveStepAnswerRequest("spring,boot", null));
        labService.saveAnswer(studentUser(), labId, autoItemId, new LabRequests.SaveStepAnswerRequest("cloud,boot", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("冻结语义小结")).get("submissionId")).longValue();
        LabStepAnswer manualAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, manualItemId).orElseThrow();
        assertThat(manualAnswer.getScoreSource()).isEqualTo("AUTO");
        assertThat(labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, autoItemId).orElseThrow().getScore())
                .isEqualTo(6D);

        // 教师对第一题人工给分，形成 scoreSource=TEACHER 的答案。
        labService.confirmStepScore(
                teacherUser(),
                submissionId,
                new LabRequests.ConfirmStepScoreRequest(manualAnswer.getId(), 5D, "人工调整", false)
        );
        // 第二题用错答案提交得到 6 分，随后教师把该答案追加为可接受答案，验证 AUTO 答案会被重判改写。
        labService.saveBlankAcceptedAnswers(
                teacherUser(),
                labId,
                new LabRequests.SaveBlankAcceptedAnswersRequest(autoItemId, List.of("cloud,boot"))
        );
        LabStepAnswer autoAfterAccepted = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, autoItemId).orElseThrow();
        assertThat(autoAfterAccepted.getAutoScore()).isEqualTo(10D);
        assertThat(autoAfterAccepted.getScore()).isEqualTo(10D);

        Map<String, Object> result = labService.saveBlankAcceptedAnswers(
                teacherUser(),
                labId,
                new LabRequests.SaveBlankAcceptedAnswersRequest(manualItemId, List.of("spring,boot"))
        );

        LabStepAnswer manualAfter = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, manualItemId).orElseThrow();
        LabSubmission submissionAfter = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(result.get("regradedCount")).isEqualTo(1);
        // 人工分与评语冻结，仅自动分刷新；填空题建议分为空。
        assertThat(manualAfter.getScore()).isEqualTo(5D);
        assertThat(manualAfter.getScoreSource()).isEqualTo("TEACHER");
        assertThat(manualAfter.getTeacherComment()).isEqualTo("人工调整");
        assertThat(manualAfter.getAutoScore()).isEqualTo(10D);
        assertThat(manualAfter.getSuggestedScore()).isNull();
        assertThat(submissionAfter.getTotalScore()).isEqualTo(15D);
        assertThat(submissionAfter.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(countLabScoreRecords(labId, studentUser().id())).isEqualTo(1);
    }

    @Test
    void saveAnswerRejectsParentDirectorySegmentInImagePath() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        assertThatThrownBy(() -> labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "说明",
                        "{\"kind\":\"text\",\"text\":\"说明\",\"images\":[{\"path\":\"lab-answers/2026-09-04/../../etc/passwd\",\"name\":\"passwd\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片路径不合法");
    }

    @Test
    void saveAnswerRejectsImagePathOwnedByAnotherStudent() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());
        CurrentUser otherStudent = new CurrentUser(203L, "student-203", "隔壁学生", UserRole.STUDENT);
        seedStudentAccount(otherStudent);
        seedMembership(classId, otherStudent.id());

        MockMultipartFile file = new MockMultipartFile("file", "other.png", "image/png", new byte[] {1, 2, 3});
        String otherPath = String.valueOf(labService.uploadAnswerImage(otherStudent, labId, stepId, file).get("path"));

        assertThatThrownBy(() -> labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "盗用路径",
                        "{\"kind\":\"text\",\"text\":\"盗用路径\",\"images\":[{\"path\":\"" + otherPath + "\",\"name\":\"other.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片路径不合法");
    }

    @Test
    void previewAnswerImageRejectsParentDirectorySegmentInPath() {
        assertThatThrownBy(() -> labService.previewAnswerImage(studentUser(), "lab-answers/2026-09-04/../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片路径不合法");
    }

    @Test
    void previewAnswerImageRejectsOtherStudentSegmentForStudent() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());
        CurrentUser otherStudent = new CurrentUser(204L, "student-204", "隔壁学生", UserRole.STUDENT);
        seedStudentAccount(otherStudent);
        seedMembership(classId, otherStudent.id());

        MockMultipartFile file = new MockMultipartFile("file", "other.png", "image/png", new byte[] {1, 2, 3});
        String otherPath = String.valueOf(labService.uploadAnswerImage(studentUser(), labId, stepId, file).get("path"));

        // 学生 B 的答案被直接写入学生 A 的图片路径，绕过保存校验，用于覆盖预览归属校验。
        labService.saveAnswer(otherStudent, labId, stepId, new LabRequests.SaveStepAnswerRequest("占位", null));
        Long otherSubmissionId = labSubmissionRepository.findByLabIdAndStudentId(labId, otherStudent.id()).orElseThrow().getId();
        jdbcTemplate.update(
                "UPDATE experiment_answer SET answer_json = ? WHERE experiment_submission_id = ? AND experiment_item_id = ?",
                "{\"kind\":\"text\",\"text\":\"占位\",\"images\":[{\"path\":\"" + otherPath + "\",\"name\":\"other.png\",\"contentType\":\"image/png\",\"size\":3}]}",
                otherSubmissionId,
                stepId
        );
        // 绕过 JPA 直改后清空一级缓存，保证后续查询读到新 JSON。
        entityManager.clear();

        assertThatThrownBy(() -> labService.previewAnswerImage(otherStudent, otherPath))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该图片");
    }

    @Test
    void previewAnswerImageAllowsLegacyPathWithoutStudentSegment() throws Exception {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());

        String legacyPath = "lab-answers/2026-09-15/legacy.png";
        Path legacyFile = storageRoot.resolve(legacyPath).normalize();
        java.nio.file.Files.createDirectories(legacyFile.getParent());
        java.nio.file.Files.write(legacyFile, new byte[] {7, 8, 9});

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("历史", null));
        Long submissionId = labSubmissionRepository.findByLabIdAndStudentId(labId, studentUser().id()).orElseThrow().getId();
        jdbcTemplate.update(
                "UPDATE experiment_answer SET answer_json = ? WHERE experiment_submission_id = ? AND experiment_item_id = ?",
                "{\"kind\":\"text\",\"text\":\"历史\",\"images\":[{\"path\":\"" + legacyPath + "\",\"name\":\"legacy.png\",\"contentType\":\"image/png\",\"size\":3}]}",
                submissionId,
                stepId
        );
        // 绕过 JPA 直改后清空一级缓存，保证后续查询读到新 JSON。
        entityManager.clear();

        LabService.PreviewAnswerImageResult result =
                labService.previewAnswerImage(studentUser(), legacyPath);
        assertThat(result.bytes()).containsExactly(7, 8, 9);
    }

    @Test
    void suggestedLabScoreShouldRemainHiddenAndSkipScoreRecordBeforeTeacherConfirmation() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", "{\"keywords\":[{\"term\":\"实验管理\",\"weight\":15}],\"commentTemplate\":\"请教师确认\"}", 20);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("实验管理流程完整", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("建议分待确认小结")).get("submissionId")).longValue();

        Map<String, Object> studentDetail = labService.getStudentLabDetail(studentUser(), labId);
        List<Map<String, Object>> studentLabs = labService.listStudentLabs(studentUser());
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'LAB' AND business_id = ? AND student_id = ?",
                Integer.class,
                labId,
                studentUser().id()
        );

        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getTotalScore()).isEqualTo(0D);
        assertThat(studentDetail.get("totalScore")).isNull();
        assertThat(studentDetail.get("teacherComment")).isNull();
        assertThat(studentLabs).anySatisfy(item -> {
            if (labId.equals(item.get("id"))) {
                assertThat(item.get("submissionStatus")).isEqualTo("SUBMITTED");
                assertThat(item.get("totalScore")).isNull();
                assertThat(item.get("teacherComment")).isNull();
            }
        });
        assertThat(scoreRecordCount).isZero();
    }

    @Test
    void teacherCanAcceptSuggestedLabScoreAndPublishFinalSubmissionScore() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{}", "{\"keywords\":[{\"term\":\"实验管理\",\"weight\":15}],\"commentTemplate\":\"请教师确认\"}", 20);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("实验管理流程完整", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("接受建议分小结")).get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        Map<String, Object> confirmResult = labService.confirmStepScore(
                teacherUser(),
                submissionId,
                new LabRequests.ConfirmStepScoreRequest(answer.getId(), null, "接受推荐分", true)
        );
        Map<String, Object> studentDetail = labService.getStudentLabDetail(studentUser(), labId);
        Double scoreRecord = jdbcTemplate.queryForObject(
                "SELECT score FROM score_record WHERE business_type = 'LAB' AND business_id = ? AND student_id = ?",
                Double.class,
                labId,
                studentUser().id()
        );

        assertThat(confirmResult.get("submissionStatus")).isEqualTo("GRADED");
        assertThat(studentDetail.get("totalScore")).isEqualTo(scoreRecord);
        assertThat(studentDetail.get("teacherComment")).isNull();
    }

    @Test
    void manualReleaseModeShowsLabScoreAfterTeacherRelease() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        jdbcTemplate.update("UPDATE t_experiment SET score_visibility_mode = ? WHERE experiment_id = ?", "MANUAL_RELEASE", labId);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 20);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("手动发布模式小结")).get("submissionId")).longValue();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest("确认成绩", List.of(new LabRequests.GradeItem(answer.getId(), 20D, "老师确认")))
        );

        Map<String, Object> hiddenDetail = labService.getStudentLabDetail(studentUser(), labId);
        assertThat(hiddenDetail.get("totalScore")).isNull();

        labService.releaseScores(teacherUser(), labId);
        Map<String, Object> visibleDetail = labService.getStudentLabDetail(studentUser(), labId);
        assertThat(visibleDetail.get("totalScore")).isEqualTo(20D);
    }

    @Test
    void immediateVisibilityShowsZeroScoreToStudent() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        jdbcTemplate.update("UPDATE t_experiment SET score_visibility_mode = ? WHERE experiment_id = ?", "IMMEDIATE", labId);
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 20);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("B", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("零分可见小结")).get("submissionId")).longValue();

        Map<String, Object> studentDetail = labService.getStudentLabDetail(studentUser(), labId);
        List<Map<String, Object>> studentLabs = labService.listStudentLabs(studentUser());
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(submission.getTotalScore()).isEqualTo(0D);
        assertThat(studentDetail.get("totalScore")).isEqualTo(0D);
        assertThat(studentLabs).anySatisfy(item -> {
            if (labId.equals(item.get("id"))) {
                assertThat(item.get("totalScore")).isEqualTo(0D);
            }
        });
    }

    @Test
    void studentLabDetailDoesNotExposeAutoJudgeDetail() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, 1L);
        Long stepId = seedStep(labId, 1, "TEXT", "{\"keywords\":[{\"term\":\"实验管理\",\"weight\":15}]}", 20);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("实验管理流程完整", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("详情视图小结")).get("submissionId")).longValue();

        Map<String, Object> teacherDetail = labService.getTeacherReportDetail(teacherUser(), submissionId);
        Map<String, Object> studentDetail = labService.getStudentLabDetail(studentUser(), labId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teacherItems = (List<Map<String, Object>>) teacherDetail.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studentItems = (List<Map<String, Object>>) studentDetail.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> studentSteps = (List<Map<String, Object>>) studentDetail.get("steps");

        assertThat(teacherItems.getFirst())
                .containsEntry("stepTitle", "步骤1")
                .containsEntry("questionType", "TEXT")
                .containsEntry("maxScore", 20)
                .containsKeys("autoScore", "suggestedScore", "scoreSource", "autoJudgeDetail");
        assertThat(teacherItems.getFirst().get("scoreSource")).isEqualTo("SUGGESTED");
        assertThat(teacherItems.getFirst().get("autoJudgeDetail")).asString().contains("实验管理");
        assertThat(teacherItems.getFirst().get("teacherComment")).isNull();
        assertThat(teacherDetail.get("className")).isEqualTo("实验班");
        assertThat(teacherDetail.get("studentNo")).isEqualTo(studentUser().username());
        assertThat(teacherDetail.get("summaryText")).isEqualTo("详情视图小结");
        assertThat(teacherDetail).doesNotContainKey("reportScore");
        assertThat(studentDetail.get("className")).isEqualTo("实验班");
        assertThat(studentDetail.get("summaryText")).isEqualTo("详情视图小结");
        assertThat(studentItems.getFirst()).doesNotContainKey("autoJudgeDetail");
        assertThat(studentSteps.getFirst()).doesNotContainKey("autoJudgeDetail");
        assertThat(studentSteps.getFirst().get("teacherComment")).isNull();
    }

    @Test
    void labViewsExposeSummaryRequiredForStudentAndTeacher() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "SINGLE_CHOICE", "{}", "{\"correctAnswer\":\"A\"}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());
        jdbcTemplate.update("UPDATE t_experiment SET summary_required = ? WHERE experiment_id = ?", true, labId);

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("A", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("需要小结")).get("submissionId")).longValue();

        List<Map<String, Object>> teacherLabs = labService.listTeacherLabs(teacherUser());
        List<Map<String, Object>> studentLabs = labService.listStudentLabs(studentUser());
        Map<String, Object> studentDetail = labService.getStudentLabDetail(studentUser(), labId);
        Map<String, Object> teacherDetail = labService.getTeacherReportDetail(teacherUser(), submissionId);

        assertThat(teacherLabs).anySatisfy(item -> {
            if (labId.equals(item.get("id"))) {
                assertThat(item.get("summaryRequired")).isEqualTo(true);
            }
        });
        assertThat(studentLabs).anySatisfy(item -> {
            if (labId.equals(item.get("id"))) {
                assertThat(item.get("summaryRequired")).isEqualTo(true);
            }
        });
        assertThat(studentDetail.get("summaryRequired")).isEqualTo(true);
        assertThat(teacherDetail.get("summaryRequired")).isEqualTo(true);
    }

    @Test
    void teacherLabReportViewUsesLatestSubmissionForLabAndStudent() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("older answer", null));
        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("newest answer", null));

        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("新小结")).get("submissionId")).longValue();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        submission.setTeacherComment("新评语");
        submission.setTotalScore(88D);
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        labSubmissionRepository.saveAndFlush(submission);

        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        answer.setScore(88D);
        labStepAnswerRepository.saveAndFlush(answer);

        Map<String, Object> reportView = labService.getTeacherLabReportView(teacherUser(), labId, studentUser().id());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) reportView.get("steps");

        assertThat(reportView.get("labId")).isEqualTo(labId);
        assertThat(reportView.get("studentId")).isEqualTo(studentUser().id());
        assertThat(reportView.get("courseName")).isEqualTo("软件设计与体系结构");
        assertThat(reportView.get("totalScore")).isEqualTo(88D);
        assertThat(reportView.get("teacherComment")).isEqualTo("新评语");
        assertThat(reportView.get("summaryText")).isEqualTo("新小结");
        assertThat(steps).singleElement().satisfies(step -> {
            assertThat(step.get("answerText")).isEqualTo("newest answer");
            assertThat(step.get("images")).asList().isEmpty();
        });
    }

    @Test
    void teacherLabReportViewIncludesStepImagesWhenAnswerHasUploads() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        MockMultipartFile file = new MockMultipartFile("file", "shot1.png", "image/png", new byte[] {1, 2, 3});
        Map<String, Object> uploaded = labService.uploadAnswerImage(studentUser(), labId, stepId, file);
        String path = String.valueOf(uploaded.get("path"));
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest(
                        "截图题",
                        "{\"kind\":\"text\",\"text\":\"截图题\",\"images\":[{\"path\":\"" + path + "\",\"name\":\"shot1.png\",\"contentType\":\"image/png\",\"size\":3}]}"
                )
        );
        labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("小结"));

        Map<String, Object> reportView = labService.getTeacherLabReportView(teacherUser(), labId, studentUser().id());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) reportView.get("steps");

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).get("answerText")).isEqualTo("截图题");
        assertThat(steps.get(0).get("images")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> image = (Map<String, Object>) ((List<?>) steps.get(0).get("images")).get(0);
        assertThat(image.get("path")).isEqualTo(path);
    }

    @Test
    void teacherLabReportViewHidesScoreWhenSubmissionNotYetGraded() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("draft answer", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("待批改小结")).get("submissionId")).longValue();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        submission.setTotalScore(66D);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setGradedAt(null);
        labSubmissionRepository.saveAndFlush(submission);

        Map<String, Object> reportView = labService.getTeacherLabReportView(teacherUser(), labId, studentUser().id());

        assertThat(reportView.get("totalScore")).isNull();
    }

    @Test
    void returnSubmittedReportResetsStatusAndClearsScoring() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", "{\"keywords\":[{\"term\":\"架构\",\"weight\":10}],\"commentTemplate\":\"请教师确认\"}", 20);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("架构设计说明", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("待打回小结")).get("submissionId")).longValue();
        LabSubmission beforeReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer beforeAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        assertThat(beforeReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(beforeReturn.getSubmittedAt()).isNotNull();
        assertThat(beforeAnswer.getSuggestedScore()).isNotNull();

        Map<String, Object> result = labService.returnReport(teacherUser(), submissionId);

        LabSubmission afterReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer afterAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        assertThat(result).containsEntry("status", "SAVED")
                .containsEntry("submissionId", submissionId)
                .containsEntry("labId", labId)
                .containsEntry("studentId", studentUser().id());
        assertThat(afterReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(afterReturn.getSubmittedAt()).isNull();
        assertThat(afterReturn.getGradedAt()).isNull();
        assertThat(afterReturn.getTotalScore()).isEqualTo(0D);
        assertThat(afterReturn.getSummaryText()).isEqualTo("待打回小结");
        assertThat(afterAnswer.getAnswerText()).isEqualTo("架构设计说明");
        assertThat(afterAnswer.getAutoScore()).isNull();
        assertThat(afterAnswer.getSuggestedScore()).isNull();
        assertThat(afterAnswer.getScore()).isNull();
        assertThat(afterAnswer.getScoreSource()).isEqualTo("TEACHER");
    }

    @Test
    void returnGradedReportResetsStatusAndDeletesScoreRecord() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedCodeStepWithSnapshot(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("通过 scoring engine 复用评分策略", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("待终评小结")).get("submissionId")).longValue();
        LabStepAnswer gradedAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        assertThat(labSubmissionRepository.findById(submissionId).orElseThrow().getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);

        labService.gradeReport(
                teacherUser(),
                submissionId,
                new LabRequests.GradeLabReportRequest(
                        "教师终评",
                        List.of(new LabRequests.GradeItem(gradedAnswer.getId(), 18D, "代码题终评"))
                )
        );
        assertThat(labSubmissionRepository.findById(submissionId).orElseThrow().getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        )).isPresent();

        Map<String, Object> result = labService.returnReport(teacherUser(), submissionId);

        LabSubmission afterReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer afterAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        assertThat(result).containsEntry("status", "SAVED")
                .containsEntry("submissionId", submissionId)
                .containsEntry("labId", labId)
                .containsEntry("studentId", studentUser().id());
        assertThat(afterReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(afterReturn.getSubmittedAt()).isNull();
        assertThat(afterReturn.getGradedAt()).isNull();
        assertThat(afterReturn.getTotalScore()).isEqualTo(0D);
        assertThat(afterReturn.getSummaryText()).isEqualTo("待终评小结");
        assertThat(afterReturn.getTeacherComment()).isEmpty();
        assertThat(afterAnswer.getAnswerText()).isEqualTo("通过 scoring engine 复用评分策略");
        assertThat(afterAnswer.getScore()).isNull();
        assertThat(afterAnswer.getAutoScore()).isNull();
        assertThat(afterAnswer.getSuggestedScore()).isNull();
        assertThat(afterAnswer.getScoreSource()).isEqualTo("TEACHER");
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        )).isNotPresent();
    }

    @Test
    void returnAutoGradedZeroScoreReportCanBeReturned() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedFillBlankQuestionBankStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("wrong,answer", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("填空全错小结")).get("submissionId")).longValue();
        LabSubmission beforeReturn = labSubmissionRepository.findById(submissionId).orElseThrow();

        assertThat(beforeReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(beforeReturn.getTotalScore()).isEqualTo(0D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        )).isPresent();

        labService.returnReport(teacherUser(), submissionId);

        LabSubmission afterReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer afterAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();
        assertThat(afterReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(afterReturn.getSubmittedAt()).isNull();
        assertThat(afterReturn.getGradedAt()).isNull();
        assertThat(afterReturn.getTotalScore()).isEqualTo(0D);
        assertThat(afterReturn.getSummaryText()).isEqualTo("填空全错小结");
        assertThat(afterReturn.getTeacherComment()).isEmpty();
        assertThat(afterAnswer.getAnswerText()).isEqualTo("wrong,answer");
        assertThat(afterAnswer.getScore()).isNull();
        assertThat(afterAnswer.getAutoScore()).isNull();
        assertThat(afterAnswer.getSuggestedScore()).isNull();
        assertThat(afterAnswer.getScoreSource()).isEqualTo("TEACHER");
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        )).isNotPresent();
    }

    @Test
    void returnGradedReportWithoutScoreRecordSucceeds() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedFillBlankQuestionBankStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("wrong,answer", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("无汇总表小结")).get("submissionId")).longValue();
        LabSubmission gradedSubmission = labSubmissionRepository.findById(submissionId).orElseThrow();
        gradedSubmission.setSubmitStatus(SubmissionStatus.GRADED);
        labSubmissionRepository.saveAndFlush(gradedSubmission);
        scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        ).ifPresent(scoreRecordRepository::delete);

        Map<String, Object> result = labService.returnReport(teacherUser(), submissionId);

        LabSubmission afterReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(result).containsEntry("status", "SAVED");
        assertThat(afterReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(afterReturn.getSubmittedAt()).isNull();
        assertThat(afterReturn.getGradedAt()).isNull();
    }

    @Test
    void returnGradedReportAllowsStudentToResubmitAndRecreatesScoreRecord() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedFillBlankQuestionBankStep(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("wrong,answer", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("初稿小结")).get("submissionId")).longValue();
        labService.returnReport(teacherUser(), submissionId);

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("spring,boot", null));
        labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("修订小结"));

        LabSubmission resubmitted = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(resubmitted.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(resubmitted.getTotalScore()).isEqualTo(10D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(
                com.opencode.teachingplatform.grading.BusinessType.LAB, labId, studentUser().id()
        ))
                .isPresent()
                .get()
                .extracting(com.opencode.teachingplatform.analysis.entity.ScoreRecord::getScore)
                .isEqualTo(10D);
    }

    @Test
    void returnReportRejectsSavedStatusAndClosedOrDraftLab() {
        Long classId = seedClass(teacherUser().id());
        Long publishedLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long closedLabId = seedLab(classId, ActivityStatus.CLOSED, teacherUser().id());
        Long draftLabId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        Long stepId = seedStep(publishedLabId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), publishedLabId, stepId, new LabRequests.SaveStepAnswerRequest("saved only", null));
        LabSubmission savedSubmission = labSubmissionRepository.findByLabIdAndStudentId(publishedLabId, studentUser().id()).orElseThrow();

        assertThatThrownBy(() -> labService.returnReport(teacherUser(), savedSubmission.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已提交或已批改的报告可打回");

        Long closedStepId = seedCodeStepWithSnapshot(closedLabId, 1);
        Long closedSubmissionId = seedSubmittedReportOnPublishedLabThenClose(closedLabId, closedStepId, "关闭实验小结");
        assertThatThrownBy(() -> labService.returnReport(teacherUser(), closedSubmissionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验已关闭，不能打回");

        Long draftStepId = seedCodeStepWithSnapshot(draftLabId, 1);
        Long draftSubmissionId = seedSubmittedReportOnPublishedLabThenDraft(draftLabId, draftStepId, "草稿实验小结");
        assertThatThrownBy(() -> labService.returnReport(teacherUser(), draftSubmissionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验未发布，不能打回");
    }

    @Test
    void returnReportClearsPartialTeacherConfirmedScores() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long firstStepId = seedStep(labId, 1, "TEXT", "{}", "{\"keywords\":[{\"term\":\"架构\",\"weight\":10}],\"commentTemplate\":\"请教师确认\"}", 10);
        Long secondStepId = seedStep(labId, 2, "TEXT", "{}", "{\"keywords\":[{\"term\":\"分层\",\"weight\":10}],\"commentTemplate\":\"请教师确认\"}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, firstStepId, new LabRequests.SaveStepAnswerRequest("架构分析", null));
        labService.saveAnswer(studentUser(), labId, secondStepId, new LabRequests.SaveStepAnswerRequest("分层设计", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("部分确认小结")).get("submissionId")).longValue();
        LabStepAnswer firstAnswer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, firstStepId).orElseThrow();

        labService.confirmStepScore(
                teacherUser(),
                submissionId,
                new LabRequests.ConfirmStepScoreRequest(firstAnswer.getId(), 9D, "先确认第一题", false)
        );

        LabSubmission beforeReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer confirmedAnswer = labStepAnswerRepository.findById(firstAnswer.getId()).orElseThrow();

        assertThat(beforeReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(confirmedAnswer.getScore()).isEqualTo(9D);
        assertThat(confirmedAnswer.getTeacherComment()).isEqualTo("先确认第一题");

        labService.returnReport(teacherUser(), submissionId);

        LabSubmission afterReturn = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer clearedAnswer = labStepAnswerRepository.findById(firstAnswer.getId()).orElseThrow();

        assertThat(afterReturn.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(clearedAnswer.getScore()).isNull();
        assertThat(clearedAnswer.getTeacherComment()).isEmpty();
        assertThat(clearedAnswer.getScoreSource()).isEqualTo("TEACHER");
    }

    @Test
    void returnReportAllowsStudentToSaveAndResubmit() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedCodeStepWithSnapshot(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("通过 scoring engine 复用评分策略", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("初稿小结")).get("submissionId")).longValue();
        assertThat(labSubmissionRepository.findById(submissionId).orElseThrow().getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);

        labService.returnReport(teacherUser(), submissionId);

        Map<String, Object> saveResult = labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest("修订后 scoring engine 复用评分策略", null)
        );
        Map<String, Object> submitResult = labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("修订后小结"));

        LabSubmission resubmitted = labSubmissionRepository.findById(submissionId).orElseThrow();
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submissionId, stepId).orElseThrow();

        assertThat(saveResult.get("submissionStatus")).isEqualTo("SAVED");
        assertThat(submitResult.get("status")).isEqualTo("SUBMITTED");
        assertThat(resubmitted.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(resubmitted.getSubmittedAt()).isNotNull();
        assertThat(resubmitted.getSummaryText()).isEqualTo("修订后小结");
        assertThat(answer.getAnswerText()).isEqualTo("修订后 scoring engine 复用评分策略");
    }

    @Test
    void returnReportRejectsTeacherWhoDidNotCreateLab() {
        Long ownerTeacherId = teacherUser().id();
        Long coTeacherId = ownerTeacherId + 101L;
        CurrentUser coTeacher = new CurrentUser(coTeacherId, "co-teacher-return", "共班教师", UserRole.TEACHER);
        seedStudentAccount(coTeacher);

        Long classId = seedClass(ownerTeacherId);
        jdbcTemplate.update(
                """
                INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at)
                SELECT 1, '共班学科', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM school_subject WHERE school_id = 1 AND code = ?)
                """,
                "CO-TEACH-" + classId,
                "CO-TEACH-" + classId
        );
        jdbcTemplate.update(
                """
                INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_id, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', (SELECT id FROM school_subject WHERE school_id = 1 AND code = ?), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                classId,
                coTeacherId,
                "CO-TEACH-" + classId
        );
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, ownerTeacherId);
        Long stepId = seedCodeStepWithSnapshot(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("通过 scoring engine 复用评分策略", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("越权打回小结")).get("submissionId")).longValue();

        assertThatThrownBy(() -> labService.returnReport(coTeacher, submissionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该实验");
    }

    @Test
    void returnReportRejectsDuplicateReturn() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedCodeStepWithSnapshot(labId, 1);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("通过 scoring engine 复用评分策略", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest("小结")).get("submissionId")).longValue();
        assertThat(labSubmissionRepository.findById(submissionId).orElseThrow().getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);

        labService.returnReport(teacherUser(), submissionId);

        assertThatThrownBy(() -> labService.returnReport(teacherUser(), submissionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已提交或已批改的报告可打回");
    }

    private Long seedSubmittedReportOnPublishedLabThenClose(Long labId, Long stepId, String summaryText) {
        labService.changeLabStatus(teacherUser(), labId, new LabRequests.ChangeLabStatusRequest("PUBLISHED"));
        Long submissionId = seedSubmittedReport(labId, stepId, summaryText);
        labService.changeLabStatus(teacherUser(), labId, new LabRequests.ChangeLabStatusRequest("CLOSED"));
        return submissionId;
    }

    private Long seedSubmittedReportOnPublishedLabThenDraft(Long labId, Long stepId, String summaryText) {
        labService.changeLabStatus(teacherUser(), labId, new LabRequests.ChangeLabStatusRequest("PUBLISHED"));
        Long submissionId = seedSubmittedReport(labId, stepId, summaryText);
        // 现有校验已禁止“有作答后退回草稿”，此处直接改写实体状态构造 draft + 有提交的历史脏状态，
        // 用于验证 returnReport 的“实验未发布”保护分支。
        Lab draftLab = labRepository.findById(labId).orElseThrow();
        draftLab.setStatus(ActivityStatus.DRAFT);
        labRepository.saveAndFlush(draftLab);
        return submissionId;
    }

    private Long seedSubmittedReport(Long labId, Long stepId, String summaryText) {
        labService.saveAnswer(
                studentUser(),
                labId,
                stepId,
                new LabRequests.SaveStepAnswerRequest("通过 scoring engine 复用评分策略", null)
        );
        return ((Number) labService.submitLab(studentUser(), labId, new LabRequests.SubmitLabRequest(summaryText)).get("submissionId")).longValue();
    }

    @Test
    void seededPublishedEditableFillBlankLabSupportsTeacherAndStudentFlows() {
        CurrentUser seededStudent = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        List<Map<String, Object>> studentLabs = labService.listStudentLabs(seededStudent);
        Map<String, Object> studentLab = studentLabs.stream()
                .filter(item -> Long.valueOf(SEEDED_PLAYWRIGHT_LAB_ID).equals(item.get("id")))
                .findFirst()
                .orElseThrow();

        assertThat(studentLab.get("status")).isEqualTo("PUBLISHED");
        assertThat(studentLab.get("summaryRequired")).isEqualTo(true);
        assertThat(studentLab.get("submissionStatus")).isNull();

        Map<String, Object> studentDetail = labService.getStudentLabDetail(seededStudent, SEEDED_PLAYWRIGHT_LAB_ID);

        assertThat(studentDetail.get("summaryRequired")).isEqualTo(true);
        assertThat(studentDetail.get("submissionStatus")).isNull();
        assertThat(studentDetail.get("summaryText")).isEqualTo("");
        assertThat((List<Map<String, Object>>) studentDetail.get("items")).anySatisfy(item -> {
            assertThat(item.get("id")).isEqualTo(SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID);
            assertThat(item.get("questionType")).isEqualTo("FILL_BLANK");
        });

        List<Map<String, Object>> blankItems = labService.listBlankItems(teacherUser(), SEEDED_PLAYWRIGHT_LAB_ID);

        assertThat(blankItems).singleElement().satisfies(item -> {
            assertThat(item.get("id")).isEqualTo(SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID);
            assertThat(item.get("questionType")).isEqualTo("FILL_BLANK");
        });

        Map<String, Object> distribution = labService.getBlankItemAnswerDistribution(
                teacherUser(),
                SEEDED_PLAYWRIGHT_LAB_ID,
                SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID
        );

        assertThat(distribution.get("acceptedAnswers")).asList().containsExactly("Controller Service");
        assertThat((List<Map<String, Object>>) distribution.get("answerDistribution")).anySatisfy(item -> {
            assertThat(item.get("normalizedAnswer")).isEqualTo("controller service");
            assertThat(item.get("count")).isEqualTo(1L);
            assertThat(item.get("accepted")).isEqualTo(true);
        });
    }

    @Test
    void saveBlankAcceptedAnswersAllowsResavingSeededAcceptedAnswerForSameItem() {
        assertThatCode(() -> labService.saveBlankAcceptedAnswers(
                teacherUser(),
                SEEDED_PLAYWRIGHT_LAB_ID,
                new LabRequests.SaveBlankAcceptedAnswersRequest(
                        SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID,
                        List.of("Controller Service")
                )
        )).doesNotThrowAnyException();

        List<ExperimentBlankAnswerOverride> overrides = experimentBlankAnswerOverrideRepository
                .findByExperimentIdAndExperimentItemIdOrderByIdAsc(
                        SEEDED_PLAYWRIGHT_LAB_ID,
                        SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID
                );

        assertThat(overrides).singleElement().satisfies(override -> {
            assertThat(override.getAcceptedAnswer()).isEqualTo("Controller Service");
            assertThat(override.getNormalizedAnswer()).isEqualTo("controller service");
        });
    }

    @Test
    void studentLabListContainsEditableSummaryRequiredSeededExperiment() {
        CurrentUser seededStudent = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        List<Map<String, Object>> studentLabs = labService.listStudentLabs(seededStudent);

        assertThat(studentLabs).anySatisfy(item -> {
            if (Long.valueOf(SEEDED_PLAYWRIGHT_LAB_ID).equals(item.get("id"))) {
                assertThat(item.get("title")).isEqualTo("实验模块联调填空实验");
                assertThat(item.get("status")).isEqualTo("PUBLISHED");
                assertThat(item.get("summaryRequired")).isEqualTo(true);
                assertThat(item.get("submissionStatus")).isNull();
            }
        });
    }

    @Test
    void submitLabComputesPlagiarismAgainstSameLabSubmissions() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        CurrentUser firstStudent = new CurrentUser(210L, "student-plag-a", "查重学生A", UserRole.STUDENT);
        CurrentUser secondStudent = new CurrentUser(211L, "student-plag-b", "查重学生B", UserRole.STUDENT);
        seedStudentAccount(firstStudent);
        seedStudentAccount(secondStudent);
        seedMembership(classId, firstStudent.id());
        seedMembership(classId, secondStudent.id());

        String copiedText = "基于策略模式的评分引擎将可替换算法封装在独立实现中，便于横向扩展";
        labService.saveAnswer(firstStudent, labId, stepId, new LabRequests.SaveStepAnswerRequest(copiedText, null));
        labService.submitLab(firstStudent, labId, new LabRequests.SubmitLabRequest("查重第一份"));

        labService.saveAnswer(secondStudent, labId, stepId, new LabRequests.SaveStepAnswerRequest(copiedText, null));
        Long secondSubmissionId = ((Number) labService.submitLab(secondStudent, labId,
                new LabRequests.SubmitLabRequest("查重第二份")).get("submissionId")).longValue();

        LabSubmission secondSubmission = labSubmissionRepository.findById(secondSubmissionId).orElseThrow();
        // 同一实验内后提交的完整抄写必须命中高相似率
        assertThat(secondSubmission.getPlagiarismRate()).isGreaterThan(0D);
        Integer labTaskCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM plagiarism_task
                WHERE business_type = 'LAB' AND business_id = ?
                """,
                Integer.class,
                secondSubmissionId
        );
        assertThat(labTaskCount).isEqualTo(1);
    }

    @Test
    void submitLabDoesNotCompareSubmissionsFromDifferentLabs() {
        Long classId = seedClass(teacherUser().id());
        Long firstLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long secondLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long firstStepId = seedStep(firstLabId, 1, "TEXT", "{}", 10);
        Long secondStepId = seedStep(secondLabId, 1, "TEXT", "{}", 10);
        CurrentUser firstStudent = new CurrentUser(212L, "student-plag-c", "查重学生C", UserRole.STUDENT);
        CurrentUser secondStudent = new CurrentUser(213L, "student-plag-d", "查重学生D", UserRole.STUDENT);
        seedStudentAccount(firstStudent);
        seedStudentAccount(secondStudent);
        seedMembership(classId, firstStudent.id());
        seedMembership(classId, secondStudent.id());

        String sameText = "跨实验的不同题目不应互相计入查重率，候选范围必须收敛在同一实验内";
        labService.saveAnswer(firstStudent, firstLabId, firstStepId, new LabRequests.SaveStepAnswerRequest(sameText, null));
        labService.submitLab(firstStudent, firstLabId, new LabRequests.SubmitLabRequest("实验一提交"));

        labService.saveAnswer(secondStudent, secondLabId, secondStepId, new LabRequests.SaveStepAnswerRequest(sameText, null));
        Long secondSubmissionId = ((Number) labService.submitLab(secondStudent, secondLabId,
                new LabRequests.SubmitLabRequest("实验二提交")).get("submissionId")).longValue();

        assertThat(labSubmissionRepository.findById(secondSubmissionId).orElseThrow().getPlagiarismRate()).isZero();
    }

    @Test
    void submitLabDegradesPlagiarismRateWhenAnalysisFails() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());
        org.mockito.Mockito.doThrow(new IllegalStateException("查重引擎不可用"))
                .when(localPlagiarismService)
                .analyze(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());

        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("查重异常降级", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId,
                new LabRequests.SubmitLabRequest("异常降级小结")).get("submissionId")).longValue();

        // 查重失败只降级查重率，提交本身必须成功且不落查重任务
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getPlagiarismRate()).isZero();
        Integer taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plagiarism_task WHERE business_type = 'LAB' AND business_id = ?",
                Integer.class,
                submissionId
        );
        assertThat(taskCount).isZero();
    }

    @Test
    void createLabRejectsMissingOrForeignSchoolMaterial() {
        Long classId = seedClass(teacherUser().id());
        long foreignMaterialId = Math.abs(System.nanoTime());
        jdbcTemplate.update(
                """
                INSERT INTO course_material (id, title, category, description, file_path, file_name,
                                             uploader_user_id, visibility, school_id, created_at, updated_at)
                VALUES (?, '他校资料', 'DOC', NULL, '/tmp/foreign.pdf', 'foreign.pdf', 2, 'ALL', 2,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                foreignMaterialId
        );
        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_experiment", Integer.class);

        assertThatThrownBy(() -> labService.createLab(teacherUser(), new LabRequests.CreateLabRequest(
                "跨校资料实验", null, null, 1, classId, "DRAFT", foreignMaterialId, null, false, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限引用该资料");
        assertThatThrownBy(() -> labService.createLab(teacherUser(), new LabRequests.CreateLabRequest(
                "不存在资料实验", null, null, 1, classId, "DRAFT", foreignMaterialId + 999L, null, false, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("资料不存在");

        Integer after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_experiment", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void teacherReportDetailReadsPlagiarismRateFromSubmission() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedStudentAccount(studentUser());
        seedMembership(classId, studentUser().id());
        labService.saveAnswer(studentUser(), labId, stepId, new LabRequests.SaveStepAnswerRequest("教师报告查重率", null));
        Long submissionId = ((Number) labService.submitLab(studentUser(), labId,
                new LabRequests.SubmitLabRequest("报告查重小结")).get("submissionId")).longValue();
        LabSubmission submission = labSubmissionRepository.findById(submissionId).orElseThrow();
        submission.setPlagiarismRate(66.6D);
        labSubmissionRepository.saveAndFlush(submission);

        Map<String, Object> detail = labService.getTeacherReportDetail(teacherUser(), submissionId);

        assertThat(detail.get("plagiarismRate")).isEqualTo(66.6D);
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
        // 顶层与逐题查重率同源，避免前端出现两套口径
        assertThat(items).allSatisfy(item -> assertThat(item.get("plagiarismRate")).isEqualTo(66.6D));
    }

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

    private Long seedClass(Long teacherId) {
        long id = Math.abs(System.nanoTime());
        jdbcTemplate.update(
                """
                INSERT INTO class_room (id, school_id, name, code, status, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                "实验班",
                "LAB-" + id,
                "ACTIVE"
        );
        jdbcTemplate.update(
                """
                INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_id, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'SUBJECT', (SELECT id FROM school_subject WHERE school_id = 1 AND code = 'SE-ARCH'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                teacherId
        );
        return id;
    }

    private void seedMembership(Long classId, Long studentId) {
        long id = Math.abs(System.nanoTime());
        jdbcTemplate.update(
                """
                INSERT INTO class_member (id, class_id, student_user_id, joined_at, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                classId,
                studentId
        );
    }

    private void updateLabStartAt(Long labId, OffsetDateTime startAt) {
        jdbcTemplate.update("UPDATE t_experiment SET start_at = ? WHERE experiment_id = ?",
                Timestamp.from(startAt.toInstant()), labId);
    }

    private void updateLabEndAt(Long labId, OffsetDateTime endAt) {
        jdbcTemplate.update("UPDATE t_experiment SET end_at = ? WHERE experiment_id = ?",
                Timestamp.from(endAt.toInstant()), labId);
    }

    private void updateLabTimeWindow(Long labId, OffsetDateTime startAt, OffsetDateTime endAt) {
        jdbcTemplate.update("UPDATE t_experiment SET start_at = ?, end_at = ? WHERE experiment_id = ?",
                Timestamp.from(startAt.toInstant()), Timestamp.from(endAt.toInstant()), labId);
    }

    private void seedStudentAccount(CurrentUser student) {
        jdbcTemplate.update(
                """
                MERGE INTO sys_user (id, username, password_hash, display_name, role, status, must_change_password, school_id, created_at, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                student.id(),
                student.username(),
                "{noop}123456",
                student.displayName(),
                student.role().name(),
                "ACTIVE",
                false
        );
    }

    private Long seedLab(Long classId, ActivityStatus status, Long teacherId) {
        long id = generatedExperimentId++;
        int state = switch (status) {
            case DRAFT -> 0;
            case PUBLISHED -> 1;
            case CLOSED -> 2;
        };
        jdbcTemplate.update(
                """
                 INSERT INTO t_experiment (experiment_id, experiment_no, experiment_name, experiment_type, instruction_type,
                                           experiment_requirement, experiment_content, state, status, material_id,
                                           class_id, created_by, score_visibility_mode, score_released, summary_required,
                                           created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                Math.toIntExact(id),
                "实验-" + id,
                1,
                "1",
                "desc",
                "desc",
                state,
                status.name(),
                null,
                classId,
                teacherId,
                "AFTER_TEACHER_CONFIRM",
                false,
                false
        );
        return id;
    }

    private Long seedFillBlankQuestionBankStep(Long labId, int stepNo) {
        long questionId = Math.abs(System.nanoTime());
        jdbcTemplate.update(
                """
                INSERT INTO question_bank (id, code, type, stem, difficulty, default_score, answer_json, scoring_config_json, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                questionId,
                "QB-" + questionId,
                "FILL_BLANK",
                "填空题",
                "MEDIUM",
                10,
                "{\"blanks\":[{\"answers\":[\"spring\"]},{\"answers\":[\"boot\"]}]}",
                "{\"blanks\":[{\"answers\":[\"spring\"],\"score\":4},{\"answers\":[\"boot\"],\"score\":6}],\"ignoreCase\":true}",
                teacherUser().id()
        );

        Long stepId = seedStep(
                labId,
                stepNo,
                "FILL_BLANK",
                "{}",
                null,
                10,
                questionId,
                """
                {"questionType":"FILL_BLANK","questionId":%d,"score":10,"scoringConfig":{"blanks":[{"answers":["spring"],"score":4},{"answers":["boot"],"score":6}],"ignoreCase":true}}
                """.formatted(questionId)
        );
        return stepId;
    }

    private Long seedCodeStepWithSnapshot(Long labId, int stepNo) {
        return seedStep(
                labId,
                stepNo,
                "CODE",
                "{}",
                null,
                20,
                null,
                """
                {"questionType":"CODE","score":20,"scoringConfig":{"keywords":[{"term":"scoring","weight":10},{"term":"复用","weight":8}],"minLength":5}}
                """
        );
    }

    private Long seedStep(Long labId, int stepNo) {
        return seedStep(labId, stepNo, "TEXT", "{}", "{}", 10);
    }

    private Long seedStep(Long labId, int stepNo, String questionType, String answerConfigJson, int stepScore) {
        return seedStep(labId, stepNo, questionType, answerConfigJson, answerConfigJson, stepScore);
    }

    private Long seedStep(Long labId, int stepNo, String questionType, String answerConfigJson, String scoringConfigJson, int stepScore) {
        return seedStep(labId, stepNo, questionType, answerConfigJson, scoringConfigJson, stepScore, null, "{\"legacyQuestionType\":\"" + questionType + "\"}");
    }

    private Long seedStep(Long labId,
                          int stepNo,
                          String questionType,
                          String answerConfigJson,
                          String scoringConfigJson,
                          int stepScore,
                          Long questionId,
                          String questionSnapshotJson) {
        long id = Math.abs(System.nanoTime());
        int questionTypeCode = switch (questionType) {
            case "SINGLE_CHOICE" -> 1;
            case "MULTIPLE_CHOICE" -> 2;
            case "FILL_BLANK" -> 3;
            case "TRUE_FALSE" -> 4;
            case "CODE" -> 6;
            default -> 5;
        };
        jdbcTemplate.update(
                """
                 INSERT INTO t_experiment_item (id, experiment_id, experiment_item_no, experiment_item_name,
                                                experiment_item_type, experiment_item_content, experiment_item_answer,
                                                experiment_item_score, state, question_type, answer_config_json,
                                                scoring_config_json, question_id, question_snapshot_json, editor_language, allow_paste,
                                                created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                 """,
                 id,
                 labId,
                 stepNo,
                "步骤" + stepNo,
                questionTypeCode,
                "content",
                answerConfigJson,
                stepScore,
                 1,
                 questionType,
                 answerConfigJson,
                 scoringConfigJson,
                 questionId,
                 questionSnapshotJson,
                 "CODE".equals(questionType) ? "JAVA" : "TEXT",
                 true
         );
         return id;
     }

    /**
     * 通过真实学生流程（保存答案 + 正式提交）为 PUBLISHED 实验造出一条提交行。
     *
     * <p>复用 {@link #seedSubmittedReport}：只用一条 TEXT 主观题，提交后停留在 SUBMITTED，
     * 不产生成绩记录，便于「已有学生作答」类守卫用例专注校验提交数分支。</p>
     *
     * @return 该实验下唯一题项的 id，供题项更新/删除用例复用
     */
    private Long seedSubmittedStudent(Long classId, Long labId) {
        Long stepId = seedStep(labId, 1, "TEXT", "{}", 10);
        seedMembership(classId, studentUser().id());
        seedSubmittedReport(labId, stepId, "实验小结");
        return stepId;
    }

    private Long seedSubmissionAnswer(Long labId, Long stepId, Long studentId, String answerText) {
        seedStudentAccount(new CurrentUser(studentId, "student-" + studentId, "学生" + studentId, UserRole.STUDENT));
        Long submissionId = Math.abs(System.nanoTime());
        Long answerId = Math.abs(System.nanoTime());
        jdbcTemplate.update(
                """
                INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, total_score, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                submissionId,
                labId,
                studentId,
                SubmissionStatus.SUBMITTED.name(),
                0D
        );
        jdbcTemplate.update(
                """
                INSERT INTO experiment_answer (id, experiment_submission_id, experiment_item_id, answer_text, score_source, editor_language, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                answerId,
                submissionId,
                stepId,
                answerText,
                "TEACHER",
                "TEXT"
        );
        return submissionId;
    }

    @Test
    void createStepDoesNotWriteQuestionBankRows() {
        Long classId = seedClass(teacherUser().id());
        Long labId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_bank", Integer.class);

        labService.createStep(teacherUser(), labId, new LabRequests.CreateStepRequest(
                1, "选择题题项", "SINGLE_CHOICE", "以下哪项是策略模式的核心？",
                "{\"options\":[{\"key\":\"A\",\"label\":\"封装可替换算法\"},{\"key\":\"B\",\"label\":\"减少注释\"}],\"correctAnswer\":[\"A\"]}",
                10, true));

        Integer after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_bank", Integer.class);
        // 保存题项只落实验题项，题库写入必须由教师显式复制触发
        assertThat(after).isEqualTo(before);
    }

    @Test
    void copyLabPreservesHistoricalQuestionReferenceOnCopiedSteps() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        String snapshotJson = "{\"type\":\"SINGLE\",\"stem\":\"历史题目\"}";
        seedStep(sourceLabId, 1, "SINGLE_CHOICE", "{}", "{}", 10, 9001L, snapshotJson);

        Map<String, Object> view = labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null));
        Long newLabId = ((Number) view.get("id")).longValue();

        LabStep copied = labStepRepository.findByLabIdOrderByStepNoAsc(newLabId).getFirst();
        // copyStep 继续复制历史 questionId / questionSnapshotJson，不清空旧字段
        assertThat(copied.getQuestionId()).isEqualTo(9001L);
        assertThat(copied.getQuestionSnapshotJson()).isEqualTo(snapshotJson);
    }

    @Test
    void copyLabDuplicatesConfigurationAndItems() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long sourceStepId1 = seedStep(sourceLabId, 1, "TEXT", "{}", 10);
        Long sourceStepId2 = seedStep(sourceLabId, 2, "CODE", "{}", 20);

        Map<String, Object> view = labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null));

        Long newLabId = ((Number) view.get("id")).longValue();
        assertThat(newLabId).isNotEqualTo(sourceLabId);
        assertThat(view.get("title")).isEqualTo("实验-" + sourceLabId + "（副本）");
        assertThat(view.get("status")).isEqualTo("DRAFT");
        assertThat(view.get("stepCount")).isEqualTo(2);

        List<LabStep> copiedSteps = labStepRepository.findByLabIdOrderByStepNoAsc(newLabId);
        assertThat(copiedSteps).hasSize(2);
        assertThat(copiedSteps.stream().map(LabStep::getId)).doesNotContain(sourceStepId1, sourceStepId2);

        LabStep first = copiedSteps.get(0);
        assertThat(first.getStepNo()).isEqualTo(1);
        assertThat(first.getQuestionType()).isEqualTo("TEXT");
        assertThat(first.getAnswerConfigJson()).isEqualTo("{}");
        assertThat(first.getExperimentItemAnswer()).isEqualTo("{}");
        assertThat(first.getStepScore()).isEqualTo(10);

        LabStep second = copiedSteps.get(1);
        assertThat(second.getStepNo()).isEqualTo(2);
        assertThat(second.getQuestionType()).isEqualTo("CODE");
        assertThat(second.getEditorLanguage()).isEqualTo("JAVA");
        assertThat(second.getStepScore()).isEqualTo(20);
    }

    @Test
    void copyLabDoesNotCopyStudentSubmissionsOrAnswers() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long sourceStepId = seedStep(sourceLabId, 1);
        seedSubmissionAnswer(sourceLabId, sourceStepId, 20260001L, "学生答案");

        Map<String, Object> view = labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null));
        Long newLabId = ((Number) view.get("id")).longValue();

        Integer submissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experiment_submission WHERE experiment_id = ?", Integer.class, newLabId);
        assertThat(submissionCount).isZero();
        Integer answerCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM experiment_answer a
                JOIN experiment_submission s ON a.experiment_submission_id = s.id
                WHERE s.experiment_id = ?
                """,
                Integer.class, newLabId);
        assertThat(answerCount).isZero();
    }

    @Test
    void copyLabResetsRuntimeFields() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        seedStep(sourceLabId, 1);
        jdbcTemplate.update(
                """
                UPDATE t_experiment SET score_released = TRUE, start_at = CURRENT_TIMESTAMP,
                       end_at = TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP), score_visibility_mode = 'AFTER_DEADLINE'
                WHERE experiment_id = ?
                """,
                sourceLabId);

        Map<String, Object> view = labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null));
        Long newLabId = ((Number) view.get("id")).longValue();

        Lab copied = labRepository.findById(newLabId).orElseThrow();
        assertThat(copied.isScoreReleased()).isFalse();
        assertThat(copied.getStartAt()).isNull();
        assertThat(copied.getEndAt()).isNull();
        // 副本没有截止时间，沿用「截止后可见」会让模式与时间窗自相矛盾，必须回落为教师确认后可见
        assertThat(copied.getScoreVisibilityMode()).isEqualTo(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        assertThat(copied.getCreatedBy()).isEqualTo(teacherUser().id());
    }

    @Test
    void copyLabRejectsSourceNotOwnedByTeacher() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        CurrentUser otherTeacher = new CurrentUser(99L, "t9999", "其他教师", UserRole.TEACHER);

        assertThatThrownBy(() -> labService.copyLab(otherTeacher, sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该实验");
    }

    @Test
    void copyLabRejectsTargetClassNotAssigned() {
        Long ownedClassId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(ownedClassId, ActivityStatus.DRAFT, teacherUser().id());
        Long unassignedClassId = seedClass(99L);

        assertThatThrownBy(() -> labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(unassignedClassId, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该资源");
    }

    @Test
    void copyLabMapsBlankAcceptedAnswersToNewItems() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.PUBLISHED, teacherUser().id());
        Long sourceStepId = seedStep(sourceLabId, 1, "FILL_BLANK", "{}", 10);
        ExperimentBlankAnswerOverride override = new ExperimentBlankAnswerOverride();
        override.setExperimentId(sourceLabId);
        override.setExperimentItemId(sourceStepId);
        override.setAcceptedAnswer("spring");
        override.setNormalizedAnswer("spring");
        override.setBlankAnswersJson("[\"spring\"]");
        override.setCreatedBy(teacherUser().id());
        experimentBlankAnswerOverrideRepository.saveAndFlush(override);

        Map<String, Object> view = labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null));
        Long newLabId = ((Number) view.get("id")).longValue();

        List<ExperimentBlankAnswerOverride> copied =
                experimentBlankAnswerOverrideRepository.findByExperimentIdOrderByIdAsc(newLabId);
        assertThat(copied).hasSize(1);
        ExperimentBlankAnswerOverride item = copied.get(0);
        assertThat(item.getExperimentItemId()).isNotEqualTo(sourceStepId);
        assertThat(item.getAcceptedAnswer()).isEqualTo("spring");
        assertThat(item.getNormalizedAnswer()).isEqualTo("spring");
        assertThat(item.getBlankAnswersJson()).isEqualTo("[\"spring\"]");
        assertThat(item.getCreatedBy()).isEqualTo(teacherUser().id());

        List<LabStep> newSteps = labStepRepository.findByLabIdOrderByStepNoAsc(newLabId);
        assertThat(newSteps).hasSize(1);
        assertThat(item.getExperimentItemId()).isEqualTo(newSteps.get(0).getId());
    }

    @Test
    void copyLabGeneratesDefaultTitleWithin30Chars() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());
        String longTitle = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八";
        jdbcTemplate.update("UPDATE t_experiment SET experiment_name = ? WHERE experiment_id = ?",
                longTitle, sourceLabId);

        Map<String, Object> view = labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, null));

        String title = (String) view.get("title");
        assertThat(title).hasSize(30);
        assertThat(title).endsWith("（副本）");
    }

    @Test
    void copyLabRejectsPublishedStatusWithoutItems() {
        Long classId = seedClass(teacherUser().id());
        Long sourceLabId = seedLab(classId, ActivityStatus.DRAFT, teacherUser().id());

        assertThatThrownBy(() -> labService.copyLab(teacherUser(), sourceLabId,
                new LabRequests.CopyLabRequest(classId, null, "PUBLISHED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实验尚未配置题项，无法发布");
    }

    private CurrentUser studentUser() {
        return new CurrentUser(200L, "student-test", "测试学生", UserRole.STUDENT);
    }

    private CurrentUser teacherUser() {
        return new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
    }

    /**
     * 为一个报告用例临时建一个学生账号，返回学生 id。
     *
     * <p>用户名带 {@code tag} 便于在断言中识别，显示名统一以「关键词学生」开头，
     * 供 keyword 走学生姓名命中分支的用例使用。</p>
     */
    private Long seedReportStudent(String tag) {
        long id = generatedReportStudentId++;
        seedStudentAccount(new CurrentUser(id, "kw-" + tag + "-" + id, "关键词学生-" + tag, UserRole.STUDENT));
        return id;
    }

    /**
     * 直接插入一条实验提交，返回提交 id。
     *
     * <p>只建提交行即可覆盖报告队列 / 统计的查询链路，不需要构造题项与答案。</p>
     */
    private Long seedReportSubmission(Long labId, Long studentId, SubmissionStatus status) {
        long id = generatedReportSubmissionId++;
        jdbcTemplate.update(
                """
                INSERT INTO experiment_submission (id, experiment_id, student_id, submit_status, total_score, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                labId,
                studentId,
                status.name(),
                status == SubmissionStatus.GRADED ? 90D : 0D
        );
        return id;
    }

    private long longValue(Map<String, Object> source, String key) {
        return ((Number) source.get(key)).longValue();
    }
}
