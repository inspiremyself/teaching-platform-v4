package com.opencode.teachingplatform.question.repository;

import com.opencode.teachingplatform.question.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    List<QuestionBank> findByCreatedByOrderByIdDesc(Long createdBy);

    Optional<QuestionBank> findByIdAndCreatedBy(Long id, Long createdBy);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByCodeAndIdNotAndCreatedBy(String code, Long id, Long createdBy);

    Optional<QuestionBank> findBySourceRef(String sourceRef);

    List<QuestionBank> findByCreatedBy(Long createdBy);
}
