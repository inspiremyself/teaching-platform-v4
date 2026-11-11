package com.opencode.teachingplatform.homework.entity;

import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "homework")
public class Homework extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "class_id", nullable = false)
    private Long classId;
    @Column(name = "start_at")
    private OffsetDateTime startAt;
    @Column(name = "due_at")
    private OffsetDateTime dueAt;
    @Column(name = "attachment_path")
    private String attachmentPath;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActivityStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "score_visibility_mode", nullable = false, length = 32)
    private ScoreVisibilityMode scoreVisibilityMode = ScoreVisibilityMode.AFTER_TEACHER_CONFIRM;
    @Column(name = "score_released", nullable = false)
    private boolean scoreReleased;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    /** 指向 school_subject.id；历史行 NULL。 */
    @Column(name = "subject_id")
    private Long subjectId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public OffsetDateTime getStartAt() { return startAt; }
    public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }
    public ScoreVisibilityMode getScoreVisibilityMode() { return scoreVisibilityMode; }
    public void setScoreVisibilityMode(ScoreVisibilityMode scoreVisibilityMode) { this.scoreVisibilityMode = scoreVisibilityMode; }
    public boolean isScoreReleased() { return scoreReleased; }
    public void setScoreReleased(boolean scoreReleased) { this.scoreReleased = scoreReleased; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
}
