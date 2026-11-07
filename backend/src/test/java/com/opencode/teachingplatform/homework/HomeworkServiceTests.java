package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.clazz.ClassStatus;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.homework.dto.HomeworkRequests;
import com.opencode.teachingplatform.homework.entity.HomeworkAnswer;
import com.opencode.teachingplatform.homework.entity.HomeworkQuestion;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.entity.HomeworkSubmission;
import com.opencode.teachingplatform.homework.repository.HomeworkAnswerRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkQuestionRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkSubmissionRepository;
import com.opencode.teachingplatform.homework.service.HomeworkService;
import com.opencode.teachingplatform.homework.service.HomeworkSubmissionClaimService;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.strategy.FillBlankScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.MultipleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SingleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SubjectiveRecommendationStrategy;
import com.opencode.teachingplatform.grading.strategy.TrueFalseScoringStrategy;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.plagiarism.service.LocalPlagiarismService;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        HomeworkService.class,
        com.opencode.teachingplatform.homework.service.HomeworkConfigService.class,
        com.opencode.teachingplatform.homework.service.HomeworkAnswerService.class,
        com.opencode.teachingplatform.homework.service.HomeworkReportService.class,
        com.opencode.teachingplatform.homework.service.HomeworkServiceSupport.class,
        HomeworkServiceTests.JacksonTestConfig.class,
        com.opencode.teachingplatform.assessment.service.QuestionContentValidator.class,
        com.opencode.teachingplatform.clazz.service.ClassAccessService.class,
        com.opencode.teachingplatform.grading.service.ScoreRecordWriter.class,
        com.opencode.teachingplatform.common.infra.audit.AuditLogService.class,
        LocalPlagiarismService.class,
        com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter.class,
        ScoringEngine.class,
        SingleChoiceScoringStrategy.class,
        MultipleChoiceScoringStrategy.class,
        TrueFalseScoringStrategy.class,
        FillBlankScoringStrategy.class,
        SubjectiveRecommendationStrategy.class
})
class HomeworkServiceTests {

    /**
     * DataJpaTest 切片默认不加载 Jackson，这里补一个 ObjectMapper 供内容校验器使用。
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class JacksonTestConfig {

        @org.springframework.context.annotation.Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.opencode.teachingplatform.homework.service.HomeworkServiceSupport homeworkServiceSupport;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private HomeworkSubmissionRepository homeworkSubmissionRepository;

    @Autowired
    private HomeworkQuestionRepository homeworkQuestionRepository;

    @Autowired
    private HomeworkAnswerRepository homeworkAnswerRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private LocalPlagiarismService localPlagiarismService;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    /**
     * 独立事务的领取器在 @DataJpaTest 里看不到测试事务内未提交的种子数据，
     * 因此替换为「同事务委托仓储」的 stub，真实并发由 @SpringBootTest 用例覆盖。
     */
    @MockBean
    private HomeworkSubmissionClaimService homeworkSubmissionClaimService;

