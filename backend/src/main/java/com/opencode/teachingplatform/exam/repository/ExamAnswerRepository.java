package com.opencode.teachingplatform.exam.repository;

import com.opencode.teachingplatform.exam.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {
    List<ExamAnswer> findByExamSubmissionId(Long examSubmissionId);

    Optional<ExamAnswer> findByExamSubmissionIdAndQuestionId(Long examSubmissionId, Long questionId);

    /**
     * 批量删除某次提交的全部答案。
     *
     * <p>必须是一次性 JPQL 批量删除，而不是派生删除：{@code ExamAnswer} 主键为 IDENTITY，
     * 交卷时新答案会立即 INSERT；若旧答案删除被推迟到 flush，会与
     * {@code uk_exam_answer_submission_question} 唯一约束产生“先插入后删除”的冲突。</p>
     *
     * @param examSubmissionId 提交 id
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM ExamAnswer a WHERE a.examSubmissionId = :examSubmissionId")
    void deleteByExamSubmissionId(@Param("examSubmissionId") Long examSubmissionId);
}
