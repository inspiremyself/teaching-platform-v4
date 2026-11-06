package com.opencode.teachingplatform.analysis.entity;

import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "score_record", uniqueConstraints = @UniqueConstraint(
        name = "uk_score_record_business_student",
        columnNames = {"business_type", "business_id", "student_id"}))
public class ScoreRecord extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 16)
    private BusinessType businessType;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Column(name = "class_id", nullable = false)
    private Long classId;
    @Column(nullable = false)
    private Double score;
    @Column(name = "graded_at", nullable = false)
    private OffsetDateTime gradedAt;

    public BusinessType getBusinessType() { return businessType; }
    public void setBusinessType(BusinessType businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public OffsetDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(OffsetDateTime gradedAt) { this.gradedAt = gradedAt; }
}
