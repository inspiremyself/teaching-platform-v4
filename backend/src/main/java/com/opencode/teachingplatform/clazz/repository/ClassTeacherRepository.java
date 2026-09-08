package com.opencode.teachingplatform.clazz.repository;

import com.opencode.teachingplatform.clazz.entity.ClassTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    List<ClassTeacher> findByClassId(Long classId);
    List<ClassTeacher> findByTeacherUserId(Long teacherUserId);
    void deleteByClassId(Long classId);
    Optional<ClassTeacher> findByClassIdAndTeacherUserId(Long classId, Long teacherUserId);
    boolean existsByClassIdAndTeacherUserId(Long classId, Long teacherUserId);

    @Query("SELECT ct FROM ClassTeacher ct JOIN ClassRoom cr ON ct.classId = cr.id WHERE ct.teacherUserId = :teacherUserId AND cr.schoolId = :schoolId")
    List<ClassTeacher> findByTeacherUserIdAndSchoolId(@Param("teacherUserId") Long teacherUserId, @Param("schoolId") Long schoolId);

    @Query("SELECT COUNT(DISTINCT ct.classId) FROM ClassTeacher ct JOIN ClassRoom cr ON ct.classId = cr.id WHERE ct.teacherUserId = :teacherUserId AND cr.status = com.opencode.teachingplatform.common.enums.ClassStatus.ACTIVE")
    long countDistinctActiveClassesByTeacherUserId(@Param("teacherUserId") Long teacherUserId);

    @Query("SELECT DISTINCT ct.classId FROM ClassTeacher ct WHERE ct.teacherUserId = :teacherUserId")
    List<Long> findClassIdsByTeacherUserId(@Param("teacherUserId") Long teacherUserId);

    long countBySubjectId(Long subjectId);
}
