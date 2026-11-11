package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.CoursewareAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoursewareAssetRepository extends JpaRepository<CoursewareAsset, Long> {
    List<CoursewareAsset> findByCoursewareId(Long coursewareId);
}