    @BeforeEach
    void stubSubmissionClaim() {
        given(homeworkSubmissionClaimService.claim(anyLong(), anyLong()))
                .willAnswer(invocation -> {
                    Long homeworkId = invocation.getArgument(0);
                    Long studentId = invocation.getArgument(1);
                    return homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homeworkId, studentId)
                            .map(HomeworkSubmission::getSubmitStatus)
                            .orElseGet(() -> {
                                HomeworkSubmission created = new HomeworkSubmission();
                                created.setHomeworkId(homeworkId);
                                created.setStudentId(studentId);
                                created.setSubmitStatus(SubmissionStatus.SAVED);
                                created.setPlagiarismRate(0D);
                                created.setTotalScore(0D);
                                homeworkSubmissionRepository.saveAndFlush(created);
                                return SubmissionStatus.SAVED;
                            });
                });
    }

    @Test
    void homeworkSchemaSupportsWholeSubmissionWorkflow() {
        List<String> submissionColumns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'HOMEWORK_SUBMISSION'",
                String.class
        );
        List<String> homeworkColumns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'HOMEWORK'",
                String.class
        );
        Integer submissionUniqueConstraintCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_NAME = 'HOMEWORK_SUBMISSION'
                  AND CONSTRAINT_TYPE = 'UNIQUE'
                """,
                Integer.class
        );

        assertThat(submissionColumns)
                .contains("ANSWER_TEXT", "ANSWER_FILE_PATH", "PLAGIARISM_RATE", "TOTAL_SCORE", "TEACHER_COMMENT");
        assertThat(homeworkColumns)
                .contains("CLASS_ID", "START_AT", "DUE_AT", "ATTACHMENT_PATH", "CREATED_BY", "STATUS");
        assertThat(submissionUniqueConstraintCount).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    void homeworkSeedDataSupportsPublishedHomeworkAndSubmittedDemo() {
        Integer teacherCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 't9001'",
                Integer.class
        );
        Integer studentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = '20260001'",
                Integer.class
        );
        Integer classCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM class_room WHERE code = 'SE2026-1'",
                Integer.class
        );
        Integer publishedHomeworkCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM homework h
                WHERE h.created_by = 1
                  AND h.status = 'PUBLISHED'
                """,
                Integer.class
        );

        Integer submittedHomeworkCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM homework_submission hs
                WHERE hs.student_id = 2
                  AND hs.submit_status = 'SUBMITTED'
                """,
                Integer.class
        );

        assertThat(teacherCount).isEqualTo(1);
        assertThat(studentCount).isEqualTo(1);
        assertThat(classCount).isEqualTo(1);
        assertThat(publishedHomeworkCount).isNotNull().isGreaterThanOrEqualTo(1);
        assertThat(submittedHomeworkCount).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    void teacherCanCreateAndPublishHomework() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        Map<String, Object> created = homeworkService.createHomework(
                teacher,
                new HomeworkRequests.CreateHomeworkRequest(
                        "分层架构作业",
                        "完成分层设计说明",
                        1L,
                        "PUBLISHED",
                        ScoreVisibilityMode.IMMEDIATE,
                        null,
                        null,
                        null
                )
        );

        Homework saved = homeworkRepository.findById(Long.valueOf(String.valueOf(created.get("id")))).orElseThrow();
        assertThat(created.get("status")).isEqualTo("PUBLISHED");
        assertThat(created.get("classId")).isEqualTo(1L);
        assertThat(saved.getCreatedBy()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
        assertThat(saved.getScoreVisibilityMode()).isEqualTo(ScoreVisibilityMode.IMMEDIATE);
    }

    @Test
    void studentOnlySeesPublishedHomeworksForOwnClasses() {
        jdbcTemplate.update(
                "INSERT INTO class_room (id, school_id, name, code, status, created_at, updated_at) VALUES (?, 1, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                99L, "SE2026-2", "SE2026-2", ClassStatus.ACTIVE.name()
        );
        jdbcTemplate.update(
                "INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at) VALUES (1, '测试学科', 'TEST-SUBJ', 'ACTIVE', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())"
        );
        jdbcTemplate.update(
                "INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_id, assigned_at, created_at, updated_at) VALUES (?, 1, 'SUBJECT', (SELECT id FROM school_subject WHERE school_id = 1 AND code = 'TEST-SUBJ'), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                99L
        );

        Homework publishedVisible = createHomework(1L, 1L, "可见作业", ActivityStatus.PUBLISHED);
        createHomework(1L, 1L, "草稿作业", ActivityStatus.DRAFT);
        createHomework(1L, 99L, "其他班级作业", ActivityStatus.PUBLISHED);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        List<Map<String, Object>> items = homeworkService.listStudentHomeworks(student);

        assertThat(items).isNotEmpty();
        assertThat(items)
                .extracting(item -> item.get("title"))
                .contains("可见作业")
                .doesNotContain("草稿作业", "其他班级作业");
        assertThat(items)
                .allSatisfy(item -> {
                    assertThat(item.get("classId")).isEqualTo(1L);
                    assertThat(item.get("status")).isEqualTo("PUBLISHED");
                });
        assertThat(items)
                .anySatisfy(item -> {
                    assertThat(item.get("className")).isNotNull();
                    assertThat(item.get("remainingMinutes")).isNotNull();
                });
        assertThat(items)
                .anySatisfy(item -> assertThat(item.get("id")).isEqualTo(publishedVisible.getId()));
    }

    @Test
    void teacherCanGradeHomeworkSubmission() {
        Homework homework = createHomework(1L, 1L, "待批改作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setAnswerText("学生提交内容");
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setPlagiarismRate(15D);
        submission.setTotalScore(0D);
        submission = homeworkSubmissionRepository.save(submission);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Map<String, Object> graded = homeworkService.gradeSubmission(
                teacher,
                submission.getId(),
                new HomeworkRequests.GradeHomeworkRequest(88D, "论述完整")
        );

        HomeworkSubmission saved = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(graded.get("graded")).isEqualTo(true);
        assertThat(graded.get("totalScore")).isEqualTo(88D);
        assertThat(saved.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(saved.getTeacherComment()).isEqualTo("论述完整");
        assertThat(saved.getTotalScore()).isEqualTo(88D);
        assertThat(saved.getGradedAt()).isNotNull();
    }

    @Test
    void teacherCanGetSubmissionDetail() {
        Homework homework = createHomework(1L, 1L, "详情作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setAnswerText("学生提交详情内容");
        submission.setAnswerFilePath("/uploads/homework/detail.docx");
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setPlagiarismRate(66.67D);
        submission.setTotalScore(0D);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);
        jdbcTemplate.update(
                "INSERT INTO plagiarism_task (business_type, business_id, student_id, status, similarity_rate, top_match_summary, raw_result_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                "HOMEWORK", savedSubmission.getId(), 2L, "COMPLETED", 66.67D, "最高相似来源: 历史作业#1 (66.67%)", "{\"algorithm\":\"jaccard\"}"
        );

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Map<String, Object> detail = homeworkService.getTeacherSubmissionDetail(teacher, savedSubmission.getId());

        assertThat(detail.get("id")).isEqualTo(savedSubmission.getId());
        assertThat(detail.get("homeworkId")).isEqualTo(homework.getId());
        assertThat(detail.get("className")).isNotNull().asString().isNotBlank();
        assertThat(detail.get("studentUsername")).isEqualTo("20260001");
        assertThat(detail.get("studentNo")).isEqualTo("20260001");
        assertThat(detail.get("submitStatus")).isEqualTo("SUBMITTED");
        assertThat(detail.get("answerFilePath")).isEqualTo("/uploads/homework/detail.docx");
        assertThat(detail.get("topMatchSummary")).isEqualTo("最高相似来源: 历史作业#1 (66.67%)");
    }

    @Test
    void studentHomeworkDetailIncludesQuestionStructureAndHidesScoreUntilTeacherConfirm() {
        Homework homework = createHomework(1L, 1L, "题目化详情作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setTotalScore(12D);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);

        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(savedSubmission.getId());
        answer.setHomeworkQuestionId(question.getId());
        answer.setAnswerText("strategy 提升扩展性");
        answer.setSuggestedScore(12D);
        answer.setScore(null);
        answer.setScoreSource("RECOMMENDED");
        homeworkAnswerRepository.save(answer);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());

        assertThat(detail.get("totalScore")).isNull();
        assertThat(detail.get("questions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Map.class))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.get("stem")).isEqualTo("请说明策略模式的价值");
                    Map<String, Object> answerView = (Map<String, Object>) item.get("answer");
                    assertThat(answerView.get("suggestedScore")).isNull();
                    assertThat(answerView.get("score")).isNull();
                });
    }

    @Test
    void studentHomeworkListHidesTotalScoreUntilTeacherConfirm() {
        Homework homework = createHomework(1L, 1L, "列表隐藏分数作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setTotalScore(88D);
        homeworkSubmissionRepository.save(submission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        List<Map<String, Object>> items = homeworkService.listStudentHomeworks(student);

        assertThat(items).anySatisfy(item -> {
            if (homework.getId().equals(item.get("id"))) {
                assertThat(item.get("totalScore")).isNull();
            }
        });
    }

    @Test
    void teacherSubmissionDetailIncludesQuestionAnswerItems() {
        Homework homework = createHomework(1L, 1L, "题目化教师详情作业", ActivityStatus.PUBLISHED);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);

        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(savedSubmission.getId());
        answer.setHomeworkQuestionId(question.getId());
        answer.setAnswerText("strategy 提升扩展性");
        answer.setSuggestedScore(12D);
        answer.setScore(null);
        answer.setAutoScore(null);
        answer.setJudgeDetail("matched=[strategy]");
        answer.setScoreSource("RECOMMENDED");
        homeworkAnswerRepository.save(answer);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Map<String, Object> detail = homeworkService.getTeacherSubmissionDetail(teacher, savedSubmission.getId());

        assertThat(detail.get("questions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Map.class))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.get("stem")).isEqualTo("请说明策略模式的价值");
                    Map<String, Object> answerView = (Map<String, Object>) item.get("answer");
                    assertThat(answerView.get("suggestedScore")).isEqualTo(12D);
                    assertThat(answerView.get("judgeDetail")).isEqualTo("matched=[strategy]");
                });
    }

    @Test
    void teacherCanScoreSingleHomeworkAnswer() {
        Homework homework = createHomework(1L, 1L, "教师单题评分作业", ActivityStatus.PUBLISHED);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);

        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(savedSubmission.getId());
        answer.setHomeworkQuestionId(question.getId());
        answer.setAnswerText("strategy 提升扩展性");
        answer.setSuggestedScore(12D);
        answer.setScoreSource("RECOMMENDED");
        HomeworkAnswer savedAnswer = homeworkAnswerRepository.save(answer);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Map<String, Object> result = homeworkService.gradeAnswer(
                teacher,
                savedAnswer.getId(),
                new HomeworkRequests.GradeHomeworkAnswerRequest(14D, "老师确认", false)
        );

        HomeworkAnswer updated = homeworkAnswerRepository.findById(savedAnswer.getId()).orElseThrow();
        HomeworkSubmission updatedSubmission = homeworkSubmissionRepository.findById(savedSubmission.getId()).orElseThrow();
        Map<String, Object> scoreRecord = jdbcTemplate.queryForMap(
                "SELECT score FROM score_record WHERE business_type = ? AND business_id = ? AND student_id = ?",
                "HOMEWORK", homework.getId(), savedSubmission.getStudentId()
        );
        assertThat(result.get("answerId")).isEqualTo(savedAnswer.getId());
        assertThat(updated.getScore()).isEqualTo(14D);
        assertThat(updated.getTeacherComment()).isEqualTo("老师确认");
        assertThat(updated.isAcceptedAutoScore()).isFalse();
        assertThat(updated.getScoreSource()).isEqualTo("TEACHER");
        assertThat(updatedSubmission.getTotalScore()).isEqualTo(14D);
        assertThat(updatedSubmission.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(updatedSubmission.getGradedAt()).isNotNull();
        assertThat(((Number) scoreRecord.get("score")).doubleValue()).isEqualTo(14D);
    }

    @Test
    void teacherCanAcceptSuggestedScoreForHomeworkAnswer() {
        Homework homework = createHomework(1L, 1L, "教师采纳推荐分作业", ActivityStatus.PUBLISHED);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);

        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(savedSubmission.getId());
        answer.setHomeworkQuestionId(question.getId());
        answer.setAnswerText("strategy 提升扩展性");
        answer.setSuggestedScore(12D);
        answer.setScoreSource("RECOMMENDED");
        HomeworkAnswer savedAnswer = homeworkAnswerRepository.save(answer);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        homeworkService.gradeAnswer(
                teacher,
                savedAnswer.getId(),
                new HomeworkRequests.GradeHomeworkAnswerRequest(null, "采纳推荐分", true)
        );

        HomeworkAnswer updated = homeworkAnswerRepository.findById(savedAnswer.getId()).orElseThrow();
        HomeworkSubmission updatedSubmission = homeworkSubmissionRepository.findById(savedSubmission.getId()).orElseThrow();
        assertThat(updated.getScore()).isEqualTo(12D);
        assertThat(updated.getTeacherComment()).isEqualTo("采纳推荐分");
        assertThat(updated.isAcceptedAutoScore()).isTrue();
        assertThat(updated.getScoreSource()).isEqualTo("AUTO_ACCEPTED");
        assertThat(updatedSubmission.getTotalScore()).isEqualTo(12D);
        assertThat(updatedSubmission.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(updatedSubmission.getGradedAt()).isNotNull();
    }

    @Test
    void afterDeadlineModeDoesNotShowScoreBeforeDueTime() {
        Homework homework = createHomework(1L, 1L, "截止后显示成绩作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.AFTER_DEADLINE);
        homework.setDueAt(OffsetDateTime.now().plusHours(2));
        homeworkRepository.save(homework);

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setTotalScore(88D);
        submission.setGradedAt(OffsetDateTime.now());
        homeworkSubmissionRepository.save(submission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());

        assertThat(detail.get("totalScore")).isNull();
    }

    @Test
    void manualReleaseModeKeepsScoreHiddenAfterGrading() {
        Homework homework = createHomework(1L, 1L, "手动发布成绩作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.MANUAL_RELEASE);
        homeworkRepository.save(homework);

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setTotalScore(91D);
        submission.setGradedAt(OffsetDateTime.now());
        homeworkSubmissionRepository.save(submission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());

        assertThat(detail.get("totalScore")).isNull();
    }

    @Test
    void manualReleaseModeShowsScoreAfterTeacherRelease() {
        Homework homework = createHomework(1L, 1L, "手动发布成绩作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.MANUAL_RELEASE);
        homeworkRepository.save(homework);

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setTotalScore(91D);
        submission.setGradedAt(OffsetDateTime.now());
        homeworkSubmissionRepository.save(submission);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        homeworkService.releaseScores(teacher, homework.getId());

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());

        assertThat(detail.get("totalScore")).isEqualTo(91D);
    }

    @Test
    void hiddenHomeworkScoreShouldAlsoHideTeacherCommentFromStudentViews() {
        Homework homework = createHomework(1L, 1L, "隐藏评语作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setTotalScore(91D);
        submission.setTeacherComment("教师已写评语但尚未确认");
        homeworkSubmissionRepository.save(submission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());
        List<Map<String, Object>> items = homeworkService.listStudentHomeworks(student);

        assertThat(detail.get("totalScore")).isNull();
        assertThat(detail.get("teacherComment")).isNull();
        assertThat(items).anySatisfy(item -> {
            if (homework.getId().equals(item.get("id"))) {
                assertThat(item.get("totalScore")).isNull();
                assertThat(item.get("teacherComment")).isNull();
            }
        });
    }

    @Test
    void questionBasedSubmissionFeedsLaterPlagiarismCandidates() {
        // 查重候选已收敛为同一作业内比对（FR-9），历史提交必须落在同一 homeworkId
        Homework homework = createHomework(1L, 1L, "题目化作业", ActivityStatus.PUBLISHED);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission historySubmission = new HomeworkSubmission();
        historySubmission.setHomeworkId(homework.getId());
        historySubmission.setStudentId(3L);
        historySubmission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        historySubmission.setAnswerText("");
        HomeworkSubmission savedHistorySubmission = homeworkSubmissionRepository.save(historySubmission);

        HomeworkAnswer historyAnswer = new HomeworkAnswer();
        historyAnswer.setHomeworkSubmissionId(savedHistorySubmission.getId());
        historyAnswer.setHomeworkQuestionId(question.getId());
        historyAnswer.setAnswerText("软件体系结构设计原则与分层架构说明");
        historyAnswer.setScoreSource("RECOMMENDED");
        homeworkAnswerRepository.save(historyAnswer);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest(List.of(
                        new HomeworkRequests.SubmitHomeworkAnswerItem(question.getId(), null, "软件体系结构设计原则与分层架构说明", null, null)
                ))
        );

        HomeworkSubmission currentSubmission = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homework.getId(), student.id()).orElseThrow();
        // 含主观题且未评分：提交后保持 SUBMITTED，等待教师批改
        assertThat(currentSubmission.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(currentSubmission.getPlagiarismRate()).isEqualTo(100D);
    }

    @Test
    void teacherSubmissionListIncludesTopMatchSummary() {
        Homework homework = createHomework(1L, 1L, "列表作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setAnswerText("学生提交内容");
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setPlagiarismRate(88.89D);
        submission.setTotalScore(0D);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);
        jdbcTemplate.update(
                "INSERT INTO plagiarism_task (business_type, business_id, student_id, status, similarity_rate, top_match_summary, raw_result_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                "HOMEWORK", savedSubmission.getId(), 2L, "COMPLETED", 88.89D, "最高相似来源: 样本作业#9 (88.89%)", "{\"algorithm\":\"jaccard\"}"
        );

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        List<Map<String, Object>> submissions = homeworkService.listTeacherSubmissions(teacher, homework.getId());

        assertThat(submissions).singleElement().satisfies(item -> {
            assertThat(item.get("topMatchSummary")).isEqualTo("最高相似来源: 样本作业#9 (88.89%)");
            assertThat(item.get("className")).isNotNull().asString().isNotBlank();
            assertThat(item.get("studentNo")).isEqualTo("20260001");
            assertThat(item.get("submittedAt")).isNotNull();
        });
    }

    @Test
    void teacherCannotGradeSubmissionWhenStatusIsNotSubmitted() {
        Homework homework = createHomework(1L, 1L, "已批改作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setAnswerText("学生提交内容");
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setTotalScore(85D);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        assertThatThrownBy(() -> homeworkService.gradeSubmission(
                teacher,
                savedSubmission.getId(),
                new HomeworkRequests.GradeHomeworkRequest(90D, "重复批改")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已提交作业允许批改");
    }

    @Test
    void studentSubmitCreatesRealPlagiarismResultWithoutFakeStatusField() {
        Homework homework = createHomework(1L, 1L, "待提交作业", ActivityStatus.PUBLISHED);
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Integer plagiarismTaskCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plagiarism_task", Integer.class);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("本次作业答案", null, "/uploads/homework/answer.docx")
        );

        HomeworkSubmission saved = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homework.getId(), student.id()).orElseThrow();
        Integer plagiarismTaskCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plagiarism_task", Integer.class);

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        assertThat(result).doesNotContainKey("plagiarismStatus");
        assertThat(result.get("plagiarismRate")).isEqualTo(0D);
        assertThat(saved.getPlagiarismRate()).isEqualTo(0D);
        assertThat(saved.getAnswerFilePath()).isEqualTo("/uploads/homework/answer.docx");
        assertThat(plagiarismTaskCountAfter).isEqualTo(plagiarismTaskCountBefore + 1);
    }

    @Test
    void submitHomeworkCreatesRealPlagiarismTask() {
        // 查重候选已收敛为同一作业内比对（FR-9），历史提交必须落在同一 homeworkId
        Homework homework = createHomework(1L, 1L, "历史作业", ActivityStatus.PUBLISHED);

        HomeworkSubmission historySubmission = new HomeworkSubmission();
        historySubmission.setHomeworkId(homework.getId());
        historySubmission.setStudentId(3L);
        historySubmission.setAnswerText("软件体系结构设计原则与分层架构说明");
        historySubmission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        historySubmission.setPlagiarismRate(0D);
        historySubmission.setTotalScore(0D);
        homeworkSubmissionRepository.save(historySubmission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("软件体系结构设计原则与分层架构说明", "/uploads/homework/answer.docx", null)
        );

        HomeworkSubmission saved = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homework.getId(), student.id()).orElseThrow();
        Map<String, Object> task = jdbcTemplate.queryForMap(
                "SELECT business_id, student_id, similarity_rate, top_match_summary, raw_result_json FROM plagiarism_task WHERE business_id = ?",
                saved.getId()
        );

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        assertThat(saved.getPlagiarismRate()).isEqualTo(100D);
        assertThat(task.get("business_id")).isEqualTo(saved.getId());
        assertThat(task.get("student_id")).isEqualTo(student.id());
        assertThat(((Number) task.get("similarity_rate")).doubleValue()).isEqualTo(100D);
        assertThat(String.valueOf(task.get("top_match_summary"))).contains("历史作业");
        assertThat(String.valueOf(task.get("raw_result_json")))
                .contains("jaccard")
                .contains("similarityRate")
                .contains("topMatchReference")
                .contains("topMatchSummary");
    }

    @Test
    void submitHomeworkGradesAllObjectiveQuestionsAndWritesScoreRecordOnce() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "全客观题作业", ActivityStatus.DRAFT);
        Map<String, Object> created = homeworkService.addInlineQuestion(teacher, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SINGLE",
                        "下列说法正确的是",
                        5D,
                        "[{\"key\":\"A\",\"content\":\"正确\"},{\"key\":\"B\",\"content\":\"错误\"}]",
                        "[\"A\"]",
                        null,
                        null,
                        1
                ));
        homework.setStatus(ActivityStatus.PUBLISHED);
        homeworkRepository.save(homework);
        Long questionId = Long.valueOf(String.valueOf(created.get("id")));

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest(List.of(
                        new HomeworkRequests.SubmitHomeworkAnswerItem(questionId, List.of("A"), null, null, null)
                ))
        );

        HomeworkSubmission saved = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homework.getId(), student.id()).orElseThrow();
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM score_record
                WHERE business_type = 'HOMEWORK' AND business_id = ? AND student_id = ?
                """,
                Integer.class,
                homework.getId(),
                student.id()
        );

        // 全客观题在提交阶段即自动结案并写入成绩记录
        assertThat(result.get("status")).isEqualTo("GRADED");
        assertThat(saved.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(saved.getTotalScore()).isEqualTo(5D);
        assertThat(scoreRecordCount).isEqualTo(1);

        // 已结案作业重复提交被拒绝，成绩记录不得重复写入
        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest(List.of(
                        new HomeworkRequests.SubmitHomeworkAnswerItem(questionId, List.of("A"), null, null, null)
                ))
        )).isInstanceOf(BusinessException.class);
        Integer afterCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM score_record
                WHERE business_type = 'HOMEWORK' AND business_id = ? AND student_id = ?
                """,
                Integer.class,
                homework.getId(),
                student.id()
        );
        assertThat(afterCount).isEqualTo(1);
    }

    @Test
    void plagiarismNormalizationTreatsCommonFullWidthAndHalfWidthVariantsAsEquivalent() {
        LocalPlagiarismService.PlagiarismAnalysisResult result = localPlagiarismService.analyze(
                "ＡＢＣ１２３，（）ａｂｃ",
                List.of(new LocalPlagiarismService.CandidateText("样本", "ABC123,()abc"))
        );

        assertThat(result.similarityRate()).isEqualTo(100D);
        assertThat(result.rawResultJson()).contains("jaccard");
    }

    @Test
    void plagiarismRateChangesWithDifferentSubmissionText() {
        // 查重候选已收敛为同一作业内比对（FR-9）：每个作业各自放一份历史提交
        Homework sameTextHomework = createHomework(1L, 1L, "相同文本作业", ActivityStatus.PUBLISHED);
        Homework differentTextHomework = createHomework(1L, 1L, "不同文本作业", ActivityStatus.PUBLISHED);

        HomeworkSubmission sameHistory = new HomeworkSubmission();
        sameHistory.setHomeworkId(sameTextHomework.getId());
        sameHistory.setStudentId(3L);
        sameHistory.setAnswerText("abcdefghijklmnop");
        sameHistory.setSubmitStatus(SubmissionStatus.SUBMITTED);
        sameHistory.setPlagiarismRate(0D);
        sameHistory.setTotalScore(0D);
        homeworkSubmissionRepository.save(sameHistory);

        HomeworkSubmission differentHistory = new HomeworkSubmission();
        differentHistory.setHomeworkId(differentTextHomework.getId());
        differentHistory.setStudentId(3L);
        differentHistory.setAnswerText("abcdefghijklmnop");
        differentHistory.setSubmitStatus(SubmissionStatus.SUBMITTED);
        differentHistory.setPlagiarismRate(0D);
        differentHistory.setTotalScore(0D);
        homeworkSubmissionRepository.save(differentHistory);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        homeworkService.submitHomework(
                student,
                sameTextHomework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("abcdefghijklmnop", null, null)
        );
        HomeworkSubmission sameSaved = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(sameTextHomework.getId(), student.id()).orElseThrow();

        homeworkService.submitHomework(
                student,
                differentTextHomework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("zzzzzzzzzzzzzzzz", null, null)
        );
        HomeworkSubmission differentSaved = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(differentTextHomework.getId(), student.id()).orElseThrow();

        assertThat(sameSaved.getPlagiarismRate()).isGreaterThan(differentSaved.getPlagiarismRate());
        assertThat(sameSaved.getPlagiarismRate()).isEqualTo(100D);
        assertThat(differentSaved.getPlagiarismRate()).isEqualTo(0D);
    }

    @Test
    void createHomeworkAcceptsOffsetDateTimeFields() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        OffsetDateTime startAt = OffsetDateTime.parse("2026-04-01T08:00:00+08:00");
        OffsetDateTime dueAt = OffsetDateTime.parse("2026-04-15T23:59:00+08:00");

        Map<String, Object> created = homeworkService.createHomework(
                teacher,
                new HomeworkRequests.CreateHomeworkRequest(
                        "带时间作业",
                        "时间字段应保持类型化",
                        1L,
                        "PUBLISHED",
                        ScoreVisibilityMode.AFTER_TEACHER_CONFIRM,
                        null,
                        startAt,
                        dueAt
                )
        );

        Homework saved = homeworkRepository.findById(Long.valueOf(String.valueOf(created.get("id")))).orElseThrow();
        assertThat(saved.getStartAt()).isEqualTo(startAt);
        assertThat(saved.getDueAt()).isEqualTo(dueAt);
    }

    @Test
    void studentHomeworkDetailReturnsClassNameAndAttachmentReadback() {
        Homework homework = createHomework(1L, 1L, "详情回显作业", ActivityStatus.PUBLISHED);
        homework.setAttachmentPath("/uploads/homework/template.pdf");
        homeworkRepository.save(homework);

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setAnswerText("学生已提交内容");
        submission.setAnswerFilePath("/uploads/homework/student.docx");
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setPlagiarismRate(12.34D);
        submission.setTotalScore(0D);
        homeworkSubmissionRepository.save(submission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());

        assertThat(detail.get("className")).isNotNull().asString().isNotBlank();
        assertThat(detail.get("attachmentPath")).isEqualTo("/uploads/homework/template.pdf");
        assertThat(detail.get("answerFilePath")).isEqualTo("/uploads/homework/student.docx");
        assertThat(detail.get("attachmentAnswerPath")).isEqualTo("/uploads/homework/student.docx");
        assertThat(detail.get("hasSubmittedAttachment")).isEqualTo(true);
    }

    @Test
    void inlineQuestionSnapshotCarriesOptionsAnswerAndAnalysis() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "内联题落库作业", ActivityStatus.DRAFT);

        Map<String, Object> created = homeworkService.addInlineQuestion(teacher, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SINGLE",
                        "下列说法正确的是",
                        5D,
                        "[{\"key\":\"A\",\"content\":\"正确\"},{\"key\":\"B\",\"content\":\"错误\"}]",
                        "[\"A\"]",
                        "解析文本",
                        null,
                        1
                ));

        HomeworkQuestion saved = homeworkQuestionRepository
                .findById(Long.valueOf(String.valueOf(created.get("id")))).orElseThrow();
        assertThat(saved.getQuestionSnapshotJson())
                .contains("\"key\":\"A\"")
                .contains("\"content\":\"正确\"")
                .contains("\"answer\":[\"A\"]")
                .contains("解析文本");
        assertThat(created.get("optionsJson")).isNotNull();
        assertThat(created.get("answerJson")).isEqualTo("[\"A\"]");
    }

    @Test
    void inlineQuestionWithMissingOptionsOrAnswerIsRejected() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "非法内联题作业", ActivityStatus.DRAFT);

        assertThatThrownBy(() -> homeworkService.addInlineQuestion(teacher, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SINGLE", "缺选项", 5D, null, "[\"A\"]", null, null, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要 2 个选项");
    }

    @Test
    void publishedHomeworkOnlyAllowsSortOrderUpdate() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "已发布作业", ActivityStatus.DRAFT);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");
        homework.setStatus(ActivityStatus.PUBLISHED);
        homeworkRepository.save(homework);

        assertThatThrownBy(() -> homeworkService.addInlineQuestion(teacher, homework.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SHORT", "新增题", 10D, null, "[\"参考\"]", null, null, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态");

        assertThatThrownBy(() -> homeworkService.updateQuestion(teacher, homework.getId(), question.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SHORT", "修改题干", 15D, null, "[]", null,
                        "{\"keywords\":[{\"term\":\"strategy\",\"weight\":12}],\"minLength\":6}", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持调整排序");

        // 历史内联题快照答案为空对象，排序保存时须原样回传才能通过一致性比较
        homeworkService.updateQuestion(teacher, homework.getId(), question.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE", "请说明策略模式的价值", 15D, null, "{}", null,
                        "{\"keywords\":[{\"term\":\"strategy\",\"weight\":12}],\"minLength\":6}", 5));
        assertThat(homeworkQuestionRepository.findById(question.getId()).orElseThrow().getSortOrder()).isEqualTo(5);
    }

    @Test
    void closedHomeworkRejectsQuestionChanges() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "已关闭作业", ActivityStatus.DRAFT);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "题干");
        homework.setStatus(ActivityStatus.CLOSED);
        homeworkRepository.save(homework);

        assertThatThrownBy(() -> homeworkService.updateQuestion(teacher, homework.getId(), question.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE", "题干", 15D, null, "[]", null, null, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已关闭");
        assertThatThrownBy(() -> homeworkService.deleteQuestion(teacher, homework.getId(), question.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除题目");
    }

    @Test
    void draftQuestionWithAnswersCannotChangeScoreOrBeDeleted() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "已有作答作业", ActivityStatus.DRAFT);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        HomeworkSubmission savedSubmission = homeworkSubmissionRepository.save(submission);
        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(savedSubmission.getId());
        answer.setHomeworkQuestionId(question.getId());
        answer.setAnswerText("strategy 提升扩展性");
        homeworkAnswerRepository.save(answer);

        assertThatThrownBy(() -> homeworkService.updateQuestion(teacher, homework.getId(), question.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE", "请说明策略模式的价值", 20D, null, "[]", null,
                        "{\"keywords\":[{\"term\":\"strategy\",\"weight\":12}],\"minLength\":6}", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能修改分值");

        homeworkService.updateQuestion(teacher, homework.getId(), question.getId(),
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SUBJECTIVE", "请说明策略模式的复用价值", 15D, null, "[]", null,
                        "{\"keywords\":[{\"term\":\"strategy\",\"weight\":12}],\"minLength\":6}", 1));
        assertThat(homeworkQuestionRepository.findById(question.getId()).orElseThrow().getQuestionSnapshotJson())
                .contains("复用价值");

        assertThatThrownBy(() -> homeworkService.deleteQuestion(teacher, homework.getId(), question.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void updatingBankRowSnapshotDoesNotModifyQuestionBank() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "引用题库作业", ActivityStatus.DRAFT);
        // 直接使用迁移种子中的题库题目，避免在 DataJpaTest 里与种子自增主键冲突
        QuestionBank bankQuestion = questionBankRepository.findById(1L).orElseThrow();
        String originalStem = bankQuestion.getStem();

        Map<String, Object> created = homeworkService.addQuestionFromBank(teacher, homework.getId(),
                new HomeworkRequests.AddHomeworkQuestionFromBankRequest(bankQuestion.getId(), 1, 5D));
        Long homeworkQuestionId = Long.valueOf(String.valueOf(created.get("id")));

        homeworkService.updateQuestion(teacher, homework.getId(), homeworkQuestionId,
                new HomeworkRequests.SaveHomeworkQuestionRequest(
                        "SINGLE", "作业内改题干", 5D,
                        "[{\"key\":\"A\",\"content\":\"降低复杂度\"},{\"key\":\"B\",\"content\":\"增加偶然性\"}]",
                        "[\"A\"]", null, null, 1));

        assertThat(questionBankRepository.findById(bankQuestion.getId()).orElseThrow().getStem()).isEqualTo(originalStem);
    }

    @Test
    void addQuestionFromBankRejectsForeignQuestion() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "他人题库作业", ActivityStatus.DRAFT);

        String foreignCode = "FOREIGN-Q-" + System.nanoTime();
        // 显式指定主键，避免 H2 identity 与种子数据显式 ID 冲突
        jdbcTemplate.update(
                """
                INSERT INTO question_bank (id, code, type, stem, difficulty, default_score, answer_json, created_by, created_at, updated_at)
                VALUES (9002, ?, 'SINGLE', ?, 'MEDIUM', 5, '["A"]', 3, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """,
                foreignCode, "他人创建的题库题目"
        );
        Long foreignQuestionId = jdbcTemplate.queryForObject(
                "SELECT id FROM question_bank WHERE code = ?", Long.class, foreignCode);
        long before = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homework.getId()).size();

        assertThatThrownBy(() -> homeworkService.addQuestionFromBank(teacher, homework.getId(),
                new HomeworkRequests.AddHomeworkQuestionFromBankRequest(foreignQuestionId, 1, 5D)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题库题目不存在");

        assertThat(homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homework.getId())).hasSize((int) before);
    }

    @Test
    void addQuestionFromBankAcceptsOwnQuestion() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "本人题库作业", ActivityStatus.DRAFT);
        QuestionBank ownQuestion = questionBankRepository.findById(1L).orElseThrow();

        Map<String, Object> created = homeworkService.addQuestionFromBank(teacher, homework.getId(),
                new HomeworkRequests.AddHomeworkQuestionFromBankRequest(ownQuestion.getId(), 1, 5D));

        assertThat(created.get("id")).isNotNull();
        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homework.getId());
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).getQuestionId()).isEqualTo(ownQuestion.getId());
    }

    @Test
    void resubmitRejectedWhenSubmissionAlreadySubmitted() {
        Homework homework = createHomework(1L, 1L, "重复提交已提交作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission existing = createSubmission(homework, SubmissionStatus.SUBMITTED);
        existing.setAnswerText("第一次提交内容");
        homeworkSubmissionRepository.save(existing);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("第二次提交内容", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已提交，如需修改请联系教师打回");

        HomeworkSubmission unchanged = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homework.getId(), 2L).orElseThrow();
        assertThat(unchanged.getAnswerText()).isEqualTo("第一次提交内容");
    }

    @Test
    void resubmitRejectedWhenSubmissionAlreadyGraded() {
        Homework homework = createHomework(1L, 1L, "重复提交已批改作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission existing = createSubmission(homework, SubmissionStatus.GRADED);
        existing.setAnswerText("已批改的作答内容");
        homeworkSubmissionRepository.save(existing);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("重新提交内容", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已批改，如需修改请联系教师打回");

        HomeworkSubmission unchanged = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homework.getId(), 2L).orElseThrow();
        assertThat(unchanged.getSubmitStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(unchanged.getAnswerText()).isEqualTo("已批改的作答内容");
    }

    @Test
    void teacherReturnSubmittedHomeworkResetsToSavedDeletesScoreAndWritesAudit() {
        Homework homework = createHomework(1L, 1L, "打回已提交作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.SUBMITTED);
        insertHomeworkScoreRecord(homework.getId(), 2L, 10D);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Map<String, Object> result = homeworkService.returnHomeworkSubmission(teacher, submission.getId());
        // 显式刷盘，确保成绩记录的删除在后续 JDBC 统计前落到同一事务连接上
        homeworkSubmissionRepository.flush();

        assertThat(result)
                .containsEntry("submissionId", submission.getId())
                .containsEntry("status", "SAVED")
                .containsEntry("homeworkId", homework.getId())
                .containsEntry("studentId", 2L);

        HomeworkSubmission returned = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(returned.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(returned.getGradedAt()).isNull();
        assertThat(returned.getTotalScore()).isNull();
        assertThat(returned.getTeacherComment()).isEqualTo("");
        assertThat(countHomeworkScoreRecords(homework.getId(), 2L)).isZero();
        assertThat(countReturnAuditLogs(submission.getId())).isEqualTo(1);

        String detailJson = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM audit_log WHERE action = 'HOMEWORK_SUBMISSION_RETURN' AND resource_id = ?",
                String.class, submission.getId());
        assertThat(detailJson)
                .contains("\"homeworkId\":" + homework.getId())
                .contains("\"studentId\":2")
                .contains("\"fromStatus\":\"SUBMITTED\"");
    }

    @Test
    void teacherReturnGradedHomeworkResetsToSavedAndDeletesScore() {
        Homework homework = createHomework(1L, 1L, "打回已批改作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.GRADED);
        insertHomeworkScoreRecord(homework.getId(), 2L, 10D);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Map<String, Object> result = homeworkService.returnHomeworkSubmission(teacher, submission.getId());
        // 显式刷盘，确保成绩记录的删除在后续 JDBC 统计前落到同一事务连接上
        homeworkSubmissionRepository.flush();

        HomeworkSubmission returned = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(result.get("status")).isEqualTo("SAVED");
        assertThat(returned.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(returned.getGradedAt()).isNull();
        assertThat(returned.getTotalScore()).isNull();
        assertThat(countHomeworkScoreRecords(homework.getId(), 2L)).isZero();

        String detailJson = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM audit_log WHERE action = 'HOMEWORK_SUBMISSION_RETURN' AND resource_id = ?",
                String.class, submission.getId());
        assertThat(detailJson).contains("\"fromStatus\":\"GRADED\"");
    }

    @Test
    void gradedHomeworkDowngradeRemovesScoreRecord() {
        Homework homework = createHomework(1L, 1L, "回退残留成绩作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.GRADED);
        insertHomeworkScoreRecord(homework.getId(), 2L, 10D);

        homeworkServiceSupport.refreshSubmissionGradeState(submission);
        homeworkSubmissionRepository.flush();

        HomeworkSubmission reloaded = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(countHomeworkScoreRecords(homework.getId(), 2L)).isZero();
    }

    @Test
    void teacherReturnSubmissionClearsAnswerGradingTraceButKeepsAnswerContent() {
        Homework homework = createHomework(1L, 1L, "打回清痕作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.GRADED);
        HomeworkAnswer answer = createScoredAnswer(submission, "保留的学生作答文本", "{\"text\":\"保留的学生作答\"}");

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        homeworkService.returnHomeworkSubmission(teacher, submission.getId());

        HomeworkAnswer returnedAnswer = homeworkAnswerRepository.findById(answer.getId()).orElseThrow();
        assertThat(returnedAnswer.getScore()).isNull();
        assertThat(returnedAnswer.getAutoScore()).isNull();
        assertThat(returnedAnswer.getSuggestedScore()).isNull();
        assertThat(returnedAnswer.getJudgeDetail()).isNull();
        assertThat(returnedAnswer.getTeacherComment()).isEqualTo("");
        assertThat(returnedAnswer.getScoreSource()).isEqualTo("TEACHER");
        assertThat(returnedAnswer.isAcceptedAutoScore()).isFalse();
        assertThat(returnedAnswer.getAnswerText()).isEqualTo("保留的学生作答文本");
        assertThat(returnedAnswer.getAnswerJson()).isEqualTo("{\"text\":\"保留的学生作答\"}");

        HomeworkSubmission returned = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(returned.getAnswerFilePath()).isEqualTo("/uploads/homework/answer.docx");
    }

    @Test
    void teacherCannotReturnSavedSubmission() {
        Homework homework = createHomework(1L, 1L, "打回草稿作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.SAVED);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        assertThatThrownBy(() -> homeworkService.returnHomeworkSubmission(teacher, submission.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已提交或已批改的作业可打回");

        HomeworkSubmission unchanged = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(unchanged.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
    }

    @Test
    void teacherCannotReturnSubmissionOfClosedHomework() {
        Homework homework = createHomework(1L, 1L, "打回已关闭作业", ActivityStatus.CLOSED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.SUBMITTED);

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        assertThatThrownBy(() -> homeworkService.returnHomeworkSubmission(teacher, submission.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已关闭，不能打回");

        HomeworkSubmission unchanged = homeworkSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(unchanged.getSubmitStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    void submitHomeworkRejectsOverdueSubmission() {
        Homework homework = createHomework(1L, 1L, "已逾期作业", ActivityStatus.PUBLISHED);
        homework.setDueAt(OffsetDateTime.now().minusMinutes(1));
        homeworkRepository.save(homework);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        int submissionCountBefore = countHomeworkSubmissions(homework.getId());
        int answerCountBefore = countHomeworkAnswers(homework.getId());
        int plagiarismCountBefore = countPlagiarismTasks();
        int scoreCountBefore = countHomeworkScoreRecords(homework.getId(), student.id());

        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("逾期提交内容", null, "/uploads/homework/answer.docx")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已截止，不能提交");

        assertThat(countHomeworkSubmissions(homework.getId())).isEqualTo(submissionCountBefore);
        assertThat(countHomeworkAnswers(homework.getId())).isEqualTo(answerCountBefore);
        assertThat(countPlagiarismTasks()).isEqualTo(plagiarismCountBefore);
        assertThat(countHomeworkScoreRecords(homework.getId(), student.id())).isEqualTo(scoreCountBefore);
    }

    @Test
    void submitHomeworkAllowsSubmissionWhenDueAtIsNull() {
        Homework homework = createHomework(1L, 1L, "不限时作业", ActivityStatus.PUBLISHED);
        assertThat(homework.getDueAt()).isNull();

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("不限时提交内容", null, null));

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        assertThat(homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homework.getId(), student.id())).isPresent();
    }

    @Test
    void submitHomeworkAllowedBeforeDueAt() {
        Homework homework = createHomework(1L, 1L, "未到期作业", ActivityStatus.PUBLISHED);
        homework.setDueAt(OffsetDateTime.now().plusHours(1));
        homeworkRepository.save(homework);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("未到期提交内容", null, null));

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        assertThat(homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homework.getId(), student.id())).isPresent();
    }

    @Test
    void submitHomeworkKeepsClosedAndOverdueGatesIndependent() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Homework closedOverdue = createHomework(1L, 1L, "关闭且逾期作业", ActivityStatus.CLOSED);
        closedOverdue.setDueAt(OffsetDateTime.now().minusHours(1));
        homeworkRepository.save(closedOverdue);

        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                closedOverdue.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("关闭且逾期提交内容", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已关闭");

        Homework publishedOverdue = createHomework(1L, 1L, "发布但逾期作业", ActivityStatus.PUBLISHED);
        publishedOverdue.setDueAt(OffsetDateTime.now().minusHours(1));
        homeworkRepository.save(publishedOverdue);

        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                publishedOverdue.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("发布但逾期提交内容", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已截止");
    }

    @Test
    void submitHomeworkRejectsBeforeStartAt() {
        Homework homework = createHomework(1L, 1L, "未开始作业", ActivityStatus.PUBLISHED);
        homework.setStartAt(OffsetDateTime.now().plusHours(2));
        homeworkRepository.save(homework);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        int submissionCountBefore = countHomeworkSubmissions(homework.getId());
        int answerCountBefore = countHomeworkAnswers(homework.getId());

        assertThatThrownBy(() -> homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("提前提交内容", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业尚未开始");

        assertThat(countHomeworkSubmissions(homework.getId())).isEqualTo(submissionCountBefore);
        assertThat(countHomeworkAnswers(homework.getId())).isEqualTo(answerCountBefore);
    }

    @Test
    void submitHomeworkAllowedAfterStartAt() {
        Homework homework = createHomework(1L, 1L, "已开始作业", ActivityStatus.PUBLISHED);
        homework.setStartAt(OffsetDateTime.now().minusSeconds(1));
        homeworkRepository.save(homework);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("已开始提交内容", null, null));

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
    }

    @Test
    void submitHomeworkAllowsSubmissionAtStartBoundary() {
        // 边界语义：now == startAt 允许提交（门禁只在 now < startAt 时拒绝）
        Homework homework = createHomework(1L, 1L, "边界开始作业", ActivityStatus.PUBLISHED);
        homework.setStartAt(OffsetDateTime.now());
        homeworkRepository.save(homework);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("边界提交内容", null, null));

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
    }

    @Test
    void submitHomeworkSkipsClaimWhenSubmissionAlreadyExists() {
        Homework homework = createHomework(1L, 1L, "已有草稿作业", ActivityStatus.PUBLISHED);
        createSubmission(homework, SubmissionStatus.SAVED);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("草稿转提交内容", null, null));

        assertThat(result.get("status")).isEqualTo("SUBMITTED");
        // 快路径命中时不得开启独立事务领取
        verify(homeworkSubmissionClaimService, never()).claim(anyLong(), anyLong());
    }

    @Test
    void gradeAnswerRejectsSubmissionInSavedStatus() {
        Homework homework = createHomework(1L, 1L, "未提交作业", ActivityStatus.PUBLISHED);
        HomeworkSubmission submission = createSubmission(homework, SubmissionStatus.SAVED);
        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(submission.getId());
        answer.setAnswerText("草稿答案");
        answer = homeworkAnswerRepository.save(answer);
        Long answerId = answer.getId();

        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        int scoreCountBefore = countHomeworkScoreRecords(homework.getId(), 2L);

        assertThatThrownBy(() -> homeworkService.gradeAnswer(
                teacher,
                answerId,
                new HomeworkRequests.GradeHomeworkAnswerRequest(5D, "草稿不应被评分", false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生尚未提交作业，不能批改");

        assertThat(homeworkAnswerRepository.findById(answerId).orElseThrow().getScore()).isNull();
        assertThat(countHomeworkScoreRecords(homework.getId(), 2L)).isEqualTo(scoreCountBefore);
    }

    @Test
    void createHomeworkDefaultsVisibilityToAfterTeacherConfirm() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        Map<String, Object> created = homeworkService.createHomework(
                teacher,
                new HomeworkRequests.CreateHomeworkRequest(
                        "默认可见性作业",
                        "未传可见性",
                        1L,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        Homework saved = homeworkRepository.findById(Long.valueOf(String.valueOf(created.get("id")))).orElseThrow();
        assertThat(saved.getScoreVisibilityMode()).isEqualTo(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        assertThat(saved.getStatus()).isEqualTo(ActivityStatus.DRAFT);
    }

    @Test
    void updateHomeworkWithoutVisibilityKeepsManualRelease() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "手动发布作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.MANUAL_RELEASE);
        homeworkRepository.save(homework);

        homeworkService.updateHomework(
                teacher,
                homework.getId(),
                new HomeworkRequests.UpdateHomeworkRequest(
                        "手动发布作业改名",
                        "只改标题",
                        1L,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        Homework updated = homeworkRepository.findById(homework.getId()).orElseThrow();
        assertThat(updated.getScoreVisibilityMode()).isEqualTo(ScoreVisibilityMode.MANUAL_RELEASE);
        assertThat(updated.getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
        assertThat(updated.getTitle()).isEqualTo("手动发布作业改名");
    }

    @Test
    void createHomeworkRejectsClosedStatus() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);

        assertThatThrownBy(() -> homeworkService.createHomework(
                teacher,
                new HomeworkRequests.CreateHomeworkRequest(
                        "非法状态作业",
                        null,
                        1L,
                        "CLOSED",
                        ScoreVisibilityMode.IMMEDIATE,
                        null,
                        null,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业状态不允许从 DRAFT 变更为 CLOSED");
    }

    @Test
    void updateHomeworkRejectsRevertingPublishedToDraftWhenSubmissionsExist() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "已发布有提交作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);
        createSubmission(homework, SubmissionStatus.SUBMITTED);

        assertThatThrownBy(() -> homeworkService.updateHomework(
                teacher,
                homework.getId(),
                new HomeworkRequests.UpdateHomeworkRequest(
                        "已发布有提交作业",
                        null,
                        1L,
                        "DRAFT",
                        null,
                        null,
                        null,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业状态不允许从 PUBLISHED 变更为 DRAFT");

        assertThat(homeworkRepository.findById(homework.getId()).orElseThrow().getStatus())
                .isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    void updateHomeworkAllowsRevertingPublishedToDraftWithoutSubmissions() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "无提交回退作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);

        homeworkService.updateHomework(
                teacher,
                homework.getId(),
                new HomeworkRequests.UpdateHomeworkRequest(
                        "无提交回退作业",
                        null,
                        1L,
                        "DRAFT",
                        null,
                        null,
                        null,
                        null
                )
        );

        assertThat(homeworkRepository.findById(homework.getId()).orElseThrow().getStatus())
                .isEqualTo(ActivityStatus.DRAFT);
    }

    @Test
    void updateHomeworkRejectsClassChangeWhenSubmissionsExist() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "换班作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);
        createSubmission(homework, SubmissionStatus.SUBMITTED);

        assertThatThrownBy(() -> homeworkService.updateHomework(
                teacher,
                homework.getId(),
                new HomeworkRequests.UpdateHomeworkRequest(
                        "换班作业",
                        null,
                        99L,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有学生提交，不能变更所属班级");
    }

    @Test
    void changeHomeworkStatusRejectsClosedToPublished() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "已关闭作业", ActivityStatus.CLOSED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);

        assertThatThrownBy(() -> homeworkService.changeHomeworkStatus(
                teacher,
                homework.getId(),
                new HomeworkRequests.ChangeHomeworkStatusRequest("PUBLISHED")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业状态不允许从 CLOSED 变更为 PUBLISHED");
    }

    @Test
    void changeHomeworkStatusAllowsDraftToPublished() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        Homework homework = createHomework(1L, 1L, "草稿发布作业", ActivityStatus.DRAFT);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);

        homeworkService.changeHomeworkStatus(
                teacher,
                homework.getId(),
                new HomeworkRequests.ChangeHomeworkStatusRequest("PUBLISHED")
        );

        assertThat(homeworkRepository.findById(homework.getId()).orElseThrow().getStatus())
                .isEqualTo(ActivityStatus.PUBLISHED);
    }

    @Test
    void saveDraftCreatesSavedSubmissionAndBackfillsAnswers() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Homework homework = createHomework(1L, 1L, "草稿作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 10D, "草稿题干");

        Map<String, Object> result = homeworkService.saveHomeworkDraft(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest(
                        "草稿正文",
                        null,
                        null,
                        List.of(new HomeworkRequests.SubmitHomeworkAnswerItem(
                                question.getId(),
                                List.of(),
                                "草稿分题答案",
                                "{\"value\":\"草稿分题答案\"}",
                                null
                        ))
                )
        );

        assertThat(result.get("status")).isEqualTo(SubmissionStatus.SAVED.name());
        assertThat(result.get("savedAt")).isNotNull();
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homework.getId(), 2L)
                .orElseThrow();
        assertThat(submission.getSubmitStatus()).isEqualTo(SubmissionStatus.SAVED);
        assertThat(submission.getAnswerText()).isEqualTo("草稿正文");
        assertThat(submission.getTotalScore()).isNull();
        assertThat(countHomeworkScoreRecords(homework.getId(), 2L)).isZero();

        Map<String, Object> detail = homeworkService.getStudentHomeworkDetail(student, homework.getId());
        assertThat(detail.get("submissionStatus")).isEqualTo(SubmissionStatus.SAVED.name());
        assertThat(detail.get("answerText")).isEqualTo("草稿正文");
        List<Map<String, Object>> questions = castQuestions(detail.get("questions"));
        assertThat(questions).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> answerView = (Map<String, Object>) questions.get(0).get("answer");
        assertThat(answerView).isNotNull();
        assertThat(answerView.get("answerText")).isEqualTo("草稿分题答案");
    }

    @Test
    void saveDraftIsIdempotentForRepeatedSaves() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Homework homework = createHomework(1L, 1L, "幂等草稿作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);
        HomeworkQuestion question = createInlineHomeworkQuestion(homework.getId(), 1, 10D, "幂等题干");

        HomeworkRequests.SubmitHomeworkRequest request = new HomeworkRequests.SubmitHomeworkRequest(
                "第一版草稿",
                null,
                null,
                List.of(new HomeworkRequests.SubmitHomeworkAnswerItem(
                        question.getId(), List.of(), "第一版", null, null))
        );
        homeworkService.saveHomeworkDraft(student, homework.getId(), request);
        homeworkService.saveHomeworkDraft(student, homework.getId(), new HomeworkRequests.SubmitHomeworkRequest(
                "第二版草稿",
                null,
                null,
                List.of(new HomeworkRequests.SubmitHomeworkAnswerItem(
                        question.getId(), List.of(), "第二版", null, null))
        ));

        assertThat(countHomeworkSubmissions(homework.getId())).isEqualTo(1);
        assertThat(countHomeworkAnswers(homework.getId())).isEqualTo(1);
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homework.getId(), 2L)
                .orElseThrow();
        assertThat(submission.getAnswerText()).isEqualTo("第二版草稿");
        HomeworkAnswer answer = homeworkAnswerRepository
                .findByHomeworkSubmissionIdAndHomeworkQuestionId(submission.getId(), question.getId())
                .orElseThrow();
        assertThat(answer.getAnswerText()).isEqualTo("第二版");
    }

    @Test
    void saveDraftRejectsSubmittedSubmission() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Homework homework = createHomework(1L, 1L, "已提交草稿作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);
        createSubmission(homework, SubmissionStatus.SUBMITTED);

        assertThatThrownBy(() -> homeworkService.saveHomeworkDraft(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("再次草稿", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交作业不可修改草稿");
    }

    @Test
    void saveDraftRejectsClosedHomework() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Homework homework = createHomework(1L, 1L, "已关闭草稿作业", ActivityStatus.CLOSED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homeworkRepository.save(homework);

        assertThatThrownBy(() -> homeworkService.saveHomeworkDraft(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("草稿", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已关闭，不能保存草稿");
    }

    @Test
    void saveDraftRejectsBeforeStartAt() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Homework homework = createHomework(1L, 1L, "未开始草稿作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homework.setStartAt(OffsetDateTime.now().plusHours(1));
        homeworkRepository.save(homework);

        assertThatThrownBy(() -> homeworkService.saveHomeworkDraft(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("草稿", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业尚未开始");
    }

    @Test
    void saveDraftRejectsAfterDueAt() {
        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        Homework homework = createHomework(1L, 1L, "已截止草稿作业", ActivityStatus.PUBLISHED);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        homework.setDueAt(OffsetDateTime.now().minusHours(1));
        homeworkRepository.save(homework);

        assertThatThrownBy(() -> homeworkService.saveHomeworkDraft(
                student,
                homework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("草稿", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已截止，不能保存草稿");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castQuestions(Object questions) {
        return (List<Map<String, Object>>) questions;
    }

    private int countHomeworkSubmissions(Long homeworkId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework_submission WHERE homework_id = ?", Integer.class, homeworkId);
        return count == null ? 0 : count;
    }

    private int countHomeworkAnswers(Long homeworkId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM homework_answer ha
                JOIN homework_submission hs ON hs.id = ha.homework_submission_id
                WHERE hs.homework_id = ?
                """,
                Integer.class, homeworkId);
        return count == null ? 0 : count;
    }

    private int countPlagiarismTasks() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plagiarism_task", Integer.class);
        return count == null ? 0 : count;
    }

    private HomeworkSubmission createSubmission(Homework homework, SubmissionStatus status) {
        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomeworkId(homework.getId());
        submission.setStudentId(2L);
        submission.setSubmitStatus(status);
        submission.setTotalScore(10D);
        submission.setTeacherComment("原教师评语");
        submission.setGradedAt(OffsetDateTime.now());
        submission.setAnswerFilePath("/uploads/homework/answer.docx");
        return homeworkSubmissionRepository.save(submission);
    }

    private HomeworkAnswer createScoredAnswer(HomeworkSubmission submission, String answerText, String answerJson) {
        HomeworkQuestion question = createInlineHomeworkQuestion(
                submission.getHomeworkId(), 1, 10D, "打回清痕题干");
        HomeworkAnswer answer = new HomeworkAnswer();
        answer.setHomeworkSubmissionId(submission.getId());
        answer.setHomeworkQuestionId(question.getId());
        answer.setAnswerText(answerText);
        answer.setAnswerJson(answerJson);
        answer.setScore(10D);
        answer.setAutoScore(8D);
        answer.setSuggestedScore(9D);
        answer.setJudgeDetail("matched=[strategy]");
        answer.setTeacherComment("原单题评语");
        answer.setScoreSource("AUTO_ACCEPTED");
        answer.setAcceptedAutoScore(true);
        return homeworkAnswerRepository.save(answer);
    }

    private void insertHomeworkScoreRecord(Long homeworkId, Long studentId, double score) {
        jdbcTemplate.update(
                """
                INSERT INTO score_record (business_type, business_id, student_id, class_id, score, graded_at, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """,
                "HOMEWORK", homeworkId, studentId, score);
    }

    private int countHomeworkScoreRecords(Long homeworkId, Long studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'HOMEWORK' AND business_id = ? AND student_id = ?",
                Integer.class, homeworkId, studentId);
        return count == null ? 0 : count;
    }

    private int countReturnAuditLogs(Long submissionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE action = 'HOMEWORK_SUBMISSION_RETURN'
                  AND resource_type = 'HOMEWORK_SUBMISSION'
                  AND resource_id = ?
                """,
                Integer.class, submissionId);
        return count == null ? 0 : count;
    }

    private Homework createHomework(Long teacherId, Long classId, String title, ActivityStatus status) {
        Homework homework = new Homework();
        homework.setTitle(title);
        homework.setDescription(title + "描述");
        homework.setClassId(classId);
        homework.setCreatedBy(teacherId);
        homework.setStatus(status);
        return homeworkRepository.save(homework);
    }

    private HomeworkQuestion createInlineHomeworkQuestion(Long homeworkId, int sortOrder, double questionScore, String stem) {
        HomeworkQuestion question = new HomeworkQuestion();
        question.setHomeworkId(homeworkId);
        question.setSortOrder(sortOrder);
        question.setQuestionScore(questionScore);
        question.setQuestionSnapshotJson("{\"type\":\"SUBJECTIVE\",\"stem\":\"" + stem + "\",\"questionScore\":" + questionScore + ",\"options\":[],\"answer\":{},\"scoringConfig\":{\"keywords\":[{\"term\":\"strategy\",\"weight\":12}],\"minLength\":6},\"fromBank\":false}");
        return homeworkQuestionRepository.save(question);
    }
}
