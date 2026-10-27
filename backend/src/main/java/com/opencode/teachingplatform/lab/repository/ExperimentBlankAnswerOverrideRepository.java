package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.ExperimentBlankAnswerOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentBlankAnswerOverrideRepository extends JpaRepository<ExperimentBlankAnswerOverride, Long> {
    List<ExperimentBlankAnswerOverride> findByExperimentIdAndExperimentItemIdOrderByIdAsc(Long experimentId, Long experimentItemId);
    List<ExperimentBlankAnswerOverride> findByExperimentIdOrderByIdAsc(Long experimentId);
    void deleteByExperimentIdAndExperimentItemId(Long experimentId, Long experimentItemId);
}
