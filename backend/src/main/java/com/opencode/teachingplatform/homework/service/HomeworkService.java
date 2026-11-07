package com.opencode.teachingplatform.homework.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.assessment.model.GradingResultView;
import com.opencode.teachingplatform.assessment.model.QuestionAnswerPayload;
import com.opencode.teachingplatform.assessment.model.QuestionSnapshot;
import com.opencode.teachingplatform.assessment.service.AnswerPayloadNormalizer;
import com.opencode.teachingplatform.assessment.service.GradingAdapter;
import com.opencode.teachingplatform.assessment.service.QuestionContentSupport;
import com.opencode.teachingplatform.assessment.service.QuestionContentValidator;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.common.audit.AuditLogService;
import com.opencode.teachingplatform.common.audit.AuditResourceType;
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
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

    public HomeworkService(HomeworkRepository homeworkRepository,
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
                           HomeworkSubmissionClaimService homeworkSubmissionClaimService) {
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
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherHomeworks(CurrentUser currentUser) {
        requireTeacher(currentUser);
        return homeworkRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream()
                .map(this::toTeacherHomeworkView)
                .toList();
    }

    @Transactional
    public Map<String, Object> createHomework(CurrentUser currentUser, HomeworkRequests.CreateHomeworkRequest request) {
        requireTeacher(currentUser);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        Homework homework = new Homework();
        homework.setCreatedBy(currentUser.id());
        applyHomework(homework, request.classId(), request.title(), request.description(), request.status(), request.scoreVisibilityMode(), request.attachmentPath(), request.startAt(), request.dueAt());
        return toTeacherHomeworkView(homeworkRepository.save(homework));
    }

    @Transactional
    public Map<String, Object> updateHomework(CurrentUser currentUser, Long homeworkId, HomeworkRequests.UpdateHomeworkRequest request) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        applyHomework(homework, request.classId(), request.title(), request.description(), request.status(), request.scoreVisibilityMode(), request.attachmentPath(), request.startAt(), request.dueAt());
        return toTeacherHomeworkView(homeworkRepository.save(homework));
    }

    @Transactional
    public Map<String, Object> changeHomeworkStatus(CurrentUser currentUser, Long homeworkId, HomeworkRequests.ChangeHomeworkStatusRequest request) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        homework.setStatus(parseStatus(request.status()));
        return toTeacherHomeworkView(homeworkRepository.save(homework));
    }

    @Transactional
    public Map<String, Object> releaseScores(CurrentUser currentUser, Long homeworkId) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        homework.setScoreReleased(true);
        homeworkRepository.save(homework);
        return Map.of("released", true, "scoreVisibilityMode", homework.getScoreVisibilityMode().name());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentHomeworks(CurrentUser currentUser) {
        requireStudent(currentUser);
        List<Long> classIds = classMemberRepository.findByStudentUserId(currentUser.id()).stream()
                .map(ClassMember::getClassId)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return homeworkRepository.findByClassIdInAndStatusOrderByIdDesc(classIds, ActivityStatus.PUBLISHED).stream()
                .map(homework -> toStudentHomeworkView(homework, currentUser.id()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentHomeworkDetail(CurrentUser currentUser, Long homeworkId) {
        requireStudent(currentUser);
        Homework homework = accessiblePublishedHomework(currentUser, homeworkId);
        HomeworkSubmission submission = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homeworkId, currentUser.id()).orElse(null);
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        boolean scoreVisible = isStudentScoreVisible(homework, submission);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", homework.getId());
        detail.put("title", homework.getTitle());
        detail.put("description", defaultString(homework.getDescription()));
        detail.put("classId", homework.getClassId());
        detail.put("className", classRoom == null ? "" : classRoom.getName());
        detail.put("status", homework.getStatus().name());
        detail.put("startAt", homework.getStartAt() == null ? null : homework.getStartAt().toString());
        detail.put("dueAt", homework.getDueAt() == null ? null : homework.getDueAt().toString());
        detail.put("attachmentPath", homework.getAttachmentPath());
        detail.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        detail.put("answerText", submission == null ? "" : defaultString(submission.getAnswerText()));
        detail.put("answerFilePath", submission == null ? null : submission.getAnswerFilePath());
        detail.put("attachmentAnswerPath", submission == null ? null : submission.getAnswerFilePath());
        detail.put("plagiarismRate", submission == null ? null : submission.getPlagiarismRate());
        detail.put("totalScore", submission == null || !scoreVisible ? null : submission.getTotalScore());
        detail.put("teacherComment", submission == null || !scoreVisible ? null : submission.getTeacherComment());
        detail.put("remainingMinutes", homework.getDueAt() == null ? null : remainingMinutes(homework.getDueAt()));
        detail.put("hasSubmittedAttachment", submission != null && submission.getAnswerFilePath() != null && !submission.getAnswerFilePath().isBlank());
        detail.put("questions", buildQuestionViews(homework.getId(), submission, scoreVisible, false));
        return detail;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherSubmissions(CurrentUser currentUser, Long homeworkId) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        return homeworkSubmissionRepository.findByHomeworkId(homework.getId()).stream()
                .map(this::toTeacherSubmissionView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherSubmissionDetail(CurrentUser currentUser, Long submissionId) {
        requireTeacher(currentUser);
        HomeworkSubmission submission = homeworkSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        ownedHomework(currentUser, submission.getHomeworkId());
        Map<String, Object> detail = new LinkedHashMap<>(toTeacherSubmissionView(submission));
        detail.put("questions", buildQuestionViews(submission.getHomeworkId(), submission, true, true));
        return detail;
    }

    /**
     * 教师作业详情：用于编辑弹窗回显与前端增量提交。
     *
     * <p>返回作业基础字段与题目列表，题目字段与新增/更新接口的响应保持一致。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherHomeworkDetail(CurrentUser currentUser, Long homeworkId) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", homework.getId());
        detail.put("title", homework.getTitle());
        detail.put("description", defaultString(homework.getDescription()));
        detail.put("classId", homework.getClassId());
        detail.put("className", classRoom == null ? "" : classRoom.getName());
        detail.put("status", homework.getStatus().name());
        detail.put("scoreVisibilityMode", homework.getScoreVisibilityMode().name());
        detail.put("startAt", homework.getStartAt() == null ? null : homework.getStartAt().toString());
        detail.put("dueAt", homework.getDueAt() == null ? null : homework.getDueAt().toString());
        detail.put("attachmentPath", homework.getAttachmentPath());
        detail.put("questions", homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homeworkId).stream()
                .map(this::toHomeworkQuestionAdminView)
                .toList());
        return detail;
    }

    @Transactional
    public Map<String, Object> addQuestionFromBank(CurrentUser currentUser, Long homeworkId, HomeworkRequests.AddHomeworkQuestionFromBankRequest request) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        assertDraftOnly(homework, "新增题目");
        QuestionBank questionBank = questionBankRepository.findByIdAndCreatedBy(request.getQuestionId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40400, "题库题目不存在"));
        HomeworkQuestion question = new HomeworkQuestion();
        question.setHomeworkId(homework.getId());
        question.setQuestionId(questionBank.getId());
        question.setSortOrder(request.getSortOrder());
        question.setQuestionScore(request.getQuestionScore());
        question.setQuestionSnapshotJson(writeJson(buildQuestionSnapshot(
                questionBank.getType(),
                questionBank.getStem(),
                request.getQuestionScore(),
                questionBank.getOptionsJson(),
                questionBank.getAnswerJson(),
                questionBank.getScoringConfigJson(),
                questionBank.getAnalysisText(),
                questionBank.getDifficulty(),
                true
        )));
        return toHomeworkQuestionAdminView(homeworkQuestionRepository.save(question));
    }

    @Transactional
    public Map<String, Object> addInlineQuestion(CurrentUser currentUser, Long homeworkId, HomeworkRequests.SaveHomeworkQuestionRequest request) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        assertDraftOnly(homework, "新增题目");
        questionContentValidator.validate(
                request.type(),
                request.stem(),
                request.questionScore(),
                request.optionsJson(),
                request.answerJson(),
                request.scoringConfigJson()
        );
        HomeworkQuestion question = new HomeworkQuestion();
        question.setHomeworkId(homework.getId());
        question.setQuestionId(null);
        question.setSortOrder(request.sortOrder());
        question.setQuestionScore(request.questionScore());
        question.setQuestionSnapshotJson(writeJson(buildQuestionSnapshot(
                request.type(),
                request.stem(),
                request.questionScore(),
                request.optionsJson(),
                request.answerJson(),
                request.scoringConfigJson(),
                request.analysisText(),
                "MEDIUM",
                false
        )));
        return toHomeworkQuestionAdminView(homeworkQuestionRepository.save(question));
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
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        HomeworkQuestion question = homeworkQuestionRepository.findByIdAndHomeworkId(homeworkQuestionId, homeworkId)
                .orElseThrow(() -> new BusinessException(40400, "作业题目不存在"));
        Map<String, Object> snapshot = readJsonMap(question.getQuestionSnapshotJson());
        ActivityStatus status = homework.getStatus();
        if (status == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "作业已关闭，不能修改题目");
        }
        if (status == ActivityStatus.PUBLISHED) {
            if (!isContentUnchanged(snapshot, request)) {
                throw new BusinessException(40000, "已发布作业仅支持调整排序");
            }
        } else {
            questionContentValidator.validate(
                    request.type(),
                    request.stem(),
                    request.questionScore(),
                    request.optionsJson(),
                    request.answerJson(),
                    request.scoringConfigJson()
            );
            if (homeworkAnswerRepository.existsByHomeworkQuestionId(homeworkQuestionId)
                    && !sameScore(snapshot.get("questionScore"), request.questionScore())) {
                throw new BusinessException(40000, "该题目已有学生作答，不能修改分值");
            }
        }

        Map<String, Object> rebuilt = new LinkedHashMap<>();
        rebuilt.put("type", request.type().trim());
        rebuilt.put("stem", request.stem().trim());
        rebuilt.put("questionScore", request.questionScore());
        rebuilt.put("options", QuestionContentSupport.normalizeOptions(request.optionsJson()));
        rebuilt.put("answer", readJsonRaw(request.answerJson()));
        Object requestScoringConfig = readJsonRawObject(request.scoringConfigJson());
        // 请求未携带评分参数时保留原快照值，避免已发布排序保存时丢失 keywords / rubric
        rebuilt.put("scoringConfig", requestScoringConfig instanceof Map<?, ?> ? requestScoringConfig : snapshot.getOrDefault("scoringConfig", Map.of()));
        rebuilt.put("analysisText", request.analysisText() == null ? "" : request.analysisText().trim());
        rebuilt.put("difficulty", snapshot.get("difficulty") == null ? "MEDIUM" : snapshot.get("difficulty"));
        rebuilt.put("fromBank", Boolean.TRUE.equals(snapshot.get("fromBank")));
        question.setSortOrder(request.sortOrder());
        question.setQuestionScore(request.questionScore());
        question.setQuestionSnapshotJson(writeJson(rebuilt));
        return toHomeworkQuestionAdminView(homeworkQuestionRepository.save(question));
    }

    /**
     * 删除作业题目：仅草稿允许，且已有学生作答的题目不允许删除。
     */
    @Transactional
    public Map<String, Object> deleteQuestion(CurrentUser currentUser, Long homeworkId, Long homeworkQuestionId) {
        requireTeacher(currentUser);
        Homework homework = ownedHomework(currentUser, homeworkId);
        HomeworkQuestion question = homeworkQuestionRepository.findByIdAndHomeworkId(homeworkQuestionId, homeworkId)
                .orElseThrow(() -> new BusinessException(40400, "作业题目不存在"));
        if (homework.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(40000, "已发布或已关闭作业不能删除题目");
        }
        if (homeworkAnswerRepository.existsByHomeworkQuestionId(homeworkQuestionId)) {
            throw new BusinessException(40000, "该题目已有学生作答，不能删除");
        }
        homeworkQuestionRepository.delete(question);
        return Map.of("deleted", true, "id", homeworkQuestionId);
    }

    private void assertDraftOnly(Homework homework, String action) {
        if (homework.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(40000, "仅草稿状态的作业可以" + action);
        }
    }

    @Transactional
    public Map<String, Object> submitHomework(CurrentUser currentUser, Long homeworkId, HomeworkRequests.SubmitHomeworkRequest request) {
        requireStudent(currentUser);
        Homework homework = accessiblePublishedHomework(currentUser, homeworkId);
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
        submission.setAnswerText(defaultString(request.answerText()));
        submission.setAnswerFilePath(blankToNull(request.resolvedAttachmentPath()));
        submission.setTotalScore(submission.getTotalScore() == null ? 0D : submission.getTotalScore());
        submission.setTeacherComment(submission.getTeacherComment());
        HomeworkSubmission saved = homeworkSubmissionRepository.save(submission);
        if (request.questionBased()) {
            applyQuestionAnswers(saved, request.answers());
        }
        // 全客观题作业在提交阶段即完成结案：含主观题或存在未评分答案时保持/置回 SUBMITTED
        refreshSubmissionGradeState(saved);
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

    @Transactional
    public Map<String, Object> gradeSubmission(CurrentUser currentUser, Long submissionId, HomeworkRequests.GradeHomeworkRequest request) {
        requireTeacher(currentUser);
        HomeworkSubmission head = homeworkSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        Homework homework = ownedHomework(currentUser, head.getHomeworkId());
        // 先取业务键 (homeworkId, studentId)，再在锁内重读并重跑状态校验，保证成绩写入串行化。
        HomeworkSubmission submission = homeworkSubmissionRepository
                .findByHomeworkIdAndStudentIdForUpdate(homework.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        if (submission.getSubmitStatus() != SubmissionStatus.SUBMITTED) {
            throw new BusinessException(40000, "仅已提交作业允许批改");
        }
        submission.setTotalScore(request.totalScore());
        submission.setTeacherComment(defaultString(request.teacherComment()));
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(OffsetDateTime.now());
        homeworkSubmissionRepository.save(submission);
        scoreRecordWriter.write(BusinessType.HOMEWORK, homework.getId(), submission.getStudentId(),
                homework.getClassId(), request.totalScore(), OffsetDateTime.now());
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
        requireTeacher(currentUser);
        HomeworkSubmission head = homeworkSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        Homework homework = ownedHomework(currentUser, head.getHomeworkId());
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
                .findByBusinessTypeAndBusinessIdAndStudentId(BusinessType.HOMEWORK, homework.getId(), submission.getStudentId())
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
        requireTeacher(currentUser);
        HomeworkAnswer answer = homeworkAnswerRepository.findById(answerId)
                .orElseThrow(() -> new BusinessException(40400, "作业答案不存在"));
        HomeworkSubmission head = homeworkSubmissionRepository.findById(answer.getHomeworkSubmissionId())
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        Homework homework = ownedHomework(currentUser, head.getHomeworkId());
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
        answer.setTeacherComment(defaultString(request.teacherComment()));
        HomeworkAnswer saved = homeworkAnswerRepository.save(answer);
        refreshSubmissionGradeState(submission);
        return Map.of(
                "answerId", saved.getId(),
                "score", saved.getScore(),
                "acceptedAutoScore", saved.isAcceptedAutoScore()
        );
    }

    private void requireTeacher(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.TEACHER) {
            throw new BusinessException(40300, "无教师权限");
        }
    }

    private void requireStudent(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.STUDENT) {
            throw new BusinessException(40300, "无学生权限");
        }
    }

    private Homework ownedHomework(CurrentUser currentUser, Long homeworkId) {
        Homework homework = homeworkRepository.findByIdAndCreatedBy(homeworkId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该作业"));
        classAccessService.assertTeacherAssignedToClass(currentUser, homework.getClassId());
        return homework;
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
            Map<String, Object> snapshot = readJsonMap(homeworkQuestion.getQuestionSnapshotJson());
            HomeworkAnswer answer = homeworkAnswerRepository
                    .findByHomeworkSubmissionIdAndHomeworkQuestionId(submission.getId(), homeworkQuestion.getId())
                    .orElseGet(HomeworkAnswer::new);
            answer.setHomeworkSubmissionId(submission.getId());
            answer.setHomeworkQuestionId(homeworkQuestion.getId());
            answer.setQuestionId(homeworkQuestion.getQuestionId());
            answer.setAnswerText(blankToNull(item.answerText()));
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

    private Homework accessiblePublishedHomework(CurrentUser currentUser, Long homeworkId) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new BusinessException(40400, "作业不存在"));
        if (homework.getStatus() == ActivityStatus.DRAFT) {
            throw new BusinessException(40300, "无权限访问该作业");
        }
        boolean enrolled = classMemberRepository.findByClassIdAndStudentUserId(homework.getClassId(), currentUser.id()).isPresent();
        if (!enrolled) {
            throw new BusinessException(40300, "无权限访问该作业");
        }
        return homework;
    }

    private String plagiarismSourceText(HomeworkSubmission submission, HomeworkRequests.SubmitHomeworkRequest request) {
        if (!request.questionBased()) {
            return defaultString(request.answerText());
        }
        return submissionCandidateText(submission);
    }

    /**
     * 作业题目的教师端视图，供详情、新增、更新三处共用，字段固定不漂移。
     */
    private Map<String, Object> toHomeworkQuestionAdminView(HomeworkQuestion question) {
        Map<String, Object> snapshot = readJsonMap(question.getQuestionSnapshotJson());
        List<Map<String, Object>> options = QuestionContentSupport.normalizeOptions(writeJson(snapshot.get("options")));
        Object answer = snapshot.get("answer");
        Object scoringConfig = snapshot.get("scoringConfig");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", question.getId());
        view.put("questionId", question.getQuestionId());
        view.put("sortOrder", question.getSortOrder());
        view.put("type", snapshot.get("type"));
        view.put("stem", snapshot.get("stem") == null ? "" : snapshot.get("stem"));
        view.put("options", options);
        view.put("optionsJson", writeJson(options));
        view.put("answerJson", writeJson(answer == null ? List.of() : answer));
        view.put("analysisText", snapshot.get("analysisText") == null ? "" : snapshot.get("analysisText"));
        view.put("scoringConfigJson",
                scoringConfig instanceof Map<?, ?> map && !map.isEmpty() ? writeJson(map) : null);
        view.put("score", question.getQuestionScore());
        view.put("sourceType", question.getQuestionId() != null ? "BANK" : "INLINE");
        view.put("difficulty", snapshot.get("difficulty") == null ? "MEDIUM" : snapshot.get("difficulty"));
        return view;
    }

    /**
     * 判断已发布作业的题目除排序外是否被修改。
     *
     * <p>比较全部基于归一后结构：选项按 key 排序后比较 key/content，答案按 JSON 结构深比较，
     * 评分配置以 Map 深比较，空串与 null 等价。</p>
     */
    private boolean isContentUnchanged(Map<String, Object> snapshot, HomeworkRequests.SaveHomeworkQuestionRequest request) {
        String snapshotType = snapshot.get("type") == null ? "" : String.valueOf(snapshot.get("type")).trim().toUpperCase();
        String requestType = request.type() == null ? "" : request.type().trim().toUpperCase();
        if (!snapshotType.equals(requestType)) {
            return false;
        }
        String snapshotStem = snapshot.get("stem") == null ? "" : String.valueOf(snapshot.get("stem")).trim();
        if (!snapshotStem.equals(request.stem().trim())) {
            return false;
        }
        List<Map<String, Object>> snapshotOptions = QuestionContentSupport.normalizeOptions(writeJson(snapshot.get("options")));
        List<Map<String, Object>> requestOptions = QuestionContentSupport.normalizeOptions(request.optionsJson());
        if (!optionsEqual(snapshotOptions, requestOptions)) {
            return false;
        }
        Object snapshotAnswer = snapshot.get("answer");
        Object requestAnswer = readJsonRaw(request.answerJson());
        if (!jsonDeepEquals(snapshotAnswer, requestAnswer)) {
            return false;
        }
        String snapshotAnalysis = snapshot.get("analysisText") == null ? "" : String.valueOf(snapshot.get("analysisText")).trim();
        String requestAnalysis = request.analysisText() == null ? "" : request.analysisText().trim();
        if (!snapshotAnalysis.equals(requestAnalysis)) {
            return false;
        }
        if (!sameScore(snapshot.get("questionScore"), request.questionScore())) {
            return false;
        }
        Map<String, Object> snapshotScoring = snapshot.get("scoringConfig") instanceof Map<?, ?> map
                ? toStringObjectMap(map)
                : Map.of();
        Map<String, Object> requestScoring = readJsonMap(request.scoringConfigJson());
        return snapshotScoring.equals(requestScoring);
    }

    private boolean optionsEqual(List<Map<String, Object>> left, List<Map<String, Object>> right) {
        if (left.size() != right.size()) {
            return false;
        }
        List<Map<String, Object>> sortedLeft = sortOptionsByKey(left);
        List<Map<String, Object>> sortedRight = sortOptionsByKey(right);
        for (int index = 0; index < sortedLeft.size(); index++) {
            Map<String, Object> leftOption = sortedLeft.get(index);
            Map<String, Object> rightOption = sortedRight.get(index);
            if (!String.valueOf(leftOption.get("key")).equals(String.valueOf(rightOption.get("key")))) {
                return false;
            }
            if (!String.valueOf(leftOption.get("content")).equals(String.valueOf(rightOption.get("content")))) {
                return false;
            }
        }
        return true;
    }

    private List<Map<String, Object>> sortOptionsByKey(List<Map<String, Object>> options) {
        return options.stream()
                .sorted((left, right) -> String.valueOf(left.get("key")).compareTo(String.valueOf(right.get("key"))))
                .toList();
    }

    private boolean sameScore(Object left, Double right) {
        if (!(left instanceof Number number) || right == null) {
            return false;
        }
        return Math.abs(number.doubleValue() - right) < 0.0001D;
    }

    /**
     * JSON 结构深比较：数字按数值比较，数组按顺序比较（填空题按 index 升序归一后再比较）。
     */
    private boolean jsonDeepEquals(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Math.abs(leftNumber.doubleValue() - rightNumber.doubleValue()) < 0.0001D;
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            if (!leftMap.keySet().equals(rightMap.keySet())) {
                return false;
            }
            for (Object key : leftMap.keySet()) {
                if (!jsonDeepEquals(leftMap.get(key), rightMap.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if (leftList.size() != rightList.size()) {
                return false;
            }
            for (int index = 0; index < leftList.size(); index++) {
                if (!jsonDeepEquals(leftList.get(index), rightList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Object readJsonRawObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception ex) {
            throw new BusinessException(40000, "JSON 数据格式不正确");
        }
    }

    /**
     * 构造作业题目快照。
     *
     * <p>快照是作业运行时的唯一真相：选项统一归一为 {@code [{key, content}]}，
     * 答案保留原始 JSON 数组形态，评分参数与解析文本独立存放。</p>
     */
    private Map<String, Object> buildQuestionSnapshot(String type,
                                                      String stem,
                                                      Double questionScore,
                                                      String optionsJson,
                                                      String answerJson,
                                                      String scoringConfigJson,
                                                      String analysisText,
                                                      String difficulty,
                                                      boolean fromBank) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", type);
        snapshot.put("stem", stem);
        snapshot.put("questionScore", questionScore);
        snapshot.put("options", QuestionContentSupport.normalizeOptions(optionsJson));
        snapshot.put("answer", readJsonRaw(answerJson));
        snapshot.put("scoringConfig", readJsonValue(scoringConfigJson, new TypeReference<Map<String, Object>>() {}, Map.of()));
        snapshot.put("analysisText", analysisText == null ? "" : analysisText.trim());
        snapshot.put("difficulty", difficulty == null || difficulty.isBlank() ? "MEDIUM" : difficulty);
        snapshot.put("fromBank", fromBank);
        return snapshot;
    }

    private String resolveAnswerJson(HomeworkRequests.SubmitHomeworkAnswerItem item) {
        if (item.answerJson() != null && !item.answerJson().isBlank()) {
            return item.answerJson();
        }
        if (item.selectedOptions() != null && !item.selectedOptions().isEmpty()) {
            return writeJson(item.selectedOptions());
        }
        return null;
    }

    private QuestionSnapshot toQuestionSnapshot(Map<String, Object> snapshot, HomeworkQuestion homeworkQuestion) {
        return new QuestionSnapshot(
                homeworkQuestion.getQuestionId(),
                Boolean.TRUE.equals(snapshot.get("fromBank")) ? "BANK" : "INLINE",
                String.valueOf(snapshot.get("type")),
                String.valueOf(snapshot.get("stem")),
                homeworkQuestion.getSortOrder(),
                homeworkQuestion.getQuestionScore(),
                writeJson(snapshot.get("options")),
                writeJson(snapshot.get("answer")),
                writeJson(snapshot.get("scoringConfig")),
                Boolean.TRUE.equals(snapshot.get("fromBank"))
        );
    }

    private Map<String, Object> readJsonMap(String json) {
        return readJsonValue(json, new TypeReference<Map<String, Object>>() {}, Map.of());
    }

    private Object readJsonRaw(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception ex) {
            throw new BusinessException(40000, "JSON 数据格式不正确");
        }
    }

    private <T> T readJsonValue(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new BusinessException(40000, "JSON 数据格式不正确");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(50000, "JSON 序列化失败");
        }
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
        return defaultString(submission.getAnswerText());
    }

    private boolean isStudentScoreVisible(Homework homework, HomeworkSubmission submission) {
        if (submission == null) {
            return false;
        }
        return switch (homework.getScoreVisibilityMode()) {
            case IMMEDIATE -> true;
            case AFTER_TEACHER_CONFIRM -> submission.getSubmitStatus() == SubmissionStatus.GRADED;
            case AFTER_DEADLINE -> homework.getDueAt() != null && !OffsetDateTime.now().isBefore(homework.getDueAt());
            case MANUAL_RELEASE -> homework.isScoreReleased();
        };
    }

    private void refreshSubmissionGradeState(HomeworkSubmission submission) {
        Homework homework = homeworkRepository.findById(submission.getHomeworkId())
                .orElseThrow(() -> new BusinessException(40400, "作业不存在"));
        List<HomeworkAnswer> answers = homeworkAnswerRepository.findByHomeworkSubmissionId(submission.getId());
        double totalScore = answers.stream()
                .map(HomeworkAnswer::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        submission.setTotalScore(totalScore);

        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(submission.getHomeworkId());
        boolean allAnswered = !questions.isEmpty() && questions.stream().allMatch(question ->
                answers.stream().anyMatch(answer -> Objects.equals(answer.getHomeworkQuestionId(), question.getId())));
        boolean allScored = allAnswered && answers.stream().allMatch(answer -> answer.getScore() != null);

        if (allScored) {
            submission.setSubmitStatus(SubmissionStatus.GRADED);
            submission.setGradedAt(OffsetDateTime.now());
            upsertScoreRecord(homework, submission.getStudentId(), submission.getTotalScore(), submission.getGradedAt());
        } else {
            submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
            submission.setGradedAt(null);
        }
        homeworkSubmissionRepository.save(submission);
    }

    private void upsertScoreRecord(Homework homework, Long studentId, Double score, OffsetDateTime gradedAt) {
        scoreRecordWriter.write(BusinessType.HOMEWORK, homework.getId(), studentId,
                homework.getClassId(), score, gradedAt);
    }

    private List<Map<String, Object>> buildQuestionViews(Long homeworkId,
                                                         HomeworkSubmission submission,
                                                         boolean scoreVisible,
                                                         boolean teacherView) {
        List<HomeworkQuestion> questions = homeworkQuestionRepository.findByHomeworkIdOrderBySortOrder(homeworkId);
        Map<Long, HomeworkAnswer> answerMap = submission == null ? Map.of() : homeworkAnswerRepository.findByHomeworkSubmissionId(submission.getId()).stream()
                .collect(Collectors.toMap(HomeworkAnswer::getHomeworkQuestionId, item -> item));
        return questions.stream()
                .map(question -> toQuestionView(question, answerMap.get(question.getId()), scoreVisible, teacherView))
                .toList();
    }

    private Map<String, Object> toQuestionView(HomeworkQuestion question,
                                               HomeworkAnswer answer,
                                               boolean scoreVisible,
                                               boolean teacherView) {
        Map<String, Object> snapshot = readJsonMap(question.getQuestionSnapshotJson());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", question.getId());
        view.put("questionId", question.getQuestionId());
        view.put("sortOrder", question.getSortOrder());
        view.put("type", snapshot.get("type"));
        view.put("stem", snapshot.get("stem"));
        view.put("options", snapshot.get("options"));
        view.put("score", question.getQuestionScore());
        view.put("answer", toAnswerView(answer, scoreVisible, teacherView));
        return view;
    }

    private Map<String, Object> toAnswerView(HomeworkAnswer answer, boolean scoreVisible, boolean teacherView) {
        if (answer == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", answer.getId());
        view.put("homeworkQuestionId", answer.getHomeworkQuestionId());
        view.put("answerText", defaultString(answer.getAnswerText()));
        view.put("answerJson", answer.getAnswerJson());
        view.put("score", scoreVisible ? answer.getScore() : null);
        view.put("autoScore", teacherView || scoreVisible ? answer.getAutoScore() : null);
        view.put("suggestedScore", teacherView || scoreVisible ? answer.getSuggestedScore() : null);
        view.put("scoreSource", teacherView ? answer.getScoreSource() : null);
        view.put("judgeDetail", teacherView ? answer.getJudgeDetail() : null);
        view.put("teacherComment", teacherView ? answer.getTeacherComment() : null);
        return view;
    }

    private String resolveHomeworkReference(Long homeworkId) {
        return homeworkRepository.findById(homeworkId)
                .map(homework -> homework.getTitle() + "#" + homework.getId())
                .orElse("homework#" + homeworkId);
    }

    private void applyHomework(Homework homework,
                               Long classId,
                               String title,
                               String description,
                               String status,
                               ScoreVisibilityMode scoreVisibilityMode,
                               String attachmentPath,
                               OffsetDateTime startAt,
                               OffsetDateTime dueAt) {
        homework.setTitle(title);
        homework.setDescription(defaultString(description));
        homework.setClassId(classId);
        homework.setStatus(parseStatus(status));
        homework.setScoreVisibilityMode(scoreVisibilityMode == null ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM : scoreVisibilityMode);
        homework.setAttachmentPath(blankToNull(attachmentPath));
        homework.setStartAt(startAt);
        homework.setDueAt(dueAt);
    }

    private ActivityStatus parseStatus(String value) {
        return value == null || value.isBlank()
                ? ActivityStatus.DRAFT
                : EnumValueParser.parse(ActivityStatus.class, value, "作业状态");
    }

    private Map<String, Object> toTeacherHomeworkView(Homework homework) {
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        List<HomeworkSubmission> submissions = homeworkSubmissionRepository.findByHomeworkId(homework.getId());
        long submissionCount = submissions.size();
        long submittedCount = submissions.stream().filter(item -> item.getSubmitStatus() == SubmissionStatus.SUBMITTED).count();
        long gradedCount = submissions.stream().filter(item -> item.getSubmitStatus() == SubmissionStatus.GRADED).count();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", homework.getId());
        view.put("title", homework.getTitle());
        view.put("description", defaultString(homework.getDescription()));
        view.put("classId", homework.getClassId());
        view.put("className", classRoom == null ? "" : classRoom.getName());
        view.put("status", homework.getStatus().name());
        view.put("attachmentPath", homework.getAttachmentPath());
        view.put("startAt", homework.getStartAt() == null ? null : homework.getStartAt().toString());
        view.put("dueAt", homework.getDueAt() == null ? null : homework.getDueAt().toString());
        view.put("submissionCount", submissionCount);
        view.put("submittedCount", submittedCount);
        view.put("gradedCount", gradedCount);
        view.put("pendingCount", submissionCount - gradedCount);
        return view;
    }

    private Map<String, Object> toStudentHomeworkView(Homework homework, Long studentId) {
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        HomeworkSubmission submission = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homework.getId(), studentId).orElse(null);
        boolean scoreVisible = isStudentScoreVisible(homework, submission);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", homework.getId());
        view.put("title", homework.getTitle());
        view.put("description", defaultString(homework.getDescription()));
        view.put("classId", homework.getClassId());
        view.put("className", classRoom == null ? "" : classRoom.getName());
        view.put("status", homework.getStatus().name());
        view.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        view.put("totalScore", submission == null || !scoreVisible ? null : submission.getTotalScore());
        view.put("teacherComment", submission == null || !scoreVisible ? null : submission.getTeacherComment());
        view.put("dueAt", homework.getDueAt() == null ? null : homework.getDueAt().toString());
        view.put("startAt", homework.getStartAt() == null ? null : homework.getStartAt().toString());
        view.put("remainingMinutes", homework.getDueAt() == null ? null : remainingMinutes(homework.getDueAt()));
        return view;
    }

    private Map<String, Object> toTeacherSubmissionView(HomeworkSubmission submission) {
        Homework homework = homeworkRepository.findById(submission.getHomeworkId())
                .orElseThrow(() -> new BusinessException(40400, "作业不存在"));
        SysUser student = sysUserRepository.findById(submission.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        PlagiarismTask plagiarismTask = plagiarismTaskRepository
                .findByBusinessTypeAndBusinessId(BusinessType.HOMEWORK, submission.getId())
                .orElse(null);
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        Map<Long, Long> classStudentCounts = classMemberRepository.findByClassIdIn(List.of(homework.getClassId())).stream()
                .collect(Collectors.groupingBy(ClassMember::getClassId, Collectors.counting()));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", submission.getId());
        view.put("homeworkId", homework.getId());
        view.put("homeworkTitle", homework.getTitle());
        view.put("classId", homework.getClassId());
        view.put("className", classRoom == null ? "" : classRoom.getName());
        view.put("studentId", student.getId());
        view.put("studentName", student.getDisplayName());
        view.put("studentUsername", student.getUsername());
        view.put("studentNo", student.getUsername());
        view.put("submitStatus", submission.getSubmitStatus().name());
        view.put("answerText", defaultString(submission.getAnswerText()));
        view.put("answerFilePath", submission.getAnswerFilePath());
        view.put("plagiarismRate", submission.getPlagiarismRate());
        view.put("topMatchSummary", plagiarismTask == null ? null : plagiarismTask.getTopMatchSummary());
        view.put("totalScore", submission.getTotalScore());
        view.put("teacherComment", submission.getTeacherComment());
        view.put("classStudentCount", classStudentCounts.getOrDefault(homework.getClassId(), 0L));
        view.put("submittedAt", submission.getCreatedAt() == null ? null : submission.getCreatedAt().toString());
        view.put("updatedAt", submission.getUpdatedAt() == null ? null : submission.getUpdatedAt().toString());
        return view;
    }

    private Long remainingMinutes(OffsetDateTime dueAt) {
        return ChronoUnit.MINUTES.between(OffsetDateTime.now(), dueAt);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
