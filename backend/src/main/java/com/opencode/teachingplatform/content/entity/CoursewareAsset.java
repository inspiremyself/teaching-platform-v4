package com.opencode.teachingplatform.content.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 图片 / 音频素材，只存 data/files/ 下相对路径，不内联 base64。
 */
@Entity
@Table(name = "courseware_asset")
public class CoursewareAsset extends BaseEntity {

    @Column(name = "courseware_id", nullable = false)
    private Long coursewareId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(length = 128)
    private String mime;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    public Long getCoursewareId() { return coursewareId; }
    public void setCoursewareId(Long coursewareId) { this.coursewareId = coursewareId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
}
