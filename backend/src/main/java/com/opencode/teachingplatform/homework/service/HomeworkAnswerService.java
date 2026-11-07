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
public class HomeworkAnswerService {

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

    public HomeworkAnswerService(HomeworkRepository homeworkRepository,
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
    public Map<String, Object> submitHomework(CurrentUser currentUser, Long homeworkId, HomeworkRequests.SubmitHomeworkRequest request) {
        support.requireStudent(currentUser);
        Homework homework = support.accessiblePublishedHomework(currentUser, homeworkId);
        if (homework.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "作业已关闭，不能提交");
        }
        // 开始时间门禁：位于任何读提交行/写操作之前，命中时不产生任何数据变更。
        if (homework.getStartAt() != null && OffsetDateTime.now().isBefore(homework.getStartAt())) {
            throw new BusinessException(40000, "作业尚未开始");
        }
        // 截止时间门禁：位于任何写操作之前，命中时不产生提交/答案/查重/成绩写入。
        if (homework.getDueAt() != null && OffsetDateTime.now().isAfter(homework.getDueAt())) {
            throw new BusinessException(40000, "作业已截止，不能提交");
        }
        // 快路径：普通读一次取锁前快照（查询次数与改造前持平）；读到即跳过独立事务领取。
        SubmissionStatus claimStatus = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homeworkId, currentUser.id())
                .map(HomeworkSubmission::getSubmitStatus)
                .orElse(null);
        // 慢路径：仅首存才用独立事务领取，唯一键冲突只回滚内层事务。
        if (claimStatus == null) {
            try {
                claimStatus = homeworkSubmissionClaimService.claim(homeworkId, currentUser.id());
            } catch (DataIntegrityViolationException ex) {
                // 并发首存：另一事务已插入，下面锁内重读
                claimStatus = SubmissionStatus.SAVED;
            }
        }
        // 锁内重读：规避 REPEATABLE READ 快照读不到并发事务新行的问题。
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentIdForUpdate(homeworkId, currentUser.id())
                .orElseThrow(() -> new BusinessException(50000, "作业提交创建失败，请重试"));
        // 锁内状态优先：顺序重复提交沿用原文案；并发重复提交按幂等成功返回。
        if (submission.getSubmitStatus() == SubmissionStatus.SUBMITTED
                || submission.getSubmitStatus() == SubmissionStatus.GRADED) {
            if (claimStatus != SubmissionStatus.SAVED) {
                if (submission.getSubmitStatus() == SubmissionStatus.SUBMITTED) {
                    throw new BusinessException(40000, "作业已提交，如需修改请联系教师打回");
                }
                throw new BusinessException(40000, "作业已批改，如需修改请联系教师打回");
            }
            return Map.of(
                    "submissionId", submission.getId(),
                    "status", submission.getSubmitStatus().name(),
                    "plagiarismRate", Objects.requireNonNullElse(submission.getPlagiarismRate(), 0D)
            );
        }
        submission.setHomeworkId(homeworkId);
        submission.setStudentId(currentUser.id());
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setAnswerText(support.defaultString(request.answerText()));
        submission.setAnswerFilePath(support.blankToNull(request.resolvedAttachmentPath()));
        submission.setTotalScore(submission.getTotalScore() == null ? 0D : submission.getTotalScore());
        submission.setTeacherComment(submission.getTeacherComment());
        HomeworkSubmission saved = homeworkSubmissionRepository.save(submission);
        if (request.questionBased()) {
            applyQuestionAnswers(saved, request.answers());
        }
        // 全客观题作业在提交阶段即完成结案：含主观题或存在未评分答案时保持/置回 SUBMITTED
        support.refreshSubmissionGradeState(saved);
        LocalPlagiarismService.PlagiarismAnalysisResult plagiarismResult = localPlagiarismService.analyze(
                plagiarismSourceText(saved, request),
                buildHomeworkCandidates(saved.getHomeworkId(), saved.getId())
        );
        saved.setPlagiarismRate(plagiarismResult.similarityRate());
        homeworkSubmissionRepository.save(saved);
        plagiarismTaskWriter.write(BusinessType.HOMEWORK, saved.getId(), saved.getStudentId(),
                plagiarismResult.similarityRate(), plagiarismResult.topMatchSummary(), plagiarismResult.rawResultJson());

