package com.opencode.teachingplatform.school.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import com.opencode.teachingplatform.school.SubjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 平台级学科字典：跨校对齐的学科标识唯一真相源。
 *
 * <p>学校侧只用 {@code school_subject.global_subject_id} 引用本表 id，不按 code 或名称跨层匹配；
 * 本表不硬删除，停用只改 status。
 */
@Entity
@Table(name = "subject")
public class Subject extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubjectStatus status;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SubjectStatus getStatus() { return status; }
    public void setStatus(SubjectStatus status) { this.status = status; }
}
