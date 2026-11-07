package com.opencode.teachingplatform.exam.repository;

import com.opencode.teachingplatform.exam.entity.ExamSubmission;
import com.opencode.teachingplatform.exam.enums.ExamSubmissionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByExamId(Long examId);
    Optional<ExamSubmission> findByExamIdAndStudentId(Long examId, Long studentId);
    long countByExamId(Long examId);

    /**
     * 自动交卷专用查询：按提交 id 对单行加悲观写锁。
     *
     * @param id 提交 id
     * @return 提交行（不存在时为空）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSubmission s where s.id = :id")
    Optional<ExamSubmission> findByIdForUpdate(@Param("id") Long id);

    /**
     * 自动交卷扫描：取指定状态且截止时间（含宽限期后）已过的前 100 条，按 id 升序。
     *
     * @param status    提交状态
     * @param threshold 截止时间阈值（now - 宽限期）
     * @return 待结算提交列表
     */
    List<ExamSubmission> findTop100ByStatusAndDeadlineAtLessThanEqualOrderByIdAsc(
            ExamSubmissionStatus status, OffsetDateTime threshold);

    /**
     * 交卷与批改场景专用查询：对“单学生 + 单考试”的提交行加悲观写锁。
     *
     * <p>交卷时先锁定该行再判定状态，确保并发双击只有一次能把 {@code IN_PROGRESS}
     * 推进为已提交；教师批改同样在锁内重读，保证成绩记录写入串行化。</p>
     *
     * @param examId    考试 id
     * @param studentId 学生 id
     * @return 提交行（不存在时为空）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSubmission s where s.examId = :examId and s.studentId = :studentId")
    Optional<ExamSubmission> findByExamIdAndStudentIdForUpdate(
            @Param("examId") Long examId,
            @Param("studentId") Long studentId);
}
