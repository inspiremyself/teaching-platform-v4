package com.opencode.teachingplatform.exam.service;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.entity.Exam;
import com.opencode.teachingplatform.exam.entity.ExamSubmission;
import com.opencode.teachingplatform.exam.enums.ExamSubmissionStatus;
import com.opencode.teachingplatform.exam.repository.ExamRepository;
import com.opencode.teachingplatform.exam.repository.ExamSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 考试超时自动交卷服务。
 *
 * <p>每条提交独立事务（{@code REQUIRES_NEW}）结算，单条失败不影响同批其它记录。
 * 进入结算前先加行锁并复查状态与宽限期，与手动交卷串行，已结算记录直接跳过。</p>
 */
@Service
public class ExamAutoSubmitService {

    private final ExamSubmissionRepository submissionRepository;
    private final ExamRepository examRepository;
    private final ExamService examService;

    public ExamAutoSubmitService(ExamSubmissionRepository submissionRepository,
                                 ExamRepository examRepository,
                                 ExamService examService) {
        this.submissionRepository = submissionRepository;
        this.examRepository = examRepository;
        this.examService = examService;
    }

    /**
     * 对单条超时提交执行自动交卷。
     *
     * <p>以下情况直接跳过：提交不存在、已不是 {@code IN_PROGRESS}、无截止时间、
     * 或当前时间尚未越过 {@code deadlineAt + SUBMIT_GRACE_SECONDS}。
     * 结算使用空 payload，所有题目回退已持久化草稿。</p>
     *
     * @param submissionId 提交 id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoSubmit(Long submissionId) {
        ExamSubmission submission = submissionRepository.findByIdForUpdate(submissionId).orElse(null);
        if (submission == null || submission.getStatus() != ExamSubmissionStatus.IN_PROGRESS) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (submission.getDeadlineAt() == null
                || now.isBefore(submission.getDeadlineAt().plusSeconds(ExamService.SUBMIT_GRACE_SECONDS))) {
            return;
        }
        Exam exam = examRepository.findById(submission.getExamId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        examService.finalizeExamSubmission(submission, exam, Map.of());
    }
}
