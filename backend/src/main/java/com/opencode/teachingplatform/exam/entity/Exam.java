package com.opencode.teachingplatform.exam.entity;

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
@Table(name = "exam")
public class Exam extends BaseEntity {
    @Column(nullable = false, length = 128)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "start_at")
    private OffsetDateTime startAt;
    @Column(name = "end_at")
    private OffsetDateTime endAt;
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActivityStatus status;
    @Column(name = "class_id", nullable = false)
    private Long classId;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "score_visibility_mode", nullable = false, length = 32)
    private ScoreVisibilityMode scoreVisibilityMode = ScoreVisibilityMode.AFTER_TEACHER_CONFIRM;
    @Column(name = "score_released", nullable = false)
    private boolean scoreReleased;
    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OffsetDateTime getStartAt() { return startAt; }
    public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public ScoreVisibilityMode getScoreVisibilityMode() { return scoreVisibilityMode; }
    public void setScoreVisibilityMode(ScoreVisibilityMode scoreVisibilityMode) { this.scoreVisibilityMode = scoreVisibilityMode; }
    public boolean isScoreReleased() { return scoreReleased; }
    public void setScoreReleased(boolean scoreReleased) { this.scoreReleased = scoreReleased; }
    public boolean isShuffleQuestions() { return shuffleQuestions; }
    public void setShuffleQuestions(boolean shuffleQuestions) { this.shuffleQuestions = shuffleQuestions; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
}
