package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabStepAnswerRepository extends JpaRepository<LabStepAnswer, Long> {
    List<LabStepAnswer> findByLabSubmissionId(Long labSubmissionId);
    Optional<LabStepAnswer> findByLabSubmissionIdAndLabStepId(Long labSubmissionId, Long labStepId);
    List<LabStepAnswer> findByLabSubmissionIdInAndLabStepId(List<Long> labSubmissionIds, Long labStepId);
    List<LabStepAnswer> findByAnswerJsonContaining(String fragment);
}
