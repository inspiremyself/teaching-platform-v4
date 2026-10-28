package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.LabStepAnswerLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabStepAnswerLogRepository extends JpaRepository<LabStepAnswerLog, Long> {
    Optional<LabStepAnswerLog> findFirstByLabStepAnswerIdOrderByFillTimeDescIdDesc(Long labStepAnswerId);

    /** 行为指标聚合数据源：批量取一份提交全部答案的保存快照（D2 保存次数单一事实源）。 */
    List<LabStepAnswerLog> findByLabStepAnswerIdIn(Collection<Long> labStepAnswerIds);
}
