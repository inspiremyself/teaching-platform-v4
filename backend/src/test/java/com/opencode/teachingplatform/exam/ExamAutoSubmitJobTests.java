package com.opencode.teachingplatform.exam;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.exam.entity.ExamSubmission;
import com.opencode.teachingplatform.exam.enums.ExamSubmissionStatus;
import com.opencode.teachingplatform.exam.repository.ExamSubmissionRepository;
import com.opencode.teachingplatform.exam.service.ExamAutoSubmitJob;
import com.opencode.teachingplatform.exam.service.ExamAutoSubmitService;
import com.opencode.teachingplatform.exam.service.ExamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考试超时自动交卷测试。
 *
 * <p>本类为 {@code @SpringBootTest}（非测试事务），保证 {@code REQUIRES_NEW} 的
 * 自动交卷事务能读到通过 service 真实提交的数据；测试数据在 {@link #cleanup()} 中按考试 id 清理。</p>
 *
 * <p>测试 profile 已关闭调度开关（{@code app.exam.auto-submit.enabled=false}），
 * 因此不依赖后台定时器，直接构造 {@link ExamAutoSubmitJob} 调用 {@code run()}。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ExamAutoSubmitJobTests {

    private static final CurrentUser TEACHER = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
    private static final CurrentUser STUDENT = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);

    /** 本类自建题库题目 id，避免依赖可能被其它非事务用例清空的 Flyway 种子题目。 */
    private static final long SINGLE_QUESTION_ID = 90001L;
    private static final long JUDGE_QUESTION_ID = 90002L;
    private static final long SUBJECTIVE_QUESTION_ID = 90003L;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamAutoSubmitService autoSubmitService;

    @Autowired
    private ExamSubmissionRepository examSubmissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdExamIds = new ArrayList<>();

    @BeforeEach
    void seedQuestions() {
        // 部分 @SpringBootTest 用例会清空题库表；本类自建题目，保证全量跑批时顺序无关
        jdbcTemplate.update("DELETE FROM exam_question WHERE question_id IN (?, ?, ?)",
                SINGLE_QUESTION_ID, JUDGE_QUESTION_ID, SUBJECTIVE_QUESTION_ID);
        jdbcTemplate.update("DELETE FROM question_bank WHERE id IN (?, ?, ?)",
                SINGLE_QUESTION_ID, JUDGE_QUESTION_ID, SUBJECTIVE_QUESTION_ID);
        jdbcTemplate.update(
                """
                INSERT INTO question_bank (id, code, type, stem, difficulty, options_json,
                                           answer_json, created_by, created_at, updated_at, school_id)
                VALUES (?, 'AUTOSUBMIT-SINGLE', 'SINGLE', '自动交卷单选题', 'MEDIUM',
                        '[{"key":"A","content":"正确"},{"key":"B","content":"错误"}]', '["A"]', 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """,
                SINGLE_QUESTION_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO question_bank (id, code, type, stem, difficulty, options_json,
                                           answer_json, created_by, created_at, updated_at, school_id)
                VALUES (?, 'AUTOSUBMIT-JUDGE', 'TRUE_FALSE', '自动交卷判断题', 'MEDIUM',
                        NULL, '["true"]', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """,
                JUDGE_QUESTION_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO question_bank (id, code, type, stem, difficulty, options_json,
                                           answer_json, created_by, created_at, updated_at, school_id)
                VALUES (?, 'AUTOSUBMIT-SUBJECTIVE', 'SUBJECTIVE', '自动交卷主观题', 'MEDIUM',
                        NULL, '["参考答案"]', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """,
                SUBJECTIVE_QUESTION_ID
        );
    }

    @AfterEach
    void cleanup() {
        for (Long examId : createdExamIds) {
            jdbcTemplate.update(
                    "DELETE FROM exam_answer WHERE exam_submission_id IN (SELECT id FROM exam_submission WHERE exam_id = ?)",
                    examId);
            jdbcTemplate.update("DELETE FROM exam_submission WHERE exam_id = ?", examId);
            jdbcTemplate.update("DELETE FROM score_record WHERE business_type = 'EXAM' AND business_id = ?", examId);
            jdbcTemplate.update("DELETE FROM exam_question WHERE exam_id = ?", examId);
            jdbcTemplate.update("DELETE FROM exam WHERE id = ?", examId);
        }
        createdExamIds.clear();
        jdbcTemplate.update("DELETE FROM exam_question WHERE question_id IN (?, ?, ?)",
                SINGLE_QUESTION_ID, JUDGE_QUESTION_ID, SUBJECTIVE_QUESTION_ID);
        jdbcTemplate.update("DELETE FROM question_bank WHERE id IN (?, ?, ?)",
                SINGLE_QUESTION_ID, JUDGE_QUESTION_ID, SUBJECTIVE_QUESTION_ID);
    }

    @Test
    void overdueInProgressSubmissionIsSettledAsGraded() {
        Long examId = createObjectiveOnlyExam();
        Long submissionId = startAndExpire(examId, OffsetDateTime.now().minusMinutes(5));

        job().run();

        ExamSubmission settled = examSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(ExamSubmissionStatus.GRADED);
        assertThat(settled.getSubmittedAt()).isNotNull();
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class, examId, STUDENT.id());
        assertThat(scoreRecordCount).isEqualTo(1);
    }

    @Test
    void overdueSubjectiveSubmissionIsSettledAsAutoSubmitted() {
        Long examId = createSubjectiveExam();
        Long submissionId = startAndExpire(examId, OffsetDateTime.now().minusMinutes(5));

        job().run();

        ExamSubmission settled = examSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(ExamSubmissionStatus.AUTO_SUBMITTED);
        assertThat(settled.getSubmittedAt()).isNotNull();
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class, examId, STUDENT.id());
        assertThat(scoreRecordCount).isZero();
    }

    @Test
    void submissionWithinGracePeriodIsNotSettled() {
        Long examId = createObjectiveOnlyExam();
        Long submissionId = startAndExpire(examId, OffsetDateTime.now().minusSeconds(30));

        job().run();

        ExamSubmission untouched = examSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ExamSubmissionStatus.IN_PROGRESS);
        assertThat(untouched.getSubmittedAt()).isNull();
    }

    @Test
    void nonInProgressSubmissionIsNotSettled() {
        Long examId = createObjectiveOnlyExam();
        Long submissionId = startAndExpire(examId, OffsetDateTime.now().minusMinutes(5));
        jdbcTemplate.update("UPDATE exam_submission SET status = 'SUBMITTED' WHERE id = ?", submissionId);

        job().run();

        ExamSubmission untouched = examSubmissionRepository.findById(submissionId).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ExamSubmissionStatus.SUBMITTED);
        assertThat(untouched.getSubmittedAt()).isNull();
    }

    @Test
    void singleFailureDoesNotBlockOtherSubmissions() {
        ExamSubmission first = submission(11L);
        ExamSubmission second = submission(12L);
        ExamSubmissionRepository repository = mock(ExamSubmissionRepository.class);
        ExamAutoSubmitService service = mock(ExamAutoSubmitService.class);
        // 阈值时间由 job 内部生成，桩必须用 any 匹配，否则纳秒差会导致 stub 不命中
        when(repository.findTop100ByStatusAndDeadlineAtLessThanEqualOrderByIdAsc(
                org.mockito.ArgumentMatchers.eq(ExamSubmissionStatus.IN_PROGRESS),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("结算异常")).when(service).autoSubmit(11L);

        ExamAutoSubmitJob job = new ExamAutoSubmitJob(repository, service);
        job.run();

        verify(service).autoSubmit(12L);
    }

    private ExamAutoSubmitJob job() {
        return new ExamAutoSubmitJob(examSubmissionRepository, autoSubmitService);
    }

    private Long createObjectiveOnlyExam() {
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                TEACHER,
                new ExamRequests.CreateExamRequest(
                        "自动交卷考试-" + System.nanoTime(),
                        "超时自动交卷测试",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "PUBLISHED",
                        ScoreVisibilityMode.IMMEDIATE,
                        List.of(
                                new ExamRequests.ExamQuestionItem(SINGLE_QUESTION_ID, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(JUDGE_QUESTION_ID, 2, 3.0)
                        ),
                        null
                )
        );
        Long examId = ((Number) created.get("id")).longValue();
        createdExamIds.add(examId);
        return examId;
    }

    private Long createSubjectiveExam() {
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                TEACHER,
                new ExamRequests.CreateExamRequest(
                        "自动交卷主观题-" + System.nanoTime(),
                        "超时自动交卷主观题测试",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "PUBLISHED",
                        ScoreVisibilityMode.IMMEDIATE,
                        List.of(new ExamRequests.ExamQuestionItem(SUBJECTIVE_QUESTION_ID, 1, 10.0)),
                        null
                )
        );
        Long examId = ((Number) created.get("id")).longValue();
        createdExamIds.add(examId);
        return examId;
    }

    private Long startAndExpire(Long examId, OffsetDateTime deadline) {
        @SuppressWarnings("unchecked")
        Map<String, Object> started = (Map<String, Object>) examService.startExam(STUDENT, examId);
        Long submissionId = ((Number) started.get("submissionId")).longValue();
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                deadline, submissionId);
        return submissionId;
    }

    private ExamSubmission submission(Long id) {
        // BaseEntity 未提供 setId，测试内用匿名子类提供固定 id
        return new ExamSubmission() {
            @Override
            public Long getId() {
                return id;
            }
        };
    }
}
