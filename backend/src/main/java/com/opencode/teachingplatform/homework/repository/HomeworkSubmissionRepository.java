package com.opencode.teachingplatform.homework.repository;

import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.homework.entity.HomeworkSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, Long> {
    Optional<HomeworkSubmission> findByHomeworkIdAndStudentId(Long homeworkId, Long studentId);
    List<HomeworkSubmission> findByHomeworkId(Long homeworkId);
    List<HomeworkSubmission> findByHomeworkIdIn(List<Long> homeworkIds);

    /**
     * 查重候选查询：只取同一作业下指定状态的提交，避免跨作业互相比较。
     *
     * @param homeworkId 作业 id
     * @param statuses   提交状态集合
     * @return 候选提交列表
     */
    List<HomeworkSubmission> findByHomeworkIdAndSubmitStatusIn(Long homeworkId, Collection<SubmissionStatus> statuses);

    /**
     * 批改与打回场景专用查询：对“单学生 + 单作业”的提交行加悲观写锁。
     *
     * <p>教师批改/打回时先锁定该行再重读状态，确保总分与成绩记录写入串行化，
     * 避免与学生重新提交或其他教师的并发批改互相覆盖。</p>
     *
     * @param homeworkId 作业 id
     * @param studentId  学生 id
     * @return 提交行（不存在时为空）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HomeworkSubmission s where s.homeworkId = :homeworkId and s.studentId = :studentId")
    Optional<HomeworkSubmission> findByHomeworkIdAndStudentIdForUpdate(
            @Param("homeworkId") Long homeworkId,
            @Param("studentId") Long studentId);
}
