package com.opencode.teachingplatform.homework.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.model.GradingResultView;
import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.assessment.service.QuestionSnapshotFactory;
import com.opencode.teachingplatform.assessment.service.AnswerPayloadNormalizer;
import com.opencode.teachingplatform.assessment.service.GradingAdapter;
import com.opencode.teachingplatform.assessment.service.QuestionContentSupport;
import com.opencode.teachingplatform.assessment.service.QuestionContentValidator;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.common.infra.audit.AuditAction;
import com.opencode.teachingplatform.common.infra.audit.AuditLogService;
import com.opencode.teachingplatform.common.infra.audit.AuditResourceType;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.common.util.ActivityStatusParser;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.homework.dto.HomeworkRequests;
import com.opencode.teachingplatform.homework.entity.HomeworkAnswer;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.entity.HomeworkQuestion;
import com.opencode.teachingplatform.homework.entity.HomeworkSubmission;
import com.opencode.teachingplatform.homework.repository.HomeworkAnswerRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkQuestionRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkSubmissionRepository;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import com.opencode.teachingplatform.plagiarism.service.LocalPlagiarismService;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HomeworkService {

    private final HomeworkConfigService homeworkConfigService;
    private final HomeworkAnswerService homeworkAnswerService;
    private final HomeworkReportService homeworkReportService;

    public HomeworkService(
            HomeworkConfigService homeworkConfigService,
            HomeworkAnswerService homeworkAnswerService,
            HomeworkReportService homeworkReportService) {
        this.homeworkConfigService = homeworkConfigService;
        this.homeworkAnswerService = homeworkAnswerService;
        this.homeworkReportService = homeworkReportService;
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherHomeworks(CurrentUser currentUser) {
        return homeworkConfigService.listTeacherHomeworks(currentUser);
    }


    @Transactional
    public Map<String, Object> createHomework(CurrentUser currentUser, HomeworkRequests.CreateHomeworkRequest request) {
        return homeworkConfigService.createHomework(currentUser, request);
    }


    @Transactional
    public Map<String, Object> updateHomework(CurrentUser currentUser, Long homeworkId, HomeworkRequests.UpdateHomeworkRequest request) {
        return homeworkConfigService.updateHomework(currentUser, homeworkId, request);
    }


    @Transactional
    public Map<String, Object> changeHomeworkStatus(CurrentUser currentUser, Long homeworkId, HomeworkRequests.ChangeHomeworkStatusRequest request) {
        return homeworkConfigService.changeHomeworkStatus(currentUser, homeworkId, request);
    }


    @Transactional
    public Map<String, Object> releaseScores(CurrentUser currentUser, Long homeworkId) {
        return homeworkConfigService.releaseScores(currentUser, homeworkId);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentHomeworks(CurrentUser currentUser) {
        return homeworkConfigService.listStudentHomeworks(currentUser);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getStudentHomeworkDetail(CurrentUser currentUser, Long homeworkId) {
        return homeworkConfigService.getStudentHomeworkDetail(currentUser, homeworkId);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherSubmissions(CurrentUser currentUser, Long homeworkId) {
        return homeworkConfigService.listTeacherSubmissions(currentUser, homeworkId);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherSubmissionDetail(CurrentUser currentUser, Long submissionId) {
        return homeworkConfigService.getTeacherSubmissionDetail(currentUser, submissionId);
    }


    /**
     * 教师作业详情：用于编辑弹窗回显与前端增量提交。
     *
     * <p>返回作业基础字段与题目列表，题目字段与新增/更新接口的响应保持一致。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherHomeworkDetail(CurrentUser currentUser, Long homeworkId) {
        return homeworkConfigService.getTeacherHomeworkDetail(currentUser, homeworkId);
    }


    @Transactional
    public Map<String, Object> addQuestionFromBank(CurrentUser currentUser, Long homeworkId, HomeworkRequests.AddHomeworkQuestionFromBankRequest request) {
        return homeworkConfigService.addQuestionFromBank(currentUser, homeworkId, request);
    }


    @Transactional
    public Map<String, Object> addInlineQuestion(CurrentUser currentUser, Long homeworkId, HomeworkRequests.SaveHomeworkQuestionRequest request) {
        return homeworkConfigService.addInlineQuestion(currentUser, homeworkId, request);
    }


    /**
     * 更新作业题目。
     *
     * <p>锁定矩阵：草稿可全量修改（已有作答的题禁止改分值）；已发布仅允许调整排序；
     * 已关闭全部禁止。已发布场景使用归一后结构做一致性比较，避免原始字符串差异被误判为内容变更。</p>
     */
    @Transactional
    public Map<String, Object> updateQuestion(CurrentUser currentUser,
                                              Long homeworkId,
                                              Long homeworkQuestionId,
                                              HomeworkRequests.SaveHomeworkQuestionRequest request) {
        return homeworkConfigService.updateQuestion(currentUser, homeworkId, homeworkQuestionId, request);
    }


    /**
     * 删除作业题目：仅草稿允许，且已有学生作答的题目不允许删除。
     */
    @Transactional
    public Map<String, Object> deleteQuestion(CurrentUser currentUser, Long homeworkId, Long homeworkQuestionId) {
        return homeworkConfigService.deleteQuestion(currentUser, homeworkId, homeworkQuestionId);
    }


    @Transactional
    public Map<String, Object> submitHomework(CurrentUser currentUser, Long homeworkId, HomeworkRequests.SubmitHomeworkRequest request) {
        return homeworkAnswerService.submitHomework(currentUser, homeworkId, request);
    }


    /**
     * 学生保存作业草稿。
     *
     * <p>草稿只落学生作答，不触发评分、不写成绩记录、不触发查重：
     * 因此本方法不调用 {@code applyQuestionAnswers} / {@code applyScoring} /
     * {@code refreshSubmissionGradeState}。校验顺序固定为：身份 → 可访问的已发布作业 →
     * 已关闭 → 未开始 → 已截止，全部命中都在任何写操作之前返回。</p>
     */
    @Transactional
    public Map<String, Object> saveHomeworkDraft(CurrentUser currentUser, Long homeworkId, HomeworkRequests.SubmitHomeworkRequest request) {
        return homeworkAnswerService.saveHomeworkDraft(currentUser, homeworkId, request);
    }


    @Transactional
    public Map<String, Object> gradeSubmission(CurrentUser currentUser, Long submissionId, HomeworkRequests.GradeHomeworkRequest request) {
        return homeworkReportService.gradeSubmission(currentUser, submissionId, request);
    }


    /**
     * 教师打回作业提交。
     *
     * <p>语义：状态回到 {@code SAVED}，清空提交时间以外的评分痕迹（总分、教师评语、逐题得分与判定），
     * 但保留学生作答内容；无论打回前是已提交还是已批改，均删除对应成绩记录。
     * 学生因此可重新编辑并提交。</p>
     */
    @Transactional
    public Map<String, Object> returnHomeworkSubmission(CurrentUser currentUser, Long submissionId) {
        return homeworkReportService.returnHomeworkSubmission(currentUser, submissionId);
    }


    @Transactional
    public Map<String, Object> gradeAnswer(CurrentUser currentUser, Long answerId, HomeworkRequests.GradeHomeworkAnswerRequest request) {
        return homeworkReportService.gradeAnswer(currentUser, answerId, request);
    }
}
