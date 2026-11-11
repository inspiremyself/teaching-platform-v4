package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 平台内容按学校集合授权；同一条记录只读引用，不做跨校复制。
 */
@Entity
@Table(name = "content_share_grant")
public class ContentShareGrant extends BaseEntity {

    @Column(name = "content_type", nullable = false, length = 16)
    private String contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
}
