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
public class HomeworkConfigService {

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

    public HomeworkConfigService(HomeworkRepository homeworkRepository,
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


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherHomeworks(CurrentUser currentUser) {
        support.requireTeacher(currentUser);
        return homeworkRepository.findBySchoolIdAndCreatedByOrderByIdDesc(currentUser.schoolId(), currentUser.id()).stream()
                .map(this::toTeacherHomeworkView)
                .toList();
    }



    @Transactional
    public Map<String, Object> createHomework(CurrentUser currentUser, HomeworkRequests.CreateHomeworkRequest request) {
        support.requireTeacher(currentUser);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        Homework homework = new Homework();
        homework.setCreatedBy(currentUser.id());
        if (currentUser.schoolId() == null) {
            throw new BusinessException(40000, "账号未绑定学校，无法创建教学数据");
        }
        homework.setSchoolId(currentUser.schoolId());
        applyHomework(homework, true, request.classId(), request.title(), request.description(), request.status(), request.scoreVisibilityMode(), request.attachmentPath(), request.startAt(), request.dueAt());
        return toTeacherHomeworkView(homeworkRepository.save(homework));
    }



    @Transactional
    public Map<String, Object> updateHomework(CurrentUser currentUser, Long homeworkId, HomeworkRequests.UpdateHomeworkRequest request) {
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        // 有学生提交后换班会让既有提交落在错误班级，必须先于班级权限校验拒绝。
        boolean hasSubmissions = !homeworkSubmissionRepository.findByHomeworkId(homeworkId).isEmpty();
        if (hasSubmissions && !Objects.equals(request.classId(), homework.getClassId())) {
            throw new BusinessException(40000, "已有学生提交，不能变更所属班级");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        applyHomework(homework, false, request.classId(), request.title(), request.description(), request.status(), request.scoreVisibilityMode(), request.attachmentPath(), request.startAt(), request.dueAt());
        return toTeacherHomeworkView(homeworkRepository.save(homework));
    }



    @Transactional
    public Map<String, Object> changeHomeworkStatus(CurrentUser currentUser, Long homeworkId, HomeworkRequests.ChangeHomeworkStatusRequest request) {
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        ActivityStatus target = parseStatus(request.status());
        // 状态接口与编辑接口共用同一迁移矩阵，避免两条入口口径漂移。
        assertStatusTransition(homework, target, !homeworkSubmissionRepository.findByHomeworkId(homeworkId).isEmpty());
        homework.setStatus(target);
        return toTeacherHomeworkView(homeworkRepository.save(homework));
    }



    @Transactional
    public Map<String, Object> releaseScores(CurrentUser currentUser, Long homeworkId) {
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        homework.setScoreReleased(true);
        homeworkRepository.save(homework);
        return ScoreReleaseSupport.releasedView(homework.getScoreVisibilityMode().name());
    }



    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentHomeworks(CurrentUser currentUser) {
        support.requireStudent(currentUser);
        List<Long> classIds = classMemberRepository.findByStudentUserId(currentUser.id()).stream()
                .map(ClassMember::getClassId)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return homeworkRepository.findBySchoolIdAndClassIdInAndStatusOrderByIdDesc(currentUser.schoolId(), classIds, ActivityStatus.PUBLISHED).stream()
                .map(homework -> toStudentHomeworkView(homework, currentUser.id()))
                .toList();
    }



    @Transactional(readOnly = true)
    public Map<String, Object> getStudentHomeworkDetail(CurrentUser currentUser, Long homeworkId) {
        support.requireStudent(currentUser);
        Homework homework = support.accessiblePublishedHomework(currentUser, homeworkId);
        HomeworkSubmission submission = homeworkSubmissionRepository.findByHomeworkIdAndStudentId(homeworkId, currentUser.id()).orElse(null);
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        boolean scoreVisible = isStudentScoreVisible(homework, submission);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", homework.getId());
        detail.put("title", homework.getTitle());
        detail.put("description", support.defaultString(homework.getDescription()));
        detail.put("classId", homework.getClassId());
        detail.put("className", classRoom == null ? "" : classRoom.getName());
        detail.put("status", homework.getStatus().name());
        detail.put("startAt", homework.getStartAt() == null ? null : homework.getStartAt().toString());
        detail.put("dueAt", homework.getDueAt() == null ? null : homework.getDueAt().toString());
        detail.put("attachmentPath", homework.getAttachmentPath());
        detail.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        detail.put("answerText", submission == null ? "" : support.defaultString(submission.getAnswerText()));
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
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        return homeworkSubmissionRepository.findByHomeworkId(homework.getId()).stream()
                .map(submission -> toTeacherSubmissionView(submission, currentUser.schoolId()))
                .toList();
    }



    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherSubmissionDetail(CurrentUser currentUser, Long submissionId) {
        support.requireTeacher(currentUser);
        HomeworkSubmission submission = homeworkSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "作业提交不存在"));
        support.ownedHomework(currentUser, submission.getHomeworkId());
        Map<String, Object> detail = new LinkedHashMap<>(toTeacherSubmissionView(submission, currentUser.schoolId()));
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
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        ClassRoom classRoom = classRoomRepository.findById(homework.getClassId()).orElse(null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", homework.getId());
        detail.put("title", homework.getTitle());
        detail.put("description", support.defaultString(homework.getDescription()));
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
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        assertDraftOnly(homework, "新增题目");
        QuestionBank questionBank = questionBankRepository.findByIdAndSchoolIdAndCreatedBy(request.getQuestionId(), currentUser.schoolId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40400, "题库题目不存在"));
        HomeworkQuestion question = new HomeworkQuestion();
        question.setHomeworkId(homework.getId());
        question.setQuestionId(questionBank.getId());
        question.setSortOrder(request.getSortOrder());
        question.setQuestionScore(request.getQuestionScore());
        question.setQuestionSnapshotJson(support.writeJson(buildQuestionSnapshot(
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
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
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
        question.setQuestionSnapshotJson(support.writeJson(buildQuestionSnapshot(
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
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
        HomeworkQuestion question = homeworkQuestionRepository.findByIdAndHomeworkId(homeworkQuestionId, homeworkId)
                .orElseThrow(() -> new BusinessException(40400, "作业题目不存在"));
        Map<String, Object> snapshot = support.readJsonMap(question.getQuestionSnapshotJson());
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
        question.setQuestionSnapshotJson(support.writeJson(rebuilt));
        return toHomeworkQuestionAdminView(homeworkQuestionRepository.save(question));
    }



    /**
     * 删除作业题目：仅草稿允许，且已有学生作答的题目不允许删除。
     */
    @Transactional
    public Map<String, Object> deleteQuestion(CurrentUser currentUser, Long homeworkId, Long homeworkQuestionId) {
        support.requireTeacher(currentUser);
        Homework homework = support.ownedHomework(currentUser, homeworkId);
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



    /**
     * 作业题目的教师端视图，供详情、新增、更新三处共用，字段固定不漂移。
     */
    private Map<String, Object> toHomeworkQuestionAdminView(HomeworkQuestion question) {
        Map<String, Object> snapshot = support.readJsonMap(question.getQuestionSnapshotJson());
        List<Map<String, Object>> options = QuestionContentSupport.normalizeOptions(support.writeJson(snapshot.get("options")));
        Object answer = snapshot.get("answer");
        Object scoringConfig = snapshot.get("scoringConfig");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", question.getId());
        view.put("questionId", question.getQuestionId());
        view.put("sortOrder", question.getSortOrder());
        view.put("type", snapshot.get("type"));
        view.put("stem", snapshot.get("stem") == null ? "" : snapshot.get("stem"));
        view.put("options", options);
        view.put("optionsJson", support.writeJson(options));
        view.put("answerJson", support.writeJson(answer == null ? List.of() : answer));
        view.put("analysisText", snapshot.get("analysisText") == null ? "" : snapshot.get("analysisText"));
        view.put("scoringConfigJson",
                scoringConfig instanceof Map<?, ?> map && !map.isEmpty() ? support.writeJson(map) : null);
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
        List<Map<String, Object>> snapshotOptions = QuestionContentSupport.normalizeOptions(support.writeJson(snapshot.get("options")));
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
        Map<String, Object> requestScoring = support.readJsonMap(request.scoringConfigJson());
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
        snapshot.put("scoringConfig", support.readJsonValue(scoringConfigJson, new TypeReference<Map<String, Object>>() {}, Map.of()));
        snapshot.put("analysisText", analysisText == null ? "" : analysisText.trim());
        snapshot.put("difficulty", difficulty == null || difficulty.isBlank() ? "MEDIUM" : difficulty);
        snapshot.put("fromBank", fromBank);
        return snapshot;
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
        Map<String, Object> snapshot = support.readJsonMap(question.getQuestionSnapshotJson());
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
        view.put("answerText", support.defaultString(answer.getAnswerText()));
        view.put("answerJson", answer.getAnswerJson());
        view.put("score", scoreVisible ? answer.getScore() : null);
        view.put("autoScore", teacherView || scoreVisible ? answer.getAutoScore() : null);
        view.put("suggestedScore", teacherView || scoreVisible ? answer.getSuggestedScore() : null);
        view.put("scoreSource", teacherView ? answer.getScoreSource() : null);
        view.put("judgeDetail", teacherView ? answer.getJudgeDetail() : null);
        view.put("teacherComment", teacherView ? answer.getTeacherComment() : null);
        return view;
    }



    /**
     * 写入作业配置字段。
     *
     * <p>空值语义按“创建 / 更新”分流：更新时未传 {@code scoreVisibilityMode} 或 {@code status}
     * 保留原值，避免教师只改标题就把可见性或状态重置；创建时未传可见性按
     * {@link ScoreVisibilityMode#AFTER_TEACHER_CONFIRM}，未传状态按 {@link ActivityStatus#DRAFT}。</p>
     *
     * @param isCreate 是否为创建流程，决定空值回退方向
     */
    private void applyHomework(Homework homework,
                               boolean isCreate,
                               Long classId,
                               String title,
                               String description,
                               String status,
                               ScoreVisibilityMode scoreVisibilityMode,
                               String attachmentPath,
                               OffsetDateTime startAt,
                               OffsetDateTime dueAt) {
        homework.setTitle(title);
        homework.setDescription(support.defaultString(description));
        homework.setClassId(classId);
        if (scoreVisibilityMode != null) {
            homework.setScoreVisibilityMode(scoreVisibilityMode);
        } else if (isCreate) {
            homework.setScoreVisibilityMode(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        }
        if (status != null && !status.isBlank()) {
            ActivityStatus target = parseStatus(status);
            boolean hasSubmissions = !isCreate && !homeworkSubmissionRepository.findByHomeworkId(homework.getId()).isEmpty();
            assertStatusTransition(homework, target, hasSubmissions);
            homework.setStatus(target);
        } else if (isCreate) {
            homework.setStatus(ActivityStatus.DRAFT);
        }
        homework.setAttachmentPath(support.blankToNull(attachmentPath));
        homework.setStartAt(startAt);
        homework.setDueAt(dueAt);
    }



    /**
     * 校验作业状态迁移是否合法。
     *
     * <p>允许：{@code DRAFT → PUBLISHED}、{@code PUBLISHED → CLOSED}、
     * 无提交时 {@code PUBLISHED → DRAFT}；{@code CLOSED} 不可再变更；
     * 同状态视为无变更放行。</p>
     *
     * @param homework       当前作业，{@code status} 为 {@code null} 时按 {@code DRAFT} 处理
     * @param target         目标状态
     * @param hasSubmissions 是否已有学生提交（任何状态，含 {@code SAVED}）
     */
    private void assertStatusTransition(Homework homework, ActivityStatus target, boolean hasSubmissions) {
        ActivityStatus current = homework.getStatus() == null ? ActivityStatus.DRAFT : homework.getStatus();
        if (current == target) {
            return;
        }
        boolean allowed = switch (current) {
            case DRAFT -> target == ActivityStatus.PUBLISHED;
            case PUBLISHED -> target == ActivityStatus.CLOSED
                    || (target == ActivityStatus.DRAFT && !hasSubmissions);
            case CLOSED -> false;
        };
        if (!allowed) {
            throw new BusinessException(40000, "作业状态不允许从 " + current.name() + " 变更为 " + target.name());
        }
    }



    private ActivityStatus parseStatus(String value) {
        return ActivityStatusParser.parse(value, "作业状态");
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
        view.put("description", support.defaultString(homework.getDescription()));
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
        view.put("description", support.defaultString(homework.getDescription()));
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



    private Map<String, Object> toTeacherSubmissionView(HomeworkSubmission submission, Long schoolId) {
        Homework homework = homeworkRepository.findByIdAndSchoolId(submission.getHomeworkId(), schoolId)
                .orElseThrow(() -> new BusinessException(40400, "作业不存在"));
        SysUser student = sysUserRepository.findById(submission.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        PlagiarismTask plagiarismTask = plagiarismTaskRepository
                .findByBusinessTypeAndBusinessIdAndSchoolId(BusinessType.HOMEWORK, submission.getId(), schoolId)
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
        view.put("answerText", support.defaultString(submission.getAnswerText()));
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

}
