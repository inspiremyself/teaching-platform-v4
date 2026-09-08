package com.opencode.teachingplatform.school.repository;

import com.opencode.teachingplatform.common.enums.SubjectStatus;
import com.opencode.teachingplatform.school.entity.SchoolSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolSubjectRepository extends JpaRepository<SchoolSubject, Long> {

    List<SchoolSubject> findBySchoolId(Long schoolId);

    List<SchoolSubject> findBySchoolIdAndStatus(Long schoolId, SubjectStatus status);

    Optional<SchoolSubject> findByIdAndSchoolId(Long id, Long schoolId);

    Optional<SchoolSubject> findBySchoolIdAndCode(Long schoolId, String code);

    boolean existsBySchoolIdAndCode(Long schoolId, String code);
}
