package com.opencode.teachingplatform.homework.repository;

import com.opencode.teachingplatform.homework.entity.HomeworkAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HomeworkAnswerRepository extends JpaRepository<HomeworkAnswer, Long> {
    List<HomeworkAnswer> findByHomeworkSubmissionId(Long homeworkSubmissionId);
    Optional<HomeworkAnswer> findByHomeworkSubmissionIdAndHomeworkQuestionId(Long homeworkSubmissionId, Long homeworkQuestionId);
    boolean existsByHomeworkQuestionId(Long homeworkQuestionId);
}
