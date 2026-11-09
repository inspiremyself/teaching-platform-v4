package com.opencode.teachingplatform.grading.service;

import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 三域成绩记录统一写入口。
 *
 * <p>幂等键为 {@code (business_type, business_id, student_id)}，对应数据库唯一约束
 * {@code uk_score_record_business_student}：先查后写保证同键只有一行；
 * 并发下若唯一约束仍冲突，统一转成 {@code 40900} 业务错误，不向用户返回 500。</p>
 */
@Service
public class ScoreRecordWriter {

    private final ScoreRecordRepository scoreRecordRepository;

    public ScoreRecordWriter(ScoreRecordRepository scoreRecordRepository) {
        this.scoreRecordRepository = scoreRecordRepository;
    }

    /**
     * 写入或更新一条成绩记录。
     *
     * <p>调用方必须已持有对应业务提交行的悲观写锁（考试/实验/作业提交行），
     * 由提交行锁串行化同键写入；本方法的唯一约束只作兜底。</p>
     *
     * @param businessType 业务类型
     * @param businessId   业务 id（考试/实验/作业 id）
     * @param studentId    学生 id
     * @param classId      班级 id
     * @param score        分数
     * @param gradedAt     评分时间，为空时取当前时间
     * @param schoolId     业务主表所属学校 id
     * @return 持久化后的成绩记录
     */
    @Transactional
    public ScoreRecord write(BusinessType businessType, Long businessId, Long studentId,
                             Long classId, Double score, OffsetDateTime gradedAt, Long schoolId) {
        ScoreRecord scoreRecord = scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentIdAndSchoolId(
                        businessType, businessId, studentId, schoolId)
                .orElseGet(ScoreRecord::new);
        scoreRecord.setBusinessType(businessType);
        scoreRecord.setBusinessId(businessId);
        scoreRecord.setStudentId(studentId);
        scoreRecord.setClassId(classId);
        scoreRecord.setScore(score);
        scoreRecord.setGradedAt(gradedAt == null ? OffsetDateTime.now() : gradedAt);
        scoreRecord.setSchoolId(schoolId);
        try {
            return scoreRecordRepository.saveAndFlush(scoreRecord);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(40900, "成绩记录并发冲突，请重试");
        }
    }

    /**
     * 删除一条成绩记录（幂等：记录不存在时直接返回）。
     *
     * <p>用于考试从 {@code GRADED} 降回 {@code SUBMITTED} 时清理残留成绩，
     * 避免学生成绩列表继续展示已撤销的结果。</p>
     *
     * @param schoolId 业务主表所属学校 id
     */
    @Transactional
    public void remove(BusinessType businessType, Long businessId, Long studentId, Long schoolId) {
        scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentIdAndSchoolId(
                        businessType, businessId, studentId, schoolId)
                .ifPresent(scoreRecordRepository::delete);
    }
}
