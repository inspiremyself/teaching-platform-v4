package com.opencode.teachingplatform.material.repository;

import com.opencode.teachingplatform.common.enums.MaterialVisibility;
import com.opencode.teachingplatform.material.entity.CourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, Long> {
    List<CourseMaterial> findBySchoolIdOrderByIdDesc(Long schoolId);
    List<CourseMaterial> findBySchoolIdAndVisibilityOrderByIdDesc(Long schoolId, MaterialVisibility visibility);
    List<CourseMaterial> findBySchoolIdAndUploaderUserIdOrderByIdDesc(Long schoolId, Long uploaderUserId);
    Optional<CourseMaterial> findByIdAndUploaderUserId(Long id, Long uploaderUserId);
}
