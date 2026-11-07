package com.opencode.teachingplatform.exam.service;

import com.opencode.teachingplatform.exam.entity.ExamSubmission;
import com.opencode.teachingplatform.exam.enums.ExamSubmissionStatus;
import com.opencode.teachingplatform.exam.repository.ExamSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 考试超时自动交卷调度任务。
 *
 * <p>每 60 秒扫描一次已超过 {@code deadlineAt + SUBMIT_GRACE_SECONDS} 仍为 {@code IN_PROGRESS}
 * 的提交，逐条调用 {@link ExamAutoSubmitService} 独立事务结算；单条异常只记 WARN，
 * 不影响同批其它记录。</p>
 *
 * <p>通过 {@code app.exam.auto-submit.enabled=false} 可关闭（单测 profile 默认关闭），
 * 开关缺省视为开启，路径 B 无需额外配置。</p>
 */
@Component
@ConditionalOnProperty(name = "app.exam.auto-submit.enabled", havingValue = "true", matchIfMissing = true)
public class ExamAutoSubmitJob {

    private static final Logger log = LoggerFactory.getLogger(ExamAutoSubmitJob.class);

    /** 单批扫描上限，与仓储方法 {@code findTop100...} 保持一致。 */
    private static final int BATCH_SIZE = 100;

    private final ExamSubmissionRepository submissionRepository;
    private final ExamAutoSubmitService autoSubmitService;

    public ExamAutoSubmitJob(ExamSubmissionRepository submissionRepository,
                             ExamAutoSubmitService autoSubmitService) {
        this.submissionRepository = submissionRepository;
        this.autoSubmitService = autoSubmitService;
    }

    /**
     * 扫描并结算超时提交。
     */
    @Scheduled(fixedDelay = 60000)
    public void run() {
        OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(ExamService.SUBMIT_GRACE_SECONDS);
        List<ExamSubmission> pending = submissionRepository
                .findTop100ByStatusAndDeadlineAtLessThanEqualOrderByIdAsc(
                        ExamSubmissionStatus.IN_PROGRESS, threshold);
        for (ExamSubmission submission : pending) {
            try {
                autoSubmitService.autoSubmit(submission.getId());
            } catch (Exception ex) {
                log.warn("考试自动交卷失败: submissionId={}", submission.getId(), ex);
            }
        }
    }
}
