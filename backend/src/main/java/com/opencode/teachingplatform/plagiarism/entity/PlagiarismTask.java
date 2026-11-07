package com.opencode.teachingplatform.plagiarism.entity;

import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.plagiarism.PlagiarismStatus;
import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "plagiarism_task")
/**
 * 查重任务持久化实体。
 *
 * <p>它保存的不是整份原始作业内容，而是“某次业务提交对应的查重结果快照”：
 * 包括业务类型、业务记录 id、学生 id、相似率、匹配摘要和复核状态。</p>
 */
public class PlagiarismTask extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 16)
    private BusinessType businessType;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "student_id", nullable = false)
    private Long studentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlagiarismStatus status;
    @Column(name = "similarity_rate")
    private Double similarityRate;
    @Column(name = "top_match_summary", length = 255)
    private String topMatchSummary;
    @Column(name = "raw_result_json", columnDefinition = "json")
    private String rawResultJson;

    public BusinessType getBusinessType() { return businessType; }
    public void setBusinessType(BusinessType businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public PlagiarismStatus getStatus() { return status; }
    public void setStatus(PlagiarismStatus status) { this.status = status; }
    public Double getSimilarityRate() { return similarityRate; }
    public void setSimilarityRate(Double similarityRate) { this.similarityRate = similarityRate; }
    public String getTopMatchSummary() { return topMatchSummary; }
    public void setTopMatchSummary(String topMatchSummary) { this.topMatchSummary = topMatchSummary; }
    public String getRawResultJson() { return rawResultJson; }
    public void setRawResultJson(String rawResultJson) { this.rawResultJson = rawResultJson; }
}
