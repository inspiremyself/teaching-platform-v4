package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.CoursewareAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CoursewareAssignmentRepository extends JpaRepository<CoursewareAssignment, Long> {

    @Query(value = "SELECT * FROM courseware_assignment WHERE school_id = :schoolId AND class_id = :classId "
            + "ORDER BY created_at DESC, assignment_group_id, display_order ASC", nativeQuery = true)
    List<CoursewareAssignment> findBySchoolAndClass(@Param("schoolId") Long schoolId,
                                                    @Param("classId") Long classId);

    @Query(value = "SELECT * FROM courseware_assignment WHERE school_id = :schoolId AND "
            + "((target_type = 'CLASS' AND class_id = :classId AND target_id = :classId) "
            + "OR (target_type = 'STUDENT' AND target_id = :studentUserId)) "
            + "ORDER BY created_at DESC, assignment_group_id, display_order ASC", nativeQuery = true)
    List<CoursewareAssignment> findVisibleToStudent(@Param("schoolId") Long schoolId,
                                                    @Param("classId") Long classId,
                                                    @Param("studentUserId") Long studentUserId);

    /** 按课件统计布置引用数，用于删除课件前的 409 门禁（平台课件可跨校布置，不按 school_id 过滤）。 */
    long countByCoursewareId(Long coursewareId);
}
