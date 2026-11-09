package com.opencode.teachingplatform.analysis.repository;

import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.grading.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScoreRecordRepository extends JpaRepository<ScoreRecord, Long> {
    List<ScoreRecord> findBySchoolIdAndStudentId(Long schoolId, Long studentId);
    Optional<ScoreRecord> findByBusinessTypeAndBusinessIdAndStudentIdAndSchoolId(
            BusinessType businessType, Long businessId, Long studentId, Long schoolId);
}
