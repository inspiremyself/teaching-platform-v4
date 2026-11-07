package com.opencode.teachingplatform.lab.repository;

import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabStepAnswerRepository extends JpaRepository<LabStepAnswer, Long> {
    List<LabStepAnswer> findByLabSubmissionId(Long labSubmissionId);
    Optional<LabStepAnswer> findByLabSubmissionIdAndLabStepId(Long labSubmissionId, Long labStepId);

    /**
     * 加锁读取单步答案：首存判断必须绕过 REPEATABLE READ 快照读，
     * 否则并发下会读到旧快照并重复插入子行。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LabStepAnswer a where a.labSubmissionId = :labSubmissionId and a.labStepId = :labStepId")
    Optional<LabStepAnswer> findByLabSubmissionIdAndLabStepIdForUpdate(
            @Param("labSubmissionId") Long labSubmissionId,
            @Param("labStepId") Long labStepId);

    List<LabStepAnswer> findByLabSubmissionIdInAndLabStepId(List<Long> labSubmissionIds, Long labStepId);
    List<LabStepAnswer> findByAnswerJsonContaining(String fragment);
}
