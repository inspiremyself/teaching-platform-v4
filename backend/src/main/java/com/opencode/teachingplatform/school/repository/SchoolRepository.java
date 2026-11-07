package com.opencode.teachingplatform.school.repository;

import com.opencode.teachingplatform.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findByCode(String code);
}
