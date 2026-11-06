package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.LabSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 提交场景专用查询：对“单学生 + 单实验”的提交行加悲观写锁。
     *
     * <p>学生正式提交时先锁定该行再判定状态，确保并发重复提交只有一个事务能把
     * {@code SAVED} 推进到 {@code SUBMITTED}，后到的事务阻塞后读到最新状态并被拒绝。</p>
     *
     * @param labId     实验 id
     * @param studentId 学生 id
     * @return 提交行（不存在时为空）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LabSubmission s where s.labId = :labId and s.studentId = :studentId")
    Optional<LabSubmission> findByLabIdAndStudentIdForUpdate(
            @Param("labId") Long labId,
            @Param("studentId") Long studentId);

    Optional<LabSubmission> findFirstByLabIdAndStudentIdOrderByIdDesc(Long labId, Long studentId);

    /**
     * 统计某实验下的提交数。
     *
     * <p>用于实验题项结构变更、状态回退与所属班级变更的前置校验：
     * 只要已有学生作答，这些操作都会破坏已产生的答案与成绩。</p>
     *
     * @param labId 实验 id
     * @return 提交数
     */
    long countByLabId(Long labId);

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
