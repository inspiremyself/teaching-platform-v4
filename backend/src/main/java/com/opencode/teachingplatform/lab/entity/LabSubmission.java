package com.opencode.teachingplatform.lab.entity;

import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "experiment_submission")
/**
 * 实验提交主记录。
 *
 * <p>一条记录对应“某个学生针对某个实验的一次提交主状态”，
 * 主要保存提交状态、总分、教师评语、实验小结以及提交 / 评分时间。</p>
 */
public class LabSubmission extends BaseEntity {

    @Column(name = "experiment_id", nullable = false)
    private Long labId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "submit_status", nullable = false, length = 16)
    private SubmissionStatus submitStatus;
    @Column(name = "total_score")
    private Double totalScore;
    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment;
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;
    @Column(name = "summary_attachment_path")
    private String summaryAttachmentPath;
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;
    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;

    public Long getLabId() { return labId; }
    public void setLabId(Long labId) { this.labId = labId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public SubmissionStatus getSubmitStatus() { return submitStatus; }
    public void setSubmitStatus(SubmissionStatus submitStatus) { this.submitStatus = submitStatus; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public String getSummaryAttachmentPath() { return summaryAttachmentPath; }
    public void setSummaryAttachmentPath(String summaryAttachmentPath) { this.summaryAttachmentPath = summaryAttachmentPath; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(OffsetDateTime gradedAt) { this.gradedAt = gradedAt; }
}
