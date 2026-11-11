package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 教材小节树。叶子由「不存在子行」判定，不设 is_leaf 标记列。
 */
@Entity
@Table(name = "textbook_section")
public class TextbookSection extends BaseEntity {

    @Column(name = "textbook_id", nullable = false)
    private Long textbookId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String heading;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public Long getTextbookId() { return textbookId; }
    public void setTextbookId(Long textbookId) { this.textbookId = textbookId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
