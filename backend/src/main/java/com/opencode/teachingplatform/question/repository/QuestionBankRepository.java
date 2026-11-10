package com.opencode.teachingplatform.question.repository;

import com.opencode.teachingplatform.question.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    List<QuestionBank> findBySchoolIdAndCreatedByOrderByIdDesc(Long schoolId, Long createdBy);

    Optional<QuestionBank> findByIdAndSchoolIdAndCreatedBy(Long id, Long schoolId, Long createdBy);

    Optional<QuestionBank> findByIdAndSchoolId(Long id, Long schoolId);

    List<QuestionBank> findBySchoolIdAndCreatedBy(Long schoolId, Long createdBy);

    boolean existsBySchoolIdAndCreatedByAndCode(Long schoolId, Long createdBy, String code);

    boolean existsBySchoolIdAndCreatedByAndCodeAndIdNot(Long schoolId, Long createdBy, String code, Long id);

    Optional<QuestionBank> findBySourceRefAndSchoolId(String sourceRef, Long schoolId);
}
