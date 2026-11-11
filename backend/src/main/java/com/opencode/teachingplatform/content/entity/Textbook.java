package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 平台级教材目录。学科是归属列而非树节点，学校是否启用由 {@link SchoolTextbook} 单独承载。
 */
@Entity
@Table(name = "textbook")
public class Textbook extends BaseEntity {

    @Column(name = "global_subject_id", nullable = false)
    private Long globalSubjectId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_by")
    private Long createdBy;

    public Long getGlobalSubjectId() { return globalSubjectId; }
    public void setGlobalSubjectId(Long globalSubjectId) { this.globalSubjectId = globalSubjectId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
