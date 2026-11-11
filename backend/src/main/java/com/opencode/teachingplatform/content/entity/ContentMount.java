package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 统一挂载关系；一个内容单元 0..N 行，多态列不建 DB 外键。
 */
@Entity
@Table(name = "content_mount")
public class ContentMount extends BaseEntity {

    @Column(name = "content_type", nullable = false, length = 16)
    private String contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "mount_type", nullable = false, length = 24)
    private String mountType;

    @Column(name = "mount_target_id", nullable = false)
    private Long mountTargetId;

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }
    public String getMountType() { return mountType; }
    public void setMountType(String mountType) { this.mountType = mountType; }
    public Long getMountTargetId() { return mountTargetId; }
    public void setMountTargetId(Long mountTargetId) { this.mountTargetId = mountTargetId; }
}
