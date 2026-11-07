package com.opencode.teachingplatform.lab;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.lab.behavior.service.LabBehaviorMetricsService;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.service.LabService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实验提交并发互斥的集成测试（技术方案 §8.5）。
 *
 * <p>验证 {@code LabService#submitLab} 先对 {@code experiment_submission} 行加悲观写锁、
 * 再判状态：两个线程同刻提交同一份「已保存」实验时，只有一个能成功，
 * 另一个必须读到 {@code SUBMITTED} 并被状态机拒绝，最终库中只留下一次提交结果。</p>
 *
 * <p>并发用例必须使用真实事务（不能像 {@code @DataJpaTest} 那样整体回滚），
 * 因此这里用 {@link TransactionTemplate} 显式提交夹具数据，线程内由 service 自身事务提交。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class LabSubmitConcurrencyTests {

    /** 种子实验 1003（PUBLISHED、班级 1、必填实验小结）。 */
    private static final long LAB_ID = 1003L;
    /** 种子学生 2（演示学生，属于班级 1）。 */
    private static final long STUDENT_ID = 2L;
    /** 本用例专用提交行，避开种子中的 3001/3002。 */
    private static final long SUBMISSION_ID = 9303L;
    /** 种子实验 1003 的第 2 步（TEXT 题型），用于并发首次保存答案。 */
    private static final long TEXT_STEP_ID = 2006L;

    @Autowired
    private LabService labService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 提交成功会在事务内联动行为指标落库；并发用例只关心提交互斥，
     * 指标由 LabBehaviorMetricsServiceTests 覆盖，这里用 mock 隔离。
     */
    @MockBean
    private LabBehaviorMetricsService labBehaviorMetricsService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // 独立内存库 + 加长锁等待：让后到事务有机会阻塞到前一事务提交，而不是直接超时。
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:teaching_platform_concurrency_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @AfterEach
    void cleanupSubmissionFixture() {
        // 答案按业务关联清理：并发首存用例的提交行 id 是自动生成的，不能只按固定 SUBMISSION_ID 删
        // 先删答案行为日志（外键指向 experiment_answer），再删答案行
        jdbcTemplate.update(
                """
                DELETE FROM lab_step_answer_log WHERE lab_step_answer_id IN (
                    SELECT a.id FROM experiment_answer a
                    JOIN experiment_submission s ON a.experiment_submission_id = s.id
                    WHERE s.experiment_id = ? AND s.student_id = ?
                )
                """,
                LAB_ID,
                STUDENT_ID
        );
        jdbcTemplate.update(
                """
                DELETE FROM experiment_answer WHERE experiment_submission_id IN (
                    SELECT id FROM experiment_submission WHERE experiment_id = ? AND student_id = ?
                )
                """,
                LAB_ID,
                STUDENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM experiment_submission WHERE experiment_id = ? AND student_id = ?",
                LAB_ID,
                STUDENT_ID
        );
        jdbcTemplate.update(
                "DELETE FROM score_record WHERE business_type = 'LAB' AND business_id = ? AND student_id = ?",
                LAB_ID,
                STUDENT_ID
        );
    }

    @Test
    void concurrentSubmitOnlyAllowsOneWinner() throws Exception {
        seedSavedSubmission();

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
                        labService.submitLab(student(), LAB_ID, new LabRequests.SubmitLabRequest("并发提交小结"));
                        return null;
                    } catch (BusinessException ex) {
                        return ex;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successCount = 0;
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get(30, TimeUnit.SECONDS);
                if (failure == null) {
                    successCount++;
                } else {
                    failures.add(failure);
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("实验已提交，不能重复提交");
            assertThat(((BusinessException) failures.get(0)).getCode()).isEqualTo(40000);
        } finally {
            executor.shutdownNow();
        }

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT submit_status, submitted_at FROM experiment_submission "
                        + "WHERE experiment_id = ? AND student_id = ?",
                LAB_ID,
                STUDENT_ID
        );
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("submit_status")).isEqualTo("SUBMITTED");
        assertThat(rows.get(0).get("submitted_at")).isNotNull();
    }

    /**
     * 并发首次保存答案：两个线程同时 saveAnswer 同一实验同一步骤。
     *
     * <p>期望：claim 的唯一键冲突被就地捕获并按幂等处理，两边都成功；
     * 最终只留 1 条提交行与 1 条答案行（方案 TD22/TD23）。</p>
     */
    @Test
    void concurrentFirstSaveAnswerCreatesSingleSubmissionAndAnswer() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM experiment_answer WHERE experiment_submission_id IN "
                            + "(SELECT id FROM experiment_submission WHERE experiment_id = ? AND student_id = ?)",
                    LAB_ID,
                    STUDENT_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM experiment_submission WHERE experiment_id = ? AND student_id = ?",
                    LAB_ID,
                    STUDENT_ID
            );
        });

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
                        labService.saveAnswer(student(), LAB_ID, TEXT_STEP_ID,
                                new LabRequests.SaveStepAnswerRequest(
                                        "实验小结应在提交前完成",
                                        "{\"kind\":\"text\",\"text\":\"实验小结应在提交前完成\"}"));
                        return null;
                    } catch (Throwable ex) {
                        return ex;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<Throwable> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isNull();
            }
        } finally {
            executor.shutdownNow();
        }

        Integer submissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM experiment_submission WHERE experiment_id = ? AND student_id = ?",
                Integer.class, LAB_ID, STUDENT_ID);
        assertThat(submissionCount).isEqualTo(1);
        Integer answerCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM experiment_answer a
                JOIN experiment_submission s ON a.experiment_submission_id = s.id
                WHERE s.experiment_id = ? AND s.student_id = ?
                """,
                Integer.class, LAB_ID, STUDENT_ID);
        assertThat(answerCount).isEqualTo(1);
    }

    /**
     * 提交夹具：以独立事务写入 SAVED 提交行，确保两个并发线程都能看到已提交数据。
     */
    private void seedSavedSubmission() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM experiment_submission WHERE experiment_id = ? AND student_id = ?",
                    LAB_ID,
                    STUDENT_ID
            );
            jdbcTemplate.update(
                    "INSERT INTO experiment_submission "
                            + "(id, experiment_id, student_id, submit_status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'SAVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    SUBMISSION_ID,
                    LAB_ID,
                    STUDENT_ID
            );
        });
    }

    private static CurrentUser student() {
        return new CurrentUser(STUDENT_ID, "20260001", "演示学生", UserRole.STUDENT, 1L, "concurrency-session");
    }
}
