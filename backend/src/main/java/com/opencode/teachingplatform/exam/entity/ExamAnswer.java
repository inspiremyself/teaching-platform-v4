package com.opencode.teachingplatform.exam.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "exam_answer", uniqueConstraints = @UniqueConstraint(
        name = "uk_exam_answer_submission_question",
        columnNames = {"exam_submission_id", "question_id"}))
public class ExamAnswer extends BaseEntity {
    @Column(name = "exam_submission_id", nullable = false)
    private Long examSubmissionId;
    @Column(name = "question_id", nullable = false)
    private Long questionId;
    @Column(name = "answer_json", columnDefinition = "json")
    private String answerJson;
    @Column(name = "is_correct")
    private Boolean isCorrect;
    @Column
    private Double score;
    @Column(name = "auto_score")
    private Double autoScore;
    @Column(name = "suggested_score")
    private Double suggestedScore;
    @Column(name = "score_source", nullable = false, length = 32)
    private String scoreSource = "TEACHER";
    @Column(name = "judge_detail", columnDefinition = "TEXT")
    private String judgeDetail;
    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment;
    @Column(name = "accepted_auto_score", nullable = false)
    private boolean acceptedAutoScore;

    public Long getExamSubmissionId() { return examSubmissionId; }
    public void setExamSubmissionId(Long examSubmissionId) { this.examSubmissionId = examSubmissionId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getAnswerJson() { return answerJson; }
    public void setAnswerJson(String answerJson) { this.answerJson = answerJson; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean correct) { isCorrect = correct; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Double getAutoScore() { return autoScore; }
    public void setAutoScore(Double autoScore) { this.autoScore = autoScore; }
    public Double getSuggestedScore() { return suggestedScore; }
    public void setSuggestedScore(Double suggestedScore) { this.suggestedScore = suggestedScore; }
    public String getScoreSource() { return scoreSource; }
    public void setScoreSource(String scoreSource) { this.scoreSource = scoreSource; }
    public String getJudgeDetail() { return judgeDetail; }
    public void setJudgeDetail(String judgeDetail) { this.judgeDetail = judgeDetail; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public boolean isAcceptedAutoScore() { return acceptedAutoScore; }
    public void setAcceptedAutoScore(boolean acceptedAutoScore) { this.acceptedAutoScore = acceptedAutoScore; }
}
