package com.opencode.teachingplatform.school.repository;

import com.opencode.teachingplatform.school.SubjectStatus;
import com.opencode.teachingplatform.school.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByCode(String code);

    List<Subject> findByStatusOrderByCodeAsc(SubjectStatus status);
}
