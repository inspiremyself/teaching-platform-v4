package com.opencode.teachingplatform.lab.service;

import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.lab.entity.LabSubmission;
import com.opencode.teachingplatform.lab.repository.LabSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实验提交行「首次写入」的并发领取器。
 *
 * <p><b>职责：</b>只负责在独立事务内保证 {@code (labId, studentId)} 唯一提交行存在，
 * 不参与答案、评分、提交等业务写入。</p>
 *
 * <p><b>为什么必须独立事务（REQUIRES_NEW）：</b>调用方 {@code LabService} 的写方法已在外层事务中，
 * 同一事务内插入撞唯一键会把外层标记为 rollback-only，导致「并发首次自动保存两边都成功」无法实现。
 * 独立事务失败只回滚它自己。</p>
 *
 * <p><b>调用约定：</b>调用方必须先普通读快路径，读不到才调用本方法；捕获
 * {@link org.springframework.dao.DataIntegrityViolationException} 后一律通过
 * {@code findByLabIdAndStudentIdForUpdate} 加锁重读真实状态。</p>
 */
@Service
public class LabSubmissionClaimService {

    private final LabSubmissionRepository submissionRepository;

    public LabSubmissionClaimService(LabSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    /**
     * 查找或创建学生在实验下的提交行。
     *
     * @param labId     实验 id
     * @param studentId 学生 id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(Long labId, Long studentId) {
        if (submissionRepository.findByLabIdAndStudentId(labId, studentId).isPresent()) {
            return;
        }
        LabSubmission created = new LabSubmission();
        created.setLabId(labId);
        created.setStudentId(studentId);
        created.setSubmitStatus(SubmissionStatus.SAVED);
        created.setPlagiarismRate(0D);
        created.setTotalScore(0D);
        // 立即 flush，让唯一键冲突在本方法内暴露，便于调用方就地捕获
        submissionRepository.saveAndFlush(created);
    }
}
