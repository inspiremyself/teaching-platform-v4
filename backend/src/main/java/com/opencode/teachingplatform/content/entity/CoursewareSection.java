package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 课件正文按小节分行，content_json 是正文唯一真相源。
 */
@Entity
@Table(name = "courseware_section")
public class CoursewareSection extends BaseEntity {

    @Column(name = "courseware_id", nullable = false)
    private Long coursewareId;

    @Column(nullable = false)
    private String heading;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    private String contentJson;

    public Long getCoursewareId() { return coursewareId; }
    public void setCoursewareId(Long coursewareId) { this.coursewareId = coursewareId; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
}
