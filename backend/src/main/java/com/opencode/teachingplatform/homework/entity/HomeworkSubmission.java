package com.opencode.teachingplatform.homework.entity;

import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "homework_submission")
public class HomeworkSubmission extends BaseEntity {
    @Column(name = "homework_id", nullable = false)
    private Long homeworkId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "submit_status", nullable = false, length = 16)
    private SubmissionStatus submitStatus;
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;
    @Column(name = "answer_file_path")
    private String answerFilePath;
    @Column(name = "total_score")
    private Double totalScore;
    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment;
    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;

    public Long getHomeworkId() { return homeworkId; }
    public void setHomeworkId(Long homeworkId) { this.homeworkId = homeworkId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public SubmissionStatus getSubmitStatus() { return submitStatus; }
    public void setSubmitStatus(SubmissionStatus submitStatus) { this.submitStatus = submitStatus; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public String getAnswerFilePath() { return answerFilePath; }
    public void setAnswerFilePath(String answerFilePath) { this.answerFilePath = answerFilePath; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public OffsetDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(OffsetDateTime gradedAt) { this.gradedAt = gradedAt; }
}
