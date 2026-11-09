package com.opencode.teachingplatform.exam.repository;

import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findBySchoolIdAndCreatedBy(Long schoolId, Long createdBy);
    List<Exam> findBySchoolIdAndClassIdAndStatus(Long schoolId, Long classId, ActivityStatus status);
    List<Exam> findBySchoolIdAndClassId(Long schoolId, Long classId);
    Optional<Exam> findByIdAndSchoolId(Long id, Long schoolId);
}
