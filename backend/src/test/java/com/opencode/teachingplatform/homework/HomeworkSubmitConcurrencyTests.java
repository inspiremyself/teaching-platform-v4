package com.opencode.teachingplatform.homework;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.homework.dto.HomeworkRequests;
import com.opencode.teachingplatform.homework.service.HomeworkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 作业提交并发首存的集成测试（技术方案 §7.3）。
 *
 * <p><b>验证目标：</b>两个线程同刻提交同一份尚未产生提交行的作业，
 * 因 {@code (homework_id, student_id)} 唯一键竞争，慢路径 claim 会有一方失败；
 * 失败方必须就地捕获唯一键冲突并按幂等成功返回，不得把异常抛给前端，
 * 最终只留下 1 条提交行，且每个题目只留 1 条答案行。</p>
 *
 * <p><b>事务策略：</b>并发用例需要真实提交（不能整体回滚），
 * 因此用 {@link TransactionTemplate} 显式提交夹具，线程内由 service 自身事务提交。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class HomeworkSubmitConcurrencyTests {

    /** 本用例专用作业 id，避开 Flyway 种子作业（-1/-2/-3）。 */
    private static final long HOMEWORK_ID = 9_200_001L;
    /** 本用例专用作业题目 id。 */
    private static final long HOMEWORK_QUESTION_ID = 9_200_101L;
    /** 种子学生 2（演示学生，属于班级 1）。 */
    private static final long STUDENT_ID = 2L;
    /** 种子班级 1。 */
    private static final long CLASS_ID = 1L;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // 独立内存库 + 加长锁等待：让后到事务有机会阻塞到前一事务提交，而不是直接超时
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:teaching_platform_homework_concurrency_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @AfterEach
    void cleanupFixture() {
        // 按业务关联级联清理：答案 → 查重任务 → 成绩 → 提交 → 题目 → 作业
        jdbcTemplate.update(
                "DELETE FROM homework_answer WHERE homework_submission_id IN "
                        + "(SELECT id FROM homework_submission WHERE homework_id = ?)",
                HOMEWORK_ID
        );
        jdbcTemplate.update(
                "DELETE FROM plagiarism_task WHERE business_type = 'HOMEWORK' AND business_id IN "
                        + "(SELECT id FROM homework_submission WHERE homework_id = ?)",
                HOMEWORK_ID
        );
        jdbcTemplate.update(
                "DELETE FROM score_record WHERE business_type = 'HOMEWORK' AND business_id IN "
                        + "(SELECT id FROM homework_submission WHERE homework_id = ?)",
                HOMEWORK_ID
        );
        jdbcTemplate.update("DELETE FROM homework_submission WHERE homework_id = ?", HOMEWORK_ID);
        jdbcTemplate.update("DELETE FROM homework_question WHERE homework_id = ?", HOMEWORK_ID);
        jdbcTemplate.update("DELETE FROM homework WHERE id = ?", HOMEWORK_ID);
    }

    @Test
    void concurrentSubmitCreatesSingleSubmissionAndSingleAnswerPerQuestion() throws Exception {
        seedHomeworkFixture();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        homeworkService.submitHomework(student(), HOMEWORK_ID,
                                new HomeworkRequests.SubmitHomeworkRequest(List.of(
                                        new HomeworkRequests.SubmitHomeworkAnswerItem(
                                                HOMEWORK_QUESTION_ID, null, "并发提交答案", null, null))));
                        return null;
                    } catch (Throwable ex) {
                        return ex;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            // 两边都必须成功：后到方由锁内状态判定按幂等成功返回
            for (Future<Throwable> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isNull();
            }
        } finally {
            executor.shutdownNow();
        }

        List<Map<String, Object>> submissions = jdbcTemplate.queryForList(
                "SELECT id, submit_status FROM homework_submission WHERE homework_id = ? AND student_id = ?",
                HOMEWORK_ID,
                STUDENT_ID
        );
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).get("submit_status")).isEqualTo("SUBMITTED");
        Long submissionId = ((Number) submissions.get(0).get("id")).longValue();

        Integer answerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework_answer WHERE homework_submission_id = ? AND homework_question_id = ?",
                Integer.class,
                submissionId,
                HOMEWORK_QUESTION_ID
        );
        assertThat(answerCount).isEqualTo(1);

        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'HOMEWORK' AND business_id = ?",
                Integer.class,
                submissionId
        );
        assertThat(scoreRecordCount).isZero();
    }

    /**
     * 夹具：以独立事务写入已发布作业与一道主观题，确保两个并发线程都能看到已提交数据。
     */
    private void seedHomeworkFixture() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM homework_submission WHERE homework_id = ?",
                    HOMEWORK_ID
            );
            jdbcTemplate.update("DELETE FROM homework_question WHERE homework_id = ?", HOMEWORK_ID);
            jdbcTemplate.update("DELETE FROM homework WHERE id = ?", HOMEWORK_ID);
            jdbcTemplate.update(
                    """
                    INSERT INTO homework (id, title, description, class_id, start_at, due_at, attachment_path,
                                          created_by, status, created_at, updated_at)
                    VALUES (?, '并发提交作业', '并发首存幂等验证', ?,
                            TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP),
                            NULL, 1, 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    HOMEWORK_ID,
                    CLASS_ID
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO homework_question (id, homework_id, question_id, sort_order, question_score,
                                                   question_snapshot_json, created_at, updated_at)
                    VALUES (?, ?, NULL, 1, 10,
                            '{"type":"SUBJECTIVE","stem":"并发提交题目","options":[],"answer":[],"scoringConfig":{"keywords":[]},"analysisText":"","difficulty":"MEDIUM","fromBank":false}',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    HOMEWORK_QUESTION_ID,
                    HOMEWORK_ID
            );
        });
    }

    private static CurrentUser student() {
        return new CurrentUser(STUDENT_ID, "20260001", "演示学生", UserRole.STUDENT, 1L, "homework-concurrency-session");
    }
}
