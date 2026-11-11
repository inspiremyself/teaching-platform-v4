package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.CoursewareSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CoursewareSectionRepository extends JpaRepository<CoursewareSection, Long> {
    List<CoursewareSection> findByCoursewareIdOrderBySortOrderAsc(Long coursewareId);

    List<CoursewareSection> findByCoursewareIdAndIdIn(Long coursewareId, Collection<Long> ids);
}
