package com.opencode.teachingplatform.exam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.model.GradingResultView;
import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.assessment.service.AnswerPayloadNormalizer;
import com.opencode.teachingplatform.assessment.service.GradingAdapter;
import com.opencode.teachingplatform.assessment.service.QuestionSnapshotFactory;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.exam.entity.Exam;
import com.opencode.teachingplatform.exam.entity.ExamAnswer;
import com.opencode.teachingplatform.exam.entity.ExamQuestion;
import com.opencode.teachingplatform.exam.entity.ExamSubmission;
import com.opencode.teachingplatform.exam.enums.ExamSubmissionStatus;
import com.opencode.teachingplatform.exam.repository.ExamAnswerRepository;
import com.opencode.teachingplatform.exam.repository.ExamQuestionRepository;
import com.opencode.teachingplatform.exam.repository.ExamRepository;
import com.opencode.teachingplatform.exam.repository.ExamSubmissionRepository;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamService {

    private final ExamConfigService examConfigService;
    private final ExamAnswerService examAnswerService;
    private final ExamReportService examReportService;

    public ExamService(
            ExamConfigService examConfigService,
            ExamAnswerService examAnswerService,
            ExamReportService examReportService) {
        this.examConfigService = examConfigService;
        this.examAnswerService = examAnswerService;
        this.examReportService = examReportService;
    }

    /** 交卷宽限期（秒）：截止时间后仍允许手动交卷，超时自动交卷同样以此边界判定。 */
    public static final long SUBMIT_GRACE_SECONDS = 120L;


    public Object listExams(CurrentUser currentUser) {
        return examConfigService.listExams(currentUser);
    }


    @Transactional
    public Object createExam(CurrentUser currentUser, ExamRequests.CreateExamRequest request) {
        return examConfigService.createExam(currentUser, request);
    }


    @Transactional
    public Object updateExam(CurrentUser currentUser, Long id, ExamRequests.UpdateExamRequest request) {
        return examConfigService.updateExam(currentUser, id, request);
    }


    public Object getExamDetail(CurrentUser currentUser, Long id) {
        return examConfigService.getExamDetail(currentUser, id);
    }


    @Transactional
    public Object changeStatus(CurrentUser currentUser, Long id, ExamRequests.ChangeExamStatusRequest request) {
        return examConfigService.changeStatus(currentUser, id, request);
    }


    @Transactional
    public Object releaseScores(CurrentUser currentUser, Long examId) {
        return examConfigService.releaseScores(currentUser, examId);
    }


    public Object startExam(CurrentUser currentUser, Long examId) {
        return examAnswerService.startExam(currentUser, examId);
    }


    @Transactional
    public Object submitExam(CurrentUser currentUser, Long examId, ExamRequests.SubmitExamRequest request) {
        return examAnswerService.submitExam(currentUser, examId, request);
    }


    /**
     * 考试结算：手动交卷与超时自动交卷共用。
     *
     * <p>调用方必须已持有提交行悲观写锁并开启事务（本方法为 {@code MANDATORY}）。
     * 结算流程：状态判定 → 快照已持久化草稿 → 删除旧答案 → 逐题评分写答案 →
     * 汇总分数与提交时间 → 全客观题结案 → 结案时写成绩记录 → 组装响应。</p>
     *
     * <p>自动交卷传入空 payload，所有题目回退 {@code exam_answer.answer_json} 草稿，
     * 保证断线场景不会提交空白卷。</p>
     *
     * @param submission  已锁定的提交行
     * @param exam        所属考试
     * @param submitItems 请求体作答条目（按 questionId 索引），自动交卷传空 Map
     * @return 与学生交卷接口一致的响应体
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> finalizeExamSubmission(ExamSubmission submission,
                                                      Exam exam,
                                                      Map<Long, ExamRequests.SubmitAnswerItem> submitItems) {
        return examAnswerService.finalizeExamSubmission(submission, exam, submitItems);
    }


    /**
     * 保存考试草稿。
     *
     * <p>只写 {@code exam_answer.answer_json}，不评分、不改提交状态、不写成绩记录。
     * 提交行悲观写锁与交卷串行；同请求内按 questionId 去重（后写覆盖前写）。</p>
     *
     * @param currentUser 当前学生
     * @param examId      考试 id
     * @param request     作答内容，与交卷请求体一致
     * @return {@code submissionId / savedAt / savedCount}
     */
    @Transactional
    public Object saveDraft(CurrentUser currentUser, Long examId, ExamRequests.SubmitExamRequest request) {
        return examAnswerService.saveDraft(currentUser, examId, request);
    }


    public Object getExamResults(CurrentUser currentUser, Long examId) {
        return examReportService.getExamResults(currentUser, examId);
    }


    public Object getSubmissionDetail(CurrentUser currentUser, Long submissionId) {
        return examReportService.getSubmissionDetail(currentUser, submissionId);
    }


    @Transactional
    public Object gradeSubmission(CurrentUser currentUser, Long submissionId, ExamRequests.GradeExamSubmissionRequest request) {
        return examReportService.gradeSubmission(currentUser, submissionId, request);
    }


    @Transactional
    public Object confirmAnswer(CurrentUser currentUser, Long submissionId, ExamRequests.ConfirmExamAnswerRequest request) {
        return examReportService.confirmAnswer(currentUser, submissionId, request);
    }
}
