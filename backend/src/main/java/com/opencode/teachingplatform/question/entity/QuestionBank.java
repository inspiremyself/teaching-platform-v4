package com.opencode.teachingplatform.question.entity;

import com.opencode.teachingplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_bank")
public class QuestionBank extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;
    @Column(nullable = false, length = 32)
    private String type;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;
    @Column(nullable = false, length = 32)
    private String difficulty;
    @Column(name = "default_score", nullable = false)
    private Integer defaultScore;
    @Column(name = "options_json", columnDefinition = "json")
    private String optionsJson;
    @Column(name = "answer_json", columnDefinition = "json")
    private String answerJson;
    @Column(name = "analysis_text", columnDefinition = "TEXT")
    private String analysisText;
    @Column(name = "scoring_config_json", columnDefinition = "json")
    private String scoringConfigJson;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    /** 题目来源：MANUAL / HOMEWORK_INLINE / LAB_STEP；NULL 表示历史或未知来源。 */
    @Column(name = "source_type", length = 20)
    private String sourceType;
    /** 来源唯一键：作业 HOMEWORK:{id}、实验 LAB:{labId}:{stepId}；唯一性由迁移索引保证。 */
    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Integer getDefaultScore() { return defaultScore; }
    public void setDefaultScore(Integer defaultScore) { this.defaultScore = defaultScore; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getAnswerJson() { return answerJson; }
    public void setAnswerJson(String answerJson) { this.answerJson = answerJson; }
    public String getAnalysisText() { return analysisText; }
    public void setAnalysisText(String analysisText) { this.analysisText = analysisText; }
    public String getScoringConfigJson() { return scoringConfigJson; }
    public void setScoringConfigJson(String scoringConfigJson) { this.scoringConfigJson = scoringConfigJson; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
}