        return Map.of(
                "submissionId", saved.getId(),
                "status", saved.getSubmitStatus().name(),
                "plagiarismRate", saved.getPlagiarismRate()
        );
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
        support.requireStudent(currentUser);
        Homework homework = support.accessiblePublishedHomework(currentUser, homeworkId);
        if (homework.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "作业已关闭，不能保存草稿");
        }
        if (homework.getStartAt() != null && OffsetDateTime.now().isBefore(homework.getStartAt())) {
            throw new BusinessException(40000, "作业尚未开始");
        }
        if (homework.getDueAt() != null && OffsetDateTime.now().isAfter(homework.getDueAt())) {
            throw new BusinessException(40000, "作业已截止，不能保存草稿");
        }
        // 快路径：普通读一次判断是否已有提交行；慢路径仅首存走独立事务领取。
        SubmissionStatus claimStatus = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentId(homeworkId, currentUser.id())
                .map(HomeworkSubmission::getSubmitStatus)
                .orElse(null);
        if (claimStatus == null) {
            try {
                homeworkSubmissionClaimService.claim(homeworkId, currentUser.id());
            } catch (DataIntegrityViolationException ex) {
                // 并发首存：另一事务已插入，下面锁内重读
            }
        }
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentIdForUpdate(homeworkId, currentUser.id())
                .orElseThrow(() -> new BusinessException(50000, "作业提交创建失败，请重试"));
        if (submission.getSubmitStatus() == SubmissionStatus.SUBMITTED
                || submission.getSubmitStatus() == SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "已提交作业不可修改草稿");
        }
        submission.setHomeworkId(homeworkId);
        submission.setStudentId(currentUser.id());
        submission.setSubmitStatus(SubmissionStatus.SAVED);
        submission.setAnswerText(support.defaultString(request.answerText()));
        submission.setAnswerFilePath(support.blankToNull(request.resolvedAttachmentPath()));
        // 草稿阶段的提交级评分字段一律清空；plagiarismRate 保留原值（草稿不触发查重）。
        submission.setTotalScore(null);
        submission.setTeacherComment(null);
        submission.setGradedAt(null);
        if (request.questionBased()) {
            applyDraftAnswers(submission, request.answers());
        }
        HomeworkSubmission saved = homeworkSubmissionRepository.saveAndFlush(submission);
        OffsetDateTime savedAt = Objects.requireNonNullElse(saved.getUpdatedAt(), OffsetDateTime.now());
        return Map.of(
                "submissionId", saved.getId(),
                "status", SubmissionStatus.SAVED.name(),
                "savedAt", savedAt.toString()
        );
    }



    /**
     * 草稿态写入逐题答案：只保留作答内容，评分字段全部清空。
     *
     * <p>{@code score_source} 列 {@code NOT NULL DEFAULT 'TEACHER'}，因此写 {@code "TEACHER"}
     * 而不是 {@code null}；其余评分字段允许为空。</p>
     */
    private void applyDraftAnswers(HomeworkSubmission submission, List<HomeworkRequests.SubmitHomeworkAnswerItem> items) {
        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(submission.getHomeworkId());
        Map<Long, HomeworkQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(HomeworkQuestion::getId, item -> item));
        for (HomeworkRequests.SubmitHomeworkAnswerItem item : items) {
            HomeworkQuestion homeworkQuestion = questionMap.get(item.homeworkQuestionId());
            if (homeworkQuestion == null) {
                throw new BusinessException(40000, "提交题目不存在");
            }
            HomeworkAnswer answer = homeworkAnswerRepository
                    .findByHomeworkSubmissionIdAndHomeworkQuestionId(submission.getId(), homeworkQuestion.getId())
                    .orElseGet(HomeworkAnswer::new);
            answer.setHomeworkSubmissionId(submission.getId());
            answer.setHomeworkQuestionId(homeworkQuestion.getId());
            answer.setQuestionId(homeworkQuestion.getQuestionId());
            answer.setAnswerText(support.blankToNull(item.answerText()));
            answer.setAnswerJson(resolveAnswerJson(item));
            answer.setScore(null);
            answer.setAutoScore(null);
            answer.setSuggestedScore(null);
            answer.setJudgeDetail(null);
            answer.setTeacherComment(null);
            answer.setAcceptedAutoScore(false);
            answer.setScoreSource("TEACHER");
            homeworkAnswerRepository.save(answer);
        }
    }



    private void applyQuestionAnswers(HomeworkSubmission submission, List<HomeworkRequests.SubmitHomeworkAnswerItem> items) {
        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(submission.getHomeworkId());
        Map<Long, HomeworkQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(HomeworkQuestion::getId, item -> item));
        double totalScore = 0D;
        for (HomeworkRequests.SubmitHomeworkAnswerItem item : items) {
            HomeworkQuestion homeworkQuestion = questionMap.get(item.homeworkQuestionId());
            if (homeworkQuestion == null) {
                throw new BusinessException(40000, "提交题目不存在");
            }
            Map<String, Object> snapshot = support.readJsonMap(homeworkQuestion.getQuestionSnapshotJson());
            HomeworkAnswer answer = homeworkAnswerRepository
                    .findByHomeworkSubmissionIdAndHomeworkQuestionId(submission.getId(), homeworkQuestion.getId())
                    .orElseGet(HomeworkAnswer::new);
            answer.setHomeworkSubmissionId(submission.getId());
            answer.setHomeworkQuestionId(homeworkQuestion.getId());
            answer.setQuestionId(homeworkQuestion.getQuestionId());
            answer.setAnswerText(support.blankToNull(item.answerText()));
            answer.setAnswerJson(resolveAnswerJson(item));
            applyScoring(answer, homeworkQuestion, snapshot, item);
            homeworkAnswerRepository.save(answer);
            if (answer.getScore() != null) {
                totalScore += answer.getScore();
            }
        }
        submission.setTotalScore(totalScore);
    }



    private void applyScoring(HomeworkAnswer answer,
                              HomeworkQuestion homeworkQuestion,
                              Map<String, Object> snapshot,
                              HomeworkRequests.SubmitHomeworkAnswerItem item) {
        QuestionSnapshot questionSnapshot = toQuestionSnapshot(snapshot, homeworkQuestion);
        QuestionAnswerPayload answerPayload = AnswerPayloadNormalizer.normalize(
                homeworkQuestion.getId(),
                String.valueOf(snapshot.get("type")),
                item.answerText(),
                item.answerJson(),
                item.selectedOptions(),
                item.attachmentPath()
        );
        GradingResultView result = gradingAdapter.grade(questionSnapshot, answerPayload);
        answer.setAutoScore(result.autoScore());
        answer.setSuggestedScore(result.suggestedScore());
        answer.setJudgeDetail(result.judgeDetail());
        answer.setScoreSource(result.scoreSource());
        answer.setAcceptedAutoScore(Objects.equals(result.scoreSource(), "AUTO")
                || Objects.equals(result.scoreSource(), "AUTO_ACCEPTED"));
        answer.setScore(result.finalScore());
    }



    private String plagiarismSourceText(HomeworkSubmission submission, HomeworkRequests.SubmitHomeworkRequest request) {
        if (!request.questionBased()) {
            return support.defaultString(request.answerText());
        }
        return submissionCandidateText(submission);
    }



    private String resolveAnswerJson(HomeworkRequests.SubmitHomeworkAnswerItem item) {
        if (item.answerJson() != null && !item.answerJson().isBlank()) {
            return item.answerJson();
        }
        if (item.selectedOptions() != null && !item.selectedOptions().isEmpty()) {
            return support.writeJson(item.selectedOptions());
        }
        return null;
    }



    private QuestionSnapshot toQuestionSnapshot(Map<String, Object> snapshot, HomeworkQuestion homeworkQuestion) {
        return QuestionSnapshotFactory.fromStored(
                homeworkQuestion.getQuestionId(),
                Boolean.TRUE.equals(snapshot.get("fromBank")) ? "BANK" : "INLINE",
                String.valueOf(snapshot.get("type")),
                String.valueOf(snapshot.get("stem")),
                homeworkQuestion.getSortOrder(),
                homeworkQuestion.getQuestionScore(),
                support.writeJson(snapshot.get("options")),
                support.writeJson(snapshot.get("answer")),
                support.writeJson(snapshot.get("scoringConfig")),
                Boolean.TRUE.equals(snapshot.get("fromBank"))
        );
    }



    /**
     * 构造作业查重候选集：只与同一作业下的已提交/已批改提交比对，避免跨作业误判。
     *
     * @param homeworkId          作业 id
     * @param currentSubmissionId 当前提交 id（从候选集中排除）
     * @return 候选文本集合
     */
    private List<LocalPlagiarismService.CandidateText> buildHomeworkCandidates(Long homeworkId, Long currentSubmissionId) {
        return homeworkSubmissionRepository
                .findByHomeworkIdAndSubmitStatusIn(homeworkId, List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED))
                .stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), currentSubmissionId))
                .map(candidate -> Map.entry(candidate, submissionCandidateText(candidate)))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> new LocalPlagiarismService.CandidateText(resolveHomeworkReference(entry.getKey().getHomeworkId()), entry.getValue()))
                .toList();
    }



    private String submissionCandidateText(HomeworkSubmission submission) {
        List<HomeworkAnswer> answers = homeworkAnswerRepository.findByHomeworkSubmissionId(submission.getId());
        if (!answers.isEmpty()) {
            return answers.stream()
                    .map(answer -> answer.getAnswerText() == null || answer.getAnswerText().isBlank() ? answer.getAnswerJson() : answer.getAnswerText())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
        }
        return support.defaultString(submission.getAnswerText());
    }



    private String resolveHomeworkReference(Long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .map(homework -> homework.getTitle() + "#" + homework.getId())
                .orElse("homework#" + homeworkId);
    }

}
