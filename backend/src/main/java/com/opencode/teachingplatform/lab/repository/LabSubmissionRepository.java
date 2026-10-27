package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.LabSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 实验提交仓储。
 *
 * <p>额外实现 {@link JpaSpecificationExecutor}，用于教师报告队列的动态条件 + 真分页查询：
 * 只有提交表是随实验数 × 班级人数增长的大表，分页收益全部落在这一层。</p>
 */
public interface LabSubmissionRepository extends JpaRepository<LabSubmission, Long>,
        JpaSpecificationExecutor<LabSubmission> {

    Optional<LabSubmission> findByLabIdAndStudentId(Long labId, Long studentId);

    Optional<LabSubmission> findFirstByLabIdAndStudentIdOrderByIdDesc(Long labId, Long studentId);

    List<LabSubmission> findByStudentId(Long studentId);

    List<LabSubmission> findByLabId(Long labId);

    List<LabSubmission> findByLabIdIn(List<Long> labIds);

    /**
     * 按提交状态分组计数，供教师报告队列的全局统计卡使用。
     *
     * @param labIds 教师本人拥有的实验 id 集合（不可为空集合，调用方需先判空）
     * @return 每行是 {@code [SubmissionStatus, Long count]} 二元组
     */
    @Query("""
            select s.submitStatus, count(s) from LabSubmission s
            where s.labId in :labIds group by s.submitStatus
            """)
    List<Object[]> countGroupByStatus(@Param("labIds") Collection<Long> labIds);
}
