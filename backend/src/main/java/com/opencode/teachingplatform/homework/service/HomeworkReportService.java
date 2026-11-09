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
public class HomeworkReportService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final HomeworkQuestionRepository homeworkQuestionRepository;
    private final HomeworkAnswerRepository homeworkAnswerRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassAccessService classAccessService;
    private final ClassMemberRepository classMemberRepository;
    private final SysUserRepository sysUserRepository;
    private final PlagiarismTaskRepository plagiarismTaskRepository;
    private final LocalPlagiarismService localPlagiarismService;
    private final PlagiarismTaskWriter plagiarismTaskWriter;
    private final ScoreRecordRepository scoreRecordRepository;
    private final ScoreRecordWriter scoreRecordWriter;
    private final AuditLogService auditLogService;
    private final QuestionBankRepository questionBankRepository;
    private final GradingAdapter gradingAdapter;
    private final ObjectMapper objectMapper;
    private final QuestionContentValidator questionContentValidator;
    private final HomeworkSubmissionClaimService homeworkSubmissionClaimService;

    private final HomeworkServiceSupport support;

    public HomeworkReportService(HomeworkRepository homeworkRepository,
                           HomeworkSubmissionRepository homeworkSubmissionRepository,
                           HomeworkQuestionRepository homeworkQuestionRepository,
                           HomeworkAnswerRepository homeworkAnswerRepository,
                           ClassRoomRepository classRoomRepository,
                           ClassAccessService classAccessService,
                           ClassMemberRepository classMemberRepository,
                           SysUserRepository sysUserRepository,
                           PlagiarismTaskRepository plagiarismTaskRepository,
                           LocalPlagiarismService localPlagiarismService,
                           PlagiarismTaskWriter plagiarismTaskWriter,
                           ScoreRecordRepository scoreRecordRepository,
                           ScoreRecordWriter scoreRecordWriter,
                           AuditLogService auditLogService,
                           QuestionBankRepository questionBankRepository,
                           ObjectProvider<GradingAdapter> gradingAdapterProvider,
                           ObjectProvider<ScoringEngine> scoringEngineProvider,
                           ObjectProvider<ObjectMapper> objectMapperProvider,
                           QuestionContentValidator questionContentValidator,
                           HomeworkSubmissionClaimService homeworkSubmissionClaimService,
                      HomeworkServiceSupport support) {
        this.homeworkRepository = homeworkRepository;
        this.homeworkSubmissionRepository = homeworkSubmissionRepository;
        this.homeworkQuestionRepository = homeworkQuestionRepository;
        this.homeworkAnswerRepository = homeworkAnswerRepository;
        this.classRoomRepository = classRoomRepository;
        this.classAccessService = classAccessService;
        this.classMemberRepository = classMemberRepository;
        this.sysUserRepository = sysUserRepository;
        this.plagiarismTaskRepository = plagiarismTaskRepository;
        this.localPlagiarismService = localPlagiarismService;
        this.plagiarismTaskWriter = plagiarismTaskWriter;
        this.scoreRecordRepository = scoreRecordRepository;
        this.scoreRecordWriter = scoreRecordWriter;
        this.auditLogService = auditLogService;
        this.questionBankRepository = questionBankRepository;
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.gradingAdapter = gradingAdapterProvider.getIfAvailable(() -> new GradingAdapter(scoringEngineProvider.getIfAvailable(), this.objectMapper));
        this.questionContentValidator = questionContentValidator;
        this.homeworkSubmissionClaimService = homeworkSubmissionClaimService;
        this.support = support;
    }


    @Transactional
    public Map<String, Object> gradeSubmission(CurrentUser currentUser, Long submissionId, HomeworkRequests.GradeHomeworkRequest request) {
        support.requireTeacher(currentUser);
        HomeworkSubmission head = homeworkSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        Homework homework = support.ownedHomework(currentUser, head.getHomeworkId());
        // 先取业务键 (homeworkId, studentId)，再在锁内重读并重跑状态校验，保证成绩写入串行化。
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentIdForUpdate(homework.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        if (submission.getSubmitStatus() != SubmissionStatus.SUBMITTED) {
            throw new BusinessException(40000, "仅已提交作业允许批改");
        }
        submission.setTotalScore(request.totalScore());
        submission.setTeacherComment(support.defaultString(request.teacherComment()));
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(OffsetDateTime.now());
        homeworkSubmissionRepository.save(submission);
        scoreRecordWriter.write(BusinessType.HOMEWORK, homework.getId(), submission.getStudentId(),
                homework.getClassId(), request.totalScore(), OffsetDateTime.now(), homework.getSchoolId());
        return Map.of(
                "graded", true,
                "totalScore", submission.getTotalScore()
        );
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
        support.requireTeacher(currentUser);
        HomeworkSubmission head = homeworkSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        Homework homework = support.ownedHomework(currentUser, head.getHomeworkId());
        if (homework.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "作业已关闭，不能打回");
        }
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentIdForUpdate(homework.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        SubmissionStatus from = submission.getSubmitStatus();
        if (from != SubmissionStatus.SUBMITTED && from != SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "仅已提交或已批改的作业可打回");
        }
        // 清空提交级评分痕迹，保留作答内容与查重结果。
        submission.setSubmitStatus(SubmissionStatus.SAVED);
        submission.setGradedAt(null);
        submission.setTotalScore(null);
        submission.setTeacherComment("");
        homeworkSubmissionRepository.save(submission);
        // 清空逐题评分痕迹，作答文本 / JSON / 附件保持不动。
        for (HomeworkAnswer answer : homeworkAnswerRepository.findByHomeworkSubmissionId(submission.getId())) {
            answer.setScore(null);
            answer.setAutoScore(null);
            answer.setSuggestedScore(null);
            answer.setJudgeDetail(null);
            answer.setTeacherComment("");
            answer.setScoreSource("TEACHER");
            answer.setAcceptedAutoScore(false);
            homeworkAnswerRepository.save(answer);
        }
        // 删除成绩记录，避免打回后统计仍保留旧分数。
        scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentIdAndSchoolId(BusinessType.HOMEWORK, homework.getId(), submission.getStudentId(), homework.getSchoolId())
                .ifPresent(scoreRecordRepository::delete);
        auditLogService.log(currentUser, AuditAction.HOMEWORK_SUBMISSION_RETURN,
                AuditResourceType.HOMEWORK_SUBMISSION, submission.getId(),
                Map.of("homeworkId", homework.getId(), "studentId", submission.getStudentId(),
                        "fromStatus", from.name()));
        return Map.of("submissionId", submission.getId(), "status", "SAVED",
                "homeworkId", homework.getId(), "studentId", submission.getStudentId());
    }



    @Transactional
    public Map<String, Object> gradeAnswer(CurrentUser currentUser, Long answerId, HomeworkRequests.GradeHomeworkAnswerRequest request) {
        support.requireTeacher(currentUser);
        HomeworkAnswer answer = homeworkAnswerRepository.findById(answerId)
                .orElseThrow(() -> new BusinessException(40400, "作业答案不存在"));
        HomeworkSubmission head = homeworkSubmissionRepository.findById(answer.getHomeworkSubmissionId())
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        Homework homework = support.ownedHomework(currentUser, head.getHomeworkId());
        // 先取业务键 (homeworkId, studentId)，再在锁内重读，总分与成绩记录由该对象写入。
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentIdForUpdate(homework.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));

        // 状态门禁：草稿（SAVED）尚无最终答案，逐题评分会污染总分与成绩记录，必须拒绝。
        SubmissionStatus submitStatus = submission.getSubmitStatus();
        if (submitStatus != SubmissionStatus.SUBMITTED && submitStatus != SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "学生尚未提交作业，不能批改");
        }

        if (request.acceptSuggested()) {
            if (answer.getSuggestedScore() == null) {
                throw new BusinessException(40000, "当前题目没有可采纳的推荐分");
            }
            answer.setScore(answer.getSuggestedScore());
            answer.setAcceptedAutoScore(true);
            answer.setScoreSource("AUTO_ACCEPTED");
        } else {
            if (request.score() == null) {
                throw new BusinessException(40000, "题目分数不能为空");
            }
            answer.setScore(request.score());
            answer.setAcceptedAutoScore(false);
            answer.setScoreSource("TEACHER");
        }
        answer.setTeacherComment(support.defaultString(request.teacherComment()));
        HomeworkAnswer saved = homeworkAnswerRepository.save(answer);
        support.refreshSubmissionGradeState(submission, currentUser.schoolId());
        return Map.of(
                "answerId", saved.getId(),
                "score", saved.getScore(),
                "acceptedAutoScore", saved.isAcceptedAutoScore()
        );
    }

}
