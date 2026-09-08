package com.opencode.teachingplatform.school.entity;

import com.opencode.teachingplatform.common.enums.SubjectStatus;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "school_subject")
public class SchoolSubject extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubjectStatus status;

    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public SubjectStatus getStatus() { return status; }
    public void setStatus(SubjectStatus status) { this.status = status; }
}
