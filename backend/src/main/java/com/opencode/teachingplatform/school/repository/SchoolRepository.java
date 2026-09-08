package com.opencode.teachingplatform.school.repository;

import com.opencode.teachingplatform.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Long> {
}
