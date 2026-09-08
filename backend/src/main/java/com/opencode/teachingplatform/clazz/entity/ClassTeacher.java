package com.opencode.teachingplatform.clazz.entity;

import com.opencode.teachingplatform.common.enums.ClassTeacherRole;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "class_teacher")
public class ClassTeacher extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "teacher_user_id", nullable = false)
    private Long teacherUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClassTeacherRole role;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getTeacherUserId() { return teacherUserId; }
    public void setTeacherUserId(Long teacherUserId) { this.teacherUserId = teacherUserId; }
    public ClassTeacherRole getRole() { return role; }
    public void setRole(ClassTeacherRole role) { this.role = role; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
}
