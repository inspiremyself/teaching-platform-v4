package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.Lab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabRepository extends JpaRepository<Lab, Long> {
    List<Lab> findBySchoolIdAndCreatedByOrderByIdDesc(Long schoolId, Long createdBy);
    List<Lab> findBySchoolIdAndClassIdInAndStatusOrderByIdDesc(Long schoolId, Collection<Long> classIds,
            com.opencode.teachingplatform.common.enums.ActivityStatus status);
    Optional<Lab> findByIdAndSchoolIdAndCreatedBy(Long id, Long schoolId, Long createdBy);
    Optional<Lab> findByIdAndSchoolId(Long id, Long schoolId);
    List<Lab> findByIdInAndSchoolId(Collection<Long> ids, Long schoolId);
}
