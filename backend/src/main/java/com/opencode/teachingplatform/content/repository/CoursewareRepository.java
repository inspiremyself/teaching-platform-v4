package com.opencode.teachingplatform.content.repository;

import com.opencode.teachingplatform.content.entity.Courseware;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoursewareRepository extends JpaRepository<Courseware, Long> {
    List<Courseware> findByOwnerScope(String ownerScope);

    List<Courseware> findByGlobalSubjectId(Long globalSubjectId);

    List<Courseware> findBySchoolSubjectId(Long schoolSubjectId);
}
