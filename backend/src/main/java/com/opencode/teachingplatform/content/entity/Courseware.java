package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 课件主表。本期只产生 PLATFORM 归属行；分域学科两列互斥由应用层强制。
 */
@Entity
@Table(name = "courseware")
public class Courseware extends BaseEntity {

    @Column(name = "owner_scope", nullable = false, length = 16)
    private String ownerScope;

    @Column(name = "owner_school_id")
    private Long ownerSchoolId;

    @Column(name = "global_subject_id")
    private Long globalSubjectId;

    @Column(name = "school_subject_id")
    private Long schoolSubjectId;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    public String getOwnerScope() { return ownerScope; }
    public void setOwnerScope(String ownerScope) { this.ownerScope = ownerScope; }
    public Long getOwnerSchoolId() { return ownerSchoolId; }
    public void setOwnerSchoolId(Long ownerSchoolId) { this.ownerSchoolId = ownerSchoolId; }
    public Long getGlobalSubjectId() { return globalSubjectId; }
    public void setGlobalSubjectId(Long globalSubjectId) { this.globalSubjectId = globalSubjectId; }
    public Long getSchoolSubjectId() { return schoolSubjectId; }
    public void setSchoolSubjectId(Long schoolSubjectId) { this.schoolSubjectId = schoolSubjectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
