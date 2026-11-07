package com.opencode.teachingplatform.homework.service;

import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.homework.entity.HomeworkSubmission;
import com.opencode.teachingplatform.homework.repository.HomeworkSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作业提交行「首次写入」的并发领取器。
 *
 * <p><b>职责：</b>只负责在独立事务内保证 {@code (homeworkId, studentId)} 唯一提交行存在，
 * 不参与答案、查重、评分等业务写入。</p>
 *
 * <p><b>为什么必须独立事务（REQUIRES_NEW）：</b>调用方 {@code submitHomework} 已有外层事务，
 * 若在同一事务内插入并撞上唯一键 {@code uk_homework_submission}，异常会把外层事务标记为
 * rollback-only，导致「并发重复提交按幂等成功返回」无法实现。独立事务失败只回滚它自己。</p>
 *
 * <p><b>调用约定：</b>调用方必须先做普通读快路径，仅当读不到时才调用本方法；捕获
 * {@link org.springframework.dao.DataIntegrityViolationException} 后一律通过
 * {@code findByHomeworkIdAndStudentIdForUpdate} 加锁重读真实状态（REPEATABLE READ 下普通读会读到旧快照）。</p>
 */
@Service
public class HomeworkSubmissionClaimService {

    private final HomeworkSubmissionRepository submissionRepository;

    public HomeworkSubmissionClaimService(HomeworkSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    /**
     * 查找或创建学生在该作业下的提交行。
     *
     * @param homeworkId 作业 id
     * @param studentId  学生 id
     * @return 本次调用时刻的提交状态；不存在时插入 {@code SAVED} 行后返回 {@code SAVED}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubmissionStatus claim(Long homeworkId, Long studentId) {
        return submissionRepository.findByHomeworkIdAndStudentId(homeworkId, studentId)
                .map(HomeworkSubmission::getSubmitStatus)
                .orElseGet(() -> {
                    HomeworkSubmission created = new HomeworkSubmission();
                    created.setHomeworkId(homeworkId);
                    created.setStudentId(studentId);
                    created.setSubmitStatus(SubmissionStatus.SAVED);
                    created.setPlagiarismRate(0D);
                    created.setTotalScore(0D);
                    // 立即 flush，让唯一键冲突在本方法内暴露，便于调用方就地捕获
                    submissionRepository.saveAndFlush(created);
                    return SubmissionStatus.SAVED;
                });
    }
}
