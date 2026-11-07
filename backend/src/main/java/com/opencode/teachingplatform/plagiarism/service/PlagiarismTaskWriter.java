package com.opencode.teachingplatform.plagiarism.service;

import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.PlagiarismStatus;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查重任务统一写入器。
 *
 * <p>作业与实验的查重结果都按「业务类型 + 业务 id」唯一收敛到 {@code plagiarism_task}，
 * 由本组件统一 upsert，避免两处各写一份而口径漂移。</p>
 *
 * <p>写入使用 {@code REQUIRES_NEW} 独立事务：查重任务不是教学主流程的必要条件，
 * 写入失败不应回滚外层提交事务；调用方捕获异常后只降级查重率即可。</p>
 */
@Service
public class PlagiarismTaskWriter {

    /** {@code top_match_summary} 列长度上限，超长摘要按此截断。 */
    private static final int SUMMARY_MAX_LENGTH = 255;

    private final PlagiarismTaskRepository repository;

    public PlagiarismTaskWriter(PlagiarismTaskRepository repository) {
        this.repository = repository;
    }

    /**
     * 写入（或更新）一条已完成的查重任务。
     *
     * @param type            业务类型
     * @param businessId      业务记录 id（实验提交 / 作业提交）
     * @param studentId       学生用户 id
     * @param rate            相似率
     * @param topMatchSummary 最高匹配摘要，超长截断
     * @param rawResultJson   原始查重结果 JSON
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(BusinessType type, Long businessId, Long studentId,
                      Double rate, String topMatchSummary, String rawResultJson) {
        PlagiarismTask task = repository.findByBusinessTypeAndBusinessId(type, businessId)
                .orElseGet(PlagiarismTask::new);
        task.setBusinessType(type);
        task.setBusinessId(businessId);
        task.setStudentId(studentId);
        task.setStatus(PlagiarismStatus.COMPLETED);
        task.setSimilarityRate(rate);
        task.setTopMatchSummary(truncate(topMatchSummary));
        task.setRawResultJson(rawResultJson);
        repository.save(task);
    }

    private String truncate(String value) {
        return value == null || value.length() <= SUMMARY_MAX_LENGTH
                ? value
                : value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
