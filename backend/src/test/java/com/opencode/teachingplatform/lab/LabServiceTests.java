package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({LabService.class, LabScoringSupport.class, LabServiceTests.TestBeans.class})
@ActiveProfiles("test")
class LabServiceTests {

    @TempDir
    static Path storageRoot;

    private static final long JS_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;
    private static final long SEEDED_PLAYWRIGHT_LAB_ID = 1003L;
    private static final long SEEDED_PLAYWRIGHT_FILL_BLANK_STEP_ID = 2005L;
    private static int generatedExperimentId = 6000;

    @Autowired
    private LabService labService;

    @Autowired
    private LabRepository labRepository;

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

        assertThat(result.get("path")).asString().matches("lab-answers/\\d{4}-\\d{2}-\\d{2}/.+");
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
                "UPDATE class_room SET teacher_user_id = ? WHERE id = ?",
                coTeacherId,
                classId
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
    void teacherCanUpdateOwnedLab() {
        Long classId = seedClass(1L);
        Long labId = seedLab(classId, ActivityStatus.DRAFT, 1L);

        Map<String, Object> result = labService.updateLab(
                teacherUser(),
                labId,
                new LabRequests.UpdateLabRequest("更新后的实验", "新的说明", "新的实验内容", 2, classId, "PUBLISHED", null, null, true)
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
                new LabRequests.UpdateLabRequest("越权更新", "desc", "content", 1, otherTeacherClassId, "DRAFT", null, null, false)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限访问该班级");
    }

    @Test
    void teacherCanReadSeededLabReports() {
        List<Map<String, Object>> reports = labService.listTeacherReports(teacherUser(), new LabRequests.TeacherLabReportQuery(null, null));

        assertThat(reports).isNotEmpty();
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
        List<Map<String, Object>> reports = labService.listTeacherReports(
                teacherUser(),
                new LabRequests.TeacherLabReportQuery("20260001", "GRADED")
        );

        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().get("labTitle")).isEqualTo("分层架构分析实验");
        assertThat(reports.getFirst().get("studentNo")).isEqualTo("20260001");
        assertThat(reports.getFirst().get("submitStatus")).isEqualTo("GRADED");
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
                new LabRequests.CreateLabRequest("自动初始化实验", "自动初始化说明", "自动初始化内容", 1, classId, "DRAFT", null, null, true)
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
        assertThat(savedAnswerBefore.getScore()).isEqualTo(0D);
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
        assertThat(targetAnswerAfter.getScore()).isEqualTo(10D);
        assertThat(targetAnswerAfter.getAutoScore()).isEqualTo(10D);
        assertThat(targetAnswerAfter.getScoreSource()).isEqualTo("AUTO");
        assertThat(otherAnswerAfter.getScore()).isEqualTo(10D);
        assertThat(savedAnswerAfter.getScore()).isEqualTo(10D);
        assertThat(savedAnswerAfter.getAutoScore()).isEqualTo(10D);
        assertThat(externalAnswerAfter.getScore()).isEqualTo(6D);
        assertThat(submissionAfter.getTotalScore()).isEqualTo(20D);
        assertThat(submissionAfter.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(savedSubmissionAfter.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(savedSubmissionAfter.getTotalScore()).isEqualTo(10D);
        assertThat(savedSubmissionAfter.getGradedAt()).isNull();
        assertThat(externalSubmissionAfter.getTotalScore()).isEqualTo(6D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(com.opencode.teachingplatform.common.enums.BusinessType.LAB, labId, studentUser().id()))
                .isPresent()
                .get()
                .extracting(com.opencode.teachingplatform.analysis.entity.ScoreRecord::getScore)
                .isEqualTo(20D);
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(com.opencode.teachingplatform.common.enums.BusinessType.LAB, labId, savedStudent.id()))
                .isNotPresent();
        assertThat(scoreRecordRepository.findByBusinessTypeAndBusinessIdAndStudentId(com.opencode.teachingplatform.common.enums.BusinessType.LAB, otherLabId, otherStudent.id()))
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
        assertThat(steps).singleElement().satisfies(step -> assertThat(step.get("answerText")).isEqualTo("newest answer"));
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
                INSERT INTO class_room (id, name, code, teacher_user_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                "实验班",
                "LAB-" + id,
                teacherId,
                "ACTIVE"
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

    private void seedStudentAccount(CurrentUser student) {
        jdbcTemplate.update(
                """
                MERGE INTO sys_user (id, username, password_hash, display_name, role, status, must_change_password, created_at, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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

    private CurrentUser studentUser() {
        return new CurrentUser(200L, "student-test", "测试学生", UserRole.STUDENT);
    }

    private CurrentUser teacherUser() {
        return new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
    }
}
