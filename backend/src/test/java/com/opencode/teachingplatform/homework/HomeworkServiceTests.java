package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.ClassStatus;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        HomeworkService.class,
        com.opencode.teachingplatform.clazz.service.ClassAccessService.class,
        LocalPlagiarismService.class,
        ScoringEngine.class,
        SingleChoiceScoringStrategy.class,
        MultipleChoiceScoringStrategy.class,
        TrueFalseScoringStrategy.class,
        FillBlankScoringStrategy.class,
        SubjectiveRecommendationStrategy.class
})
class HomeworkServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                "INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_name, assigned_at, created_at, updated_at) VALUES (?, 1, 'SUBJECT', '测试学科', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
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
        Homework historyHomework = createHomework(1L, 1L, "历史题目化作业", ActivityStatus.PUBLISHED);
        Homework currentHomework = createHomework(1L, 1L, "当前题目化作业", ActivityStatus.PUBLISHED);
        HomeworkQuestion historyQuestion = createInlineHomeworkQuestion(historyHomework.getId(), 1, 15D, "请说明策略模式的价值");
        HomeworkQuestion currentQuestion = createInlineHomeworkQuestion(currentHomework.getId(), 1, 15D, "请说明策略模式的价值");

        HomeworkSubmission historySubmission = new HomeworkSubmission();
        historySubmission.setHomeworkId(historyHomework.getId());
        historySubmission.setStudentId(3L);
        historySubmission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        historySubmission.setAnswerText("");
        HomeworkSubmission savedHistorySubmission = homeworkSubmissionRepository.save(historySubmission);

        HomeworkAnswer historyAnswer = new HomeworkAnswer();
        historyAnswer.setHomeworkSubmissionId(savedHistorySubmission.getId());
        historyAnswer.setHomeworkQuestionId(historyQuestion.getId());
        historyAnswer.setAnswerText("软件体系结构设计原则与分层架构说明");
        historyAnswer.setScoreSource("RECOMMENDED");
        homeworkAnswerRepository.save(historyAnswer);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
        homeworkService.submitHomework(
                student,
                currentHomework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest(List.of(
                        new HomeworkRequests.SubmitHomeworkAnswerItem(currentQuestion.getId(), null, "软件体系结构设计原则与分层架构说明", null, null)
                ))
        );

        HomeworkSubmission currentSubmission = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(currentHomework.getId(), student.id()).orElseThrow();
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
        Homework historyHomework = createHomework(1L, 1L, "历史作业", ActivityStatus.PUBLISHED);
        Homework currentHomework = createHomework(1L, 1L, "当前作业", ActivityStatus.PUBLISHED);

        HomeworkSubmission historySubmission = new HomeworkSubmission();
        historySubmission.setHomeworkId(historyHomework.getId());
        historySubmission.setStudentId(3L);
        historySubmission.setAnswerText("软件体系结构设计原则与分层架构说明");
        historySubmission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        historySubmission.setPlagiarismRate(0D);
        historySubmission.setTotalScore(0D);
        homeworkSubmissionRepository.save(historySubmission);

        CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

        Map<String, Object> result = homeworkService.submitHomework(
                student,
                currentHomework.getId(),
                new HomeworkRequests.SubmitHomeworkRequest("软件体系结构设计原则与分层架构说明", "/uploads/homework/answer.docx", null)
        );

        HomeworkSubmission saved = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(currentHomework.getId(), student.id()).orElseThrow();
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
        Homework historyHomework = createHomework(1L, 1L, "样本文档作业", ActivityStatus.PUBLISHED);
        Homework sameTextHomework = createHomework(1L, 1L, "相同文本作业", ActivityStatus.PUBLISHED);
        Homework differentTextHomework = createHomework(1L, 1L, "不同文本作业", ActivityStatus.PUBLISHED);

        HomeworkSubmission historySubmission = new HomeworkSubmission();
        historySubmission.setHomeworkId(historyHomework.getId());
        historySubmission.setStudentId(3L);
        historySubmission.setAnswerText("abcdefghijklmnop");
        historySubmission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        historySubmission.setPlagiarismRate(0D);
        historySubmission.setTotalScore(0D);
        homeworkSubmissionRepository.save(historySubmission);

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
