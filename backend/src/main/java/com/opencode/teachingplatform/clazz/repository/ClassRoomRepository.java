package com.opencode.teachingplatform.clazz.repository;

import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.common.enums.ClassStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {
    List<ClassRoom> findBySchoolId(Long schoolId);
    List<ClassRoom> findBySchoolIdAndStatus(Long schoolId, ClassStatus status);
    boolean existsBySchoolIdAndCode(Long schoolId, String code);
    Optional<ClassRoom> findByIdAndSchoolId(Long id, Long schoolId);

    @Query("SELECT DISTINCT cr FROM ClassRoom cr JOIN ClassTeacher ct ON cr.id = ct.classId WHERE cr.schoolId = :schoolId AND ct.teacherUserId = :teacherUserId")
    List<ClassRoom> findBySchoolIdAndTeacherUserId(@Param("schoolId") Long schoolId, @Param("teacherUserId") Long teacherUserId);
}
