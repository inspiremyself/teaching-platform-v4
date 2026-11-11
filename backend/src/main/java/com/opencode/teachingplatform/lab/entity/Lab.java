package com.opencode.teachingplatform.lab.entity;

import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Entity
@Table(name = "t_experiment")
@EntityListeners(AuditingEntityListener.class)
/**
 * 实验主表实体。
 *
 * <p>虽然 Java 类名仍叫 {@code Lab}，但它实际映射到数据库中的 {@code t_experiment}。
 * 这反映了项目在“领域命名保持为实验模块语义”与“数据库历史表名兼容”之间做的折中。</p>
 */
public class Lab {

    private static final int GENERATED_ID_BOUND_EXCLUSIVE = 2_000_000_000;

    @Id
    @Column(name = "experiment_id")
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "experiment_no", nullable = false)
    private Integer experimentNo;
    @Column(name = "experiment_name", nullable = false, length = 30)
    private String title;
    @Column(name = "experiment_requirement", columnDefinition = "TEXT")
    private String description;
    @Column(name = "experiment_type", nullable = false)
    private Integer experimentType = 1;
    @Column(name = "instruction_type", length = 10)
    private String instructionType = "1";
    @Column(name = "experiment_content", columnDefinition = "TEXT")
    private String experimentContent;
    @Column(name = "state", nullable = false)
    private Integer state = 0;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ActivityStatus status;
    @Column(name = "start_at")
    private OffsetDateTime startAt;
    @Column(name = "end_at")
    private OffsetDateTime endAt;
    @Column(name = "material_id")
    private Long materialId;
    @Column(name = "class_id", nullable = false)
    private Long classId;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "score_visibility_mode", nullable = false, length = 32)
    private ScoreVisibilityMode scoreVisibilityMode = ScoreVisibilityMode.AFTER_TEACHER_CONFIRM;
    @Column(name = "score_released", nullable = false)
    private boolean scoreReleased;
    @Column(name = "summary_required", nullable = false)
    private boolean summaryRequired;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    /** 指向 school_subject.id；历史行 NULL。 */
    @Column(name = "subject_id")
    private Long subjectId;

    @PrePersist
    void prePersist() {
        ensureId();
        applyDerivedFields();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        applyDerivedFields();
        updatedAt = OffsetDateTime.now();
    }

    private void ensureId() {
        if (getId() != null) {
            return;
        }
        long candidate = ThreadLocalRandom.current().nextInt(1, GENERATED_ID_BOUND_EXCLUSIVE);
        this.id = candidate;
    }

    private void applyDerivedFields() {
        if (experimentNo == null || experimentNo <= 0) {
            experimentNo = Math.toIntExact(id);
        }
        if (experimentContent == null) {
            experimentContent = description;
        }
        if (instructionType == null || instructionType.isBlank()) {
            instructionType = "1";
        }
        if (status != null) {
            state = switch (status) {
                case DRAFT -> 0;
                case PUBLISHED -> 1;
                case CLOSED -> 2;
            };
        }
    }

    public Long getId() { return id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
    }
    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) {
        this.status = status;
        if (status != null) {
            this.state = switch (status) {
                case DRAFT -> 0;
                case PUBLISHED -> 1;
                case CLOSED -> 2;
            };
        }
    }
    public OffsetDateTime getStartAt() { return startAt; }
    public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public ScoreVisibilityMode getScoreVisibilityMode() { return scoreVisibilityMode; }
    public void setScoreVisibilityMode(ScoreVisibilityMode scoreVisibilityMode) { this.scoreVisibilityMode = scoreVisibilityMode; }
    public boolean isScoreReleased() { return scoreReleased; }
    public void setScoreReleased(boolean scoreReleased) { this.scoreReleased = scoreReleased; }
    public Integer getExperimentNo() { return experimentNo; }
    public void setExperimentNo(Integer experimentNo) { this.experimentNo = experimentNo; }
    public Integer getExperimentType() { return experimentType; }
    public void setExperimentType(Integer experimentType) { this.experimentType = experimentType; }
    public String getInstructionType() { return instructionType; }
    public void setInstructionType(String instructionType) { this.instructionType = instructionType; }
    public String getExperimentContent() { return experimentContent; }
    public void setExperimentContent(String experimentContent) { this.experimentContent = experimentContent; }
    public Integer getState() { return state; }
    public void setState(Integer state) { this.state = state; }
    public boolean isSummaryRequired() { return summaryRequired; }
    public void setSummaryRequired(boolean summaryRequired) { this.summaryRequired = summaryRequired; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
}
