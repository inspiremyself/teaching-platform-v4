package com.opencode.teachingplatform.lab.behavior.repository;

import com.opencode.teachingplatform.lab.behavior.entity.LabSubmissionMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 实验提交行为指标仓储。
 *
 * <p>主键即 submission_id，懒算按当前页 id 集合批量 {@code findAllById}；
 * riskLevel 筛选用 {@code labId in 教师域} 收敛后按等级等值命中（D10）。</p>
 */
public interface LabSubmissionMetricsRepository extends JpaRepository<LabSubmissionMetrics, Long> {

    /** riskLevel 筛选命中集：教师域实验内按等级等值查询（LOW/MEDIUM/HIGH）。 */
    List<LabSubmissionMetrics> findByLabIdInAndRiskLevel(Collection<Long> labIds, String riskLevel);

    /** NONE 筛选的补集来源：教师域实验内全部已建 metrics 行。 */
    List<LabSubmissionMetrics> findByLabIdIn(Collection<Long> labIds);
}
