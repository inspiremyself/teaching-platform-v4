package com.opencode.teachingplatform.lab.behavior.repository;

import com.opencode.teachingplatform.lab.behavior.entity.BehaviorEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 行为事件仓储。
 *
 * <p>核心查询路径均落在 {@code (biz_type, lab_id, student_id, id)} 索引上：
 * 指标聚合按归属键顺序扫描，懒算批量取各归属键的最新事件 id。</p>
 */
public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {

    /** 幂等去重：批量上报前过滤已存在的 event_uuid（D4）。 */
    List<BehaviorEvent> findByEventUuidIn(Collection<String> eventUuids);

    /** 指标聚合数据源：按归属键全量取事件，id 升序即到达顺序。 */
    List<BehaviorEvent> findByBizTypeAndLabIdAndStudentIdOrderByIdAsc(String bizType, Long labId, Long studentId);

    /** 行为时间线：按发生时间升序分页（上限 500 条由调用方 Pageable 控制），total 供 timelineTotal。 */
    Page<BehaviorEvent> findByBizTypeAndLabIdAndStudentId(String bizType, Long labId, Long studentId, Pageable pageable);

    /**
     * 懒算水位判定：批量取各 (labId, studentId) 归属键的最新事件 id。
     *
     * <p>按 labIds 粗筛后 group by 出 (labId, studentId) 二元组，调用方在内存中
     * 再按当前页实际的归属键对过滤——避免 JPQL 元组 IN 的方言兼容风险，
     * 且教师域单页涉及的实验数极少，粗筛代价可控。</p>
     *
     * @return 每行是 {@code [labId, studentId, maxEventId]} 三元组
     */
    @Query("""
            select e.labId, e.studentId, max(e.id) from BehaviorEvent e
            where e.bizType = :bizType and e.labId in :labIds
            group by e.labId, e.studentId
            """)
    List<Object[]> findMaxIdByBizTypeAndLabIdInGroupByStudent(@Param("bizType") String bizType,
                                                              @Param("labIds") Collection<Long> labIds);
}
