package com.opencode.teachingplatform.exam;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.exam.service.ExamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 考试交卷与开始考试的并发互斥集成测试（技术方案 §8.1 / TD16–TD17）。
 *
 * <p>并发用例必须使用真实事务：{@code @DataJpaTest} 会让整个用例回滚，无法观察两个线程之间
 * 的隔离与唯一键冲突，因此这里用 {@code @SpringBootTest} + 独立 H2 内存库。H2 悲观锁行为
 * 依赖 {@code LOCK_TIMEOUT}，加长等待时间让后到事务能阻塞到前一事务提交，而不是直接超时。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ExamSubmitConcurrencyTests {

    /** 种子教师 1（演示教师，班级 1 的负责教师）。 */
    private static final long TEACHER_ID = 1L;
    /** 种子学生 2（演示学生，属于班级 1）。 */
    private static final long STUDENT_ID = 2L;
    /** 种子班级 1（SE2026-1）。 */
    private static final long CLASS_ID = 1L;

    @Autowired
    private ExamService examService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 本用例创建的考试 id，用于 @AfterEach 清理，避免污染共享内存库。 */
    private final List<Long> createdExamIds = new ArrayList<>();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // 独立内存库 + 加长锁等待：让后到事务有机会阻塞到前一事务提交。
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:teaching_platform_exam_concurrency_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @AfterEach
    void cleanupExamFixtures() {
        for (Long examId : createdExamIds) {
            jdbcTemplate.update("""
                    DELETE FROM exam_answer
                     WHERE exam_submission_id IN (SELECT id FROM exam_submission WHERE exam_id = ?)
                    """, examId);
            jdbcTemplate.update("DELETE FROM exam_submission WHERE exam_id = ?", examId);
            jdbcTemplate.update("DELETE FROM exam_question WHERE exam_id = ?", examId);
            jdbcTemplate.update(
                    "DELETE FROM score_record WHERE business_type = 'EXAM' AND business_id = ?", examId);
            jdbcTemplate.update("DELETE FROM exam WHERE id = ?", examId);
        }
        createdExamIds.clear();
    }

    @Test
    void concurrentSubmitAllowsOnlyOneWinner() throws Exception {
        Long examId = createPublishedObjectiveOnlyExam();
        seedInProgressSubmission(examId);

        List<Object> outcomes = runConcurrently(
                () -> examService.submitExam(student(), examId, submitObjectiveAnswer()));

        int successCount = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Object outcome : outcomes) {
            if (outcome instanceof Throwable failure) {
                failures.add(failure);
            } else {
                successCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(failures).hasSize(1);
        // 后到线程必须被状态机拒绝为业务异常，不能是数据库异常或 500
        assertThat(failures.get(0)).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failures.get(0)).getCode()).isEqualTo(40000);
        assertThat(failures.get(0).getMessage()).contains("考试已提交，不能重复提交");

        // 库里只留下一份提交结果与一行答案
        assertThat(countAnswers(examId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_submission WHERE exam_id = ? AND student_id = ?",
                String.class, examId, STUDENT_ID)).isEqualTo("GRADED");
    }

    @Test
    void concurrentStartCreatesSingleSubmissionWithoutServerError() throws Exception {
        Long examId = createPublishedObjectiveOnlyExam();

        List<Object> outcomes = runConcurrently(() -> examService.startExam(student(), examId));

        // 允许两种结果：两线程拿到同一提交，或一成功一业务异常（唯一键冲突后重读兜底）。
        // 关键约束：不得出现非 BusinessException 的异常（即不得 500），且只产生一条提交。
        List<Throwable> failures = new ArrayList<>();
        List<Long> submissionIds = new ArrayList<>();
        for (Object outcome : outcomes) {
            if (outcome instanceof Throwable failure) {
                failures.add(failure);
            } else {
                submissionIds.add(((Number) ((Map<?, ?>) outcome).get("submissionId")).longValue());
            }
        }

        assertThat(failures).allSatisfy(failure -> {
            assertThat(failure).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) failure).getCode()).isIn(40000, 40900);
        });
        assertThat(submissionIds).isNotEmpty();
        // 所有成功的线程必须指向同一条提交
        assertThat(submissionIds.stream().distinct().toList()).hasSize(1);
        assertThat(countSubmissions(examId)).isEqualTo(1);
    }

    /**
     * 两线程同刻起跑：ready 保证两个线程都已就绪，start 放行让它们尽量同时进入业务方法。
     *
     * @return 每个线程的结果：正常返回值为业务对象，异常返回 Throwable
     */
    private List<Object> runConcurrently(Callable<Object> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        return action.call();
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 创建一份仅含单选题（题库 1，5 分）的已发布考试，默认 IMMEDIATE 可见性。
     */
    @SuppressWarnings("unchecked")
    private Long createPublishedObjectiveOnlyExam() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher(),
                new ExamRequests.CreateExamRequest(
                        "并发测试考试-" + System.nanoTime(),
                        "考试并发用例专用",
                        CLASS_ID,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "PUBLISHED",
                        ScoreVisibilityMode.IMMEDIATE,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        );
        Long examId = ((Number) created.get("id")).longValue();
        createdExamIds.add(examId);
        return examId;
    }

    /**
     * 以独立事务写入 IN_PROGRESS 提交行，确保两个并发线程都能看到已提交数据。
     */
    private void seedInProgressSubmission(Long examId) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                INSERT INTO exam_submission
                    (exam_id, student_id, started_at, deadline_at, status, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, examId, STUDENT_ID, OffsetDateTime.now().plusMinutes(30)));
    }

    private Integer countAnswers(Long examId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_answer
                 WHERE exam_submission_id IN (SELECT id FROM exam_submission WHERE exam_id = ?)
                """, Integer.class, examId);
    }

    private Integer countSubmissions(Long examId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_submission WHERE exam_id = ?", Integer.class, examId);
    }

    private static ExamRequests.SubmitExamRequest submitObjectiveAnswer() {
        return new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        ));
    }

    private static CurrentUser teacher() {
        return new CurrentUser(TEACHER_ID, "t9001", "演示教师", UserRole.TEACHER);
    }

    private static CurrentUser student() {
        return new CurrentUser(STUDENT_ID, "20260001", "演示学生", UserRole.STUDENT);
    }
}
