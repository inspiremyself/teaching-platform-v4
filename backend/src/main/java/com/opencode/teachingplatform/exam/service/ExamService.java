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
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.UserRole;
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

    /** 交卷宽限期（秒）：截止时间后仍允许手动交卷，超时自动交卷同样以此边界判定。 */
    public static final long SUBMIT_GRACE_SECONDS = 120L;

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassAccessService classAccessService;
    private final ExamScoringService examScoringService;
    private final SysUserRepository sysUserRepository;
    private final ScoreRecordWriter scoreRecordWriter;
    private final TransactionTemplate transactionTemplate;
    private final GradingAdapter gradingAdapter;
    private final ObjectMapper objectMapper;

    public ExamService(ExamRepository examRepository,
                       ExamQuestionRepository examQuestionRepository,
                       ExamSubmissionRepository examSubmissionRepository,
                       ExamAnswerRepository examAnswerRepository,
                       QuestionBankRepository questionBankRepository,
                       ClassMemberRepository classMemberRepository,
                       ClassRoomRepository classRoomRepository,
                       ClassAccessService classAccessService,
                       ExamScoringService examScoringService,
                       SysUserRepository sysUserRepository,
                       ScoreRecordWriter scoreRecordWriter,
                       PlatformTransactionManager transactionManager,
                       ObjectProvider<GradingAdapter> gradingAdapterProvider,
                       ObjectProvider<ScoringEngine> scoringEngineProvider,
                       ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.examSubmissionRepository = examSubmissionRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.questionBankRepository = questionBankRepository;
        this.classMemberRepository = classMemberRepository;
        this.classRoomRepository = classRoomRepository;
        this.classAccessService = classAccessService;
        this.examScoringService = examScoringService;
        this.sysUserRepository = sysUserRepository;
        this.scoreRecordWriter = scoreRecordWriter;
        // 开始考试需要“创建提交”独立事务：唯一键冲突必须在外层捕获后再重读，
        // 因此这里用事务模板实现，不新增 Bean 与独立类。
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.gradingAdapter = gradingAdapterProvider.getIfAvailable(() -> new GradingAdapter(scoringEngineProvider.getIfAvailable(), this.objectMapper));
    }

    public Object listExams(CurrentUser currentUser) {
        if (currentUser.role() == UserRole.TEACHER) {
            List<Exam> exams = examRepository.findByCreatedBy(currentUser.id());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Exam exam : exams) {
                var classRoom = classRoomRepository.findById(exam.getClassId()).orElse(null);
                long submissionCount = examSubmissionRepository.countByExamId(exam.getId());
                long questionCount = examQuestionRepository.findByExamIdOrderBySortOrder(exam.getId()).size();
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", exam.getId());
                map.put("title", exam.getTitle());
                map.put("description", exam.getDescription());
                map.put("classId", exam.getClassId());
                map.put("className", classRoom == null ? "" : classRoom.getName());
                map.put("startAt", exam.getStartAt());
                map.put("endAt", exam.getEndAt());
                map.put("durationMinutes", exam.getDurationMinutes());
                map.put("status", exam.getStatus().name());
                map.put("submissionCount", submissionCount);
                map.put("questionCount", questionCount);
                result.add(map);
            }
            return result;
        } else {
            // Student: show published exams, and also retain exams that already have a submission/result
            List<ClassMember> memberships = classMemberRepository.findByStudentUserId(currentUser.id());
            List<Map<String, Object>> result = new ArrayList<>();
            OffsetDateTime now = OffsetDateTime.now();
            for (ClassMember membership : memberships) {
                List<Exam> exams = examRepository.findByClassId(membership.getClassId());
                for (Exam exam : exams) {
                    Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findByExamIdAndStudentId(exam.getId(), currentUser.id());
                    if (exam.getStatus() == ActivityStatus.DRAFT) {
                        continue;
                    }
                    if (exam.getStatus() == ActivityStatus.CLOSED && submissionOpt.isEmpty()) {
                        continue;
                    }
                    var classRoom = classRoomRepository.findById(exam.getClassId()).orElse(null);
                    boolean canStart = submissionOpt.isEmpty()
                            && (exam.getStartAt() == null || !now.isBefore(exam.getStartAt()))
                            && (exam.getEndAt() == null || now.isBefore(exam.getEndAt()));
                    boolean canResume = submissionOpt.isPresent()
                            && submissionOpt.get().getStatus() == ExamSubmissionStatus.IN_PROGRESS
                            && (submissionOpt.get().getDeadlineAt() == null
                                || !now.isAfter(submissionOpt.get().getDeadlineAt().plusSeconds(SUBMIT_GRACE_SECONDS)));
                    boolean canViewResult = submissionOpt.isPresent() && canStudentSeeScore(exam, submissionOpt.get());

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", exam.getId());
                    map.put("title", exam.getTitle());
                    map.put("description", exam.getDescription());
                    map.put("classId", exam.getClassId());
                    map.put("className", classRoom == null ? "" : classRoom.getName());
                    map.put("startAt", exam.getStartAt());
                    map.put("endAt", exam.getEndAt());
                    map.put("durationMinutes", exam.getDurationMinutes());
                    map.put("status", exam.getStatus().name());
                    map.put("submissionStatus", submissionOpt.map(item -> item.getStatus().name()).orElse(null));
                    map.put("totalScore", canViewResult ? submissionOpt.map(ExamSubmission::getTotalScore).orElse(null) : null);
                    map.put("canStart", canStart);
                    map.put("canResume", canResume);
                    map.put("canViewResult", canViewResult);
                    result.add(map);
                }
            }
            return result;
        }
    }

    @Transactional
    public Object createExam(CurrentUser currentUser, ExamRequests.CreateExamRequest request) {
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        Exam exam = new Exam();
        exam.setTitle(request.title());
        exam.setDescription(request.description());
        exam.setClassId(request.classId());
        exam.setStartAt(request.startAt());
        exam.setEndAt(request.endAt());
        exam.setDurationMinutes(request.durationMinutes());
        exam.setStatus(EnumValueParser.parse(ActivityStatus.class, request.status(), "考试状态"));
        exam.setScoreVisibilityMode(request.scoreVisibilityMode() == null ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM : request.scoreVisibilityMode());
        exam.setShuffleQuestions(Boolean.TRUE.equals(request.shuffleQuestions()));
        exam.setCreatedBy(currentUser.id());
        assertBankQuestions(request.questions());
        assertNoDuplicateQuestions(request.questions());
        Map<Long, QuestionBank> bankQuestions = loadOwnedBankQuestions(request.questions(), currentUser.id());
        examRepository.save(exam);

        if (request.questions() != null) {
            for (ExamRequests.ExamQuestionItem item : request.questions()) {
                examQuestionRepository.save(buildExamQuestion(exam.getId(), item, bankQuestions.get(item.questionId())));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.getId());
        result.put("title", exam.getTitle());
        result.put("classId", exam.getClassId());
        result.put("status", exam.getStatus().name());
        return result;
    }

    @Transactional
    public Object updateExam(CurrentUser currentUser, Long id, ExamRequests.UpdateExamRequest request) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限修改该考试");
        }
        if (exam.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(40000, "只有草稿状态的考试可以修改");
        }

        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());

        // Validate question-bank ownership before any mutation so a 404 leaves the exam untouched.
        assertBankQuestions(request.questions());
        assertNoDuplicateQuestions(request.questions());
        Map<Long, QuestionBank> bankQuestions = loadOwnedBankQuestions(request.questions(), currentUser.id());

        exam.setTitle(request.title());
        exam.setDescription(request.description());
        exam.setClassId(request.classId());
        exam.setStartAt(request.startAt());
        exam.setEndAt(request.endAt());
        exam.setDurationMinutes(request.durationMinutes());
        exam.setScoreVisibilityMode(request.scoreVisibilityMode() == null ? exam.getScoreVisibilityMode() : request.scoreVisibilityMode());
        if (request.shuffleQuestions() != null) {
            exam.setShuffleQuestions(request.shuffleQuestions());
        }
        examRepository.save(exam);

        if (request.questions() != null) {
            examQuestionRepository.deleteByExamId(id);
            for (ExamRequests.ExamQuestionItem item : request.questions()) {
                examQuestionRepository.save(buildExamQuestion(exam.getId(), item, bankQuestions.get(item.questionId())));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.getId());
        result.put("title", exam.getTitle());
        result.put("classId", exam.getClassId());
        result.put("status", exam.getStatus().name());
        return result;
    }

    public Object getExamDetail(CurrentUser currentUser, Long id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));

        if (currentUser.role() == UserRole.TEACHER && !exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限查看该考试");
        }

        if (currentUser.role() == UserRole.STUDENT) {
            if (exam.getStatus() != ActivityStatus.PUBLISHED && exam.getStatus() != ActivityStatus.CLOSED) {
                throw new BusinessException(40300, "无权限查看该考试");
            }
            boolean isMember = classMemberRepository.findByClassIdAndStudentUserId(exam.getClassId(), currentUser.id()).isPresent();
            if (!isMember) {
                throw new BusinessException(40300, "无权限查看该考试");
            }
        }

        // 学生未到开始时间时只下发基础信息：不下发题干/选项，也不回填任何提交与判分字段，避免提前泄题
        boolean notStarted = currentUser.role() == UserRole.STUDENT
                && exam.getStartAt() != null
                && OffsetDateTime.now().isBefore(exam.getStartAt());
        if (notStarted) {
            Map<String, Object> result = buildExamBaseView(exam);
            result.put("questions", List.of());
            result.put("notStarted", true);
            return result;
        }

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySortOrder(id);

        List<Map<String, Object>> questionList = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            QuestionSnapshot snapshot = resolveQuestionSnapshot(eq);
            Map<String, Object> qMap = new LinkedHashMap<>();
            qMap.put("questionId", eq.getQuestionId());
            qMap.put("sortOrder", eq.getSortOrder());
            qMap.put("questionScore", eq.getQuestionScore());

            qMap.put("type", snapshot.questionType());
            qMap.put("stem", snapshot.stem());
            qMap.put("optionsJson", snapshot.optionsJson());
            if (currentUser.role() == UserRole.TEACHER) {
                qMap.put("answerJson", snapshot.answerJson());
                if (eq.getQuestionId() != null) {
                    questionBankRepository.findById(eq.getQuestionId()).ifPresent(qb -> qMap.put("analysisText", qb.getAnalysisText()));
                }
            }

            questionList.add(qMap);
        }

        Map<String, Object> result = buildExamBaseView(exam);
        result.put("questions", questionList);
        // 教师结果页需要知道当前考试是否已发布成绩，用于「发布 / 重新发布」入口。
        if (currentUser.role() == UserRole.TEACHER) {
            result.put("scoreReleased", exam.isScoreReleased());
        }

        // For students: enrich with submission state so frontend can restore progress/result
        if (currentUser.role() == UserRole.STUDENT) {
            Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findByExamIdAndStudentId(id, currentUser.id());
            if (submissionOpt.isPresent()) {
                ExamSubmission submission = submissionOpt.get();
                boolean scoreVisible = canStudentSeeScore(exam, submission);
                result.put("submissionId", submission.getId());
                result.put("submissionStatus", submission.getStatus().name());
                result.put("startedAt", submission.getStartedAt());
                result.put("submittedAt", submission.getSubmittedAt());
                // 成绩不可见时不泄露任何判分字段，避免提前推断客观题对错。
                result.put("autoScore", scoreVisible ? submission.getAutoScore() : null);
                result.put("manualScore", scoreVisible ? submission.getManualScore() : null);
                result.put("totalScore", scoreVisible ? submission.getTotalScore() : null);
                result.put("resultAvailable", scoreVisible);
                // 阅卷中：仅 SUBMITTED / AUTO_SUBMITTED 为 true，IN_PROGRESS 与 GRADED 恒为 false。
                result.put("gradingInProgress",
                        submission.getStatus() == ExamSubmissionStatus.SUBMITTED
                                || submission.getStatus() == ExamSubmissionStatus.AUTO_SUBMITTED);

                if (submission.getStatus() == ExamSubmissionStatus.IN_PROGRESS) {
                    result.put("deadlineAt", submission.getDeadlineAt());
                    long remainingSeconds = 0;
                    if (submission.getDeadlineAt() != null) {
                        remainingSeconds = Math.max(0, Duration.between(OffsetDateTime.now(), submission.getDeadlineAt()).getSeconds());
                    }
                    result.put("remainingSeconds", remainingSeconds);
                    // 作答中仅回填草稿答案，不返回任何判分与解析字段。
                    Map<Long, ExamAnswer> draftMap = examAnswerRepository.findByExamSubmissionId(submission.getId())
                            .stream()
                            .collect(Collectors.toMap(ExamAnswer::getQuestionId, item -> item, (left, right) -> right));
                    for (Map<String, Object> qMap : questionList) {
                        ExamAnswer draft = draftMap.get((Long) qMap.get("questionId"));
                        if (draft != null) {
                            qMap.put("studentAnswer", draft.getAnswerJson());
                        }
                    }
                }

                if (submission.getStatus() == ExamSubmissionStatus.SUBMITTED
                        || submission.getStatus() == ExamSubmissionStatus.AUTO_SUBMITTED
                        || submission.getStatus() == ExamSubmissionStatus.GRADED) {
                    // Attach student answers + grading info to each question
                    List<ExamAnswer> answers = examAnswerRepository.findByExamSubmissionId(submission.getId());
                    Map<Long, ExamAnswer> answerMap = new HashMap<>();
                    for (ExamAnswer a : answers) {
                        answerMap.put(a.getQuestionId(), a);
                    }
                    for (Map<String, Object> qMap : questionList) {
                        Long questionId = (Long) qMap.get("questionId");
                        ExamAnswer ea = answerMap.get(questionId);
                        if (ea != null) {
                            qMap.put("studentAnswer", ea.getAnswerJson());
                            qMap.put("isCorrect", scoreVisible ? ea.getIsCorrect() : null);
                            qMap.put("earnedScore", scoreVisible ? ea.getScore() : null);
                            qMap.put("teacherComment", scoreVisible ? ea.getTeacherComment() : null);
                        }
                    }
                    if (scoreVisible) {
                        for (Map<String, Object> qMap : questionList) {
                            Long questionId = (Long) qMap.get("questionId");
                            examQuestions.stream()
                                    .filter(item -> Objects.equals(item.getQuestionId(), questionId))
                                    .findFirst()
                                    .ifPresent(item -> {
                                        qMap.put("answerJson", resolveQuestionSnapshot(item).answerJson());
                                        if (item.getQuestionId() != null) {
                                            questionBankRepository.findById(item.getQuestionId())
                                                    .ifPresent(qb -> qMap.put("analysisText", qb.getAnalysisText()));
                                        }
                                    });
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 构建考试详情的基础字段视图。
     *
     * <p>「未开始」短路返回与正常详情共用同一份基础字段，避免两处字段漂移。</p>
     */
    private Map<String, Object> buildExamBaseView(Exam exam) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.getId());
        result.put("title", exam.getTitle());
        result.put("description", exam.getDescription());
        result.put("classId", exam.getClassId());
        result.put("className", classRoomRepository.findById(exam.getClassId()).map(cr -> cr.getName()).orElse(""));
        result.put("startAt", exam.getStartAt());
        result.put("endAt", exam.getEndAt());
        result.put("durationMinutes", exam.getDurationMinutes());
        result.put("status", exam.getStatus().name());
        result.put("scoreVisibilityMode", exam.getScoreVisibilityMode().name());
        result.put("shuffleQuestions", exam.isShuffleQuestions());
        return result;
    }

    /**
     * 校验成绩是否处于可改分状态。
     *
     * <p>冻结条件（任一命中即拒绝）：</p>
     * <ul>
     *   <li>成绩已发布（{@code scoreReleased == true}）；</li>
     *   <li>可见模式为 {@code AFTER_DEADLINE} 且考试结束时间已过。</li>
     * </ul>
     */
    private void assertScoreEditable(Exam exam) {
        ScoreVisibilityMode mode = exam.getScoreVisibilityMode() == null
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : exam.getScoreVisibilityMode();
        boolean deadlineReached = mode == ScoreVisibilityMode.AFTER_DEADLINE
                && exam.getEndAt() != null
                && !OffsetDateTime.now().isBefore(exam.getEndAt());
        if (exam.isScoreReleased() || deadlineReached) {
            throw new BusinessException(40000, "成绩已发布或已到期，禁止直接改分");
        }
    }

    @Transactional
    public Object changeStatus(CurrentUser currentUser, Long id, ExamRequests.ChangeExamStatusRequest request) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限修改该考试");
        }

        ActivityStatus newStatus = EnumValueParser.parse(ActivityStatus.class, request.status(), "考试状态");
        if (exam.getStatus() == ActivityStatus.DRAFT && newStatus == ActivityStatus.PUBLISHED) {
            boolean hasLegacyInlineQuestion = examQuestionRepository
                    .findByExamIdOrderBySortOrder(id).stream()
                    .anyMatch(item -> item.getQuestionId() == null);
            if (hasLegacyInlineQuestion) {
                throw new BusinessException(40000, "请先将题目保存到题库后再组卷");
            }
            exam.setStatus(ActivityStatus.PUBLISHED);
        } else if (exam.getStatus() == ActivityStatus.PUBLISHED && newStatus == ActivityStatus.CLOSED) {
            exam.setStatus(ActivityStatus.CLOSED);
        } else {
            throw new BusinessException(40000, "不允许的状态变更: " + exam.getStatus() + " → " + newStatus);
        }
        examRepository.save(exam);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.getId());
        result.put("status", exam.getStatus().name());
        return result;
    }

    @Transactional
    public Object releaseScores(CurrentUser currentUser, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限发布该考试成绩");
        }
        exam.setScoreReleased(true);
        examRepository.save(exam);
        // 幂等补写：对已结案（GRADED）但历史缺失成绩记录的提交补建 score_record。
        // 逐条对提交行加悲观锁，与教师批改 / 学生交卷串行，避免与批改并发读到中间态。
        OffsetDateTime now = OffsetDateTime.now();
        for (ExamSubmission candidate : examSubmissionRepository.findByExamId(examId)) {
            if (candidate.getStatus() != ExamSubmissionStatus.GRADED) {
                continue;
            }
            ExamSubmission locked = examSubmissionRepository
                    .findByExamIdAndStudentIdForUpdate(examId, candidate.getStudentId())
                    .orElse(null);
            if (locked == null || locked.getStatus() != ExamSubmissionStatus.GRADED) {
                continue;
            }
            Double lockedScore = locked.getTotalScore();
            scoreRecordWriter.write(BusinessType.EXAM, exam.getId(), locked.getStudentId(),
                    exam.getClassId(), lockedScore == null ? 0D : lockedScore, now);
        }
        return Map.of("released", true, "scoreVisibilityMode", exam.getScoreVisibilityMode().name());
    }

    public Object startExam(CurrentUser currentUser, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));

        if (exam.getStatus() != ActivityStatus.PUBLISHED) {
            throw new BusinessException(40000, "考试未发布，不能开始");
        }

        boolean isMember = classMemberRepository
                .findByClassIdAndStudentUserId(exam.getClassId(), currentUser.id())
                .isPresent();
        if (!isMember) {
            throw new BusinessException(40300, "无权限参加该考试");
        }

        boolean hasLegacyInlineQuestion = examQuestionRepository
                .findByExamIdOrderBySortOrder(examId).stream()
                .anyMatch(item -> item.getQuestionId() == null);
        if (hasLegacyInlineQuestion) {
            throw new BusinessException(40000, "试卷包含不支持的题目，请联系教师");
        }

        Optional<ExamSubmission> existingOpt = examSubmissionRepository.findByExamIdAndStudentId(examId, currentUser.id());
        if (existingOpt.isPresent()) {
            ExamSubmission existing = existingOpt.get();
            if (existing.getStatus() == ExamSubmissionStatus.IN_PROGRESS) {
                // Return existing in-progress submission without time-window check,
                // so refreshing at the deadline can still trigger auto submission.
                return buildStartResult(existing, OffsetDateTime.now());
            } else {
                // SUBMITTED, AUTO_SUBMITTED, GRADED
                throw new BusinessException(40000, "考试已提交，不能重新开始");
            }
        }

        // Validate time window only for a brand-new submission
        OffsetDateTime now = OffsetDateTime.now();
        if (exam.getStartAt() != null && now.isBefore(exam.getStartAt())) {
            throw new BusinessException(40000, "考试尚未开始");
        }
        if (exam.getEndAt() != null && !now.isBefore(exam.getEndAt())) {
            throw new BusinessException(40000, "考试已结束");
        }

        // Create new submission, deadline capped by exam end time
        OffsetDateTime deadline = now.plusMinutes(exam.getDurationMinutes());
        if (exam.getEndAt() != null && deadline.isAfter(exam.getEndAt())) {
            deadline = exam.getEndAt();
        }
        final OffsetDateTime deadlineAt = deadline;

        // 创建提交走独立事务：唯一键冲突必须在外层事务之外捕获，否则事务已被标记回滚。
        try {
            ExamSubmission created = Objects.requireNonNull(transactionTemplate.execute(status -> {
                ExamSubmission submission = new ExamSubmission();
                submission.setExamId(examId);
                submission.setStudentId(currentUser.id());
                submission.setStartedAt(now);
                submission.setDeadlineAt(deadlineAt);
                submission.setStatus(ExamSubmissionStatus.IN_PROGRESS);
                return examSubmissionRepository.saveAndFlush(submission);
            }));
            return buildStartResult(created, now);
        } catch (DataIntegrityViolationException ex) {
            ExamSubmission existing = examSubmissionRepository
                    .findByExamIdAndStudentId(examId, currentUser.id())
                    .orElseThrow(() -> new BusinessException(40900, "考试提交创建冲突，请重试"));
            if (existing.getStatus() == ExamSubmissionStatus.IN_PROGRESS) {
                return buildStartResult(existing, now);
            }
            throw new BusinessException(40000, "考试已提交，不能重新开始");
        }
    }

    /**
     * 构造开始考试响应体：统一计算剩余秒数，剩余时间不允许为负。
     */
    private Map<String, Object> buildStartResult(ExamSubmission submission, OffsetDateTime now) {
        long remainingSeconds = 0;
        if (submission.getDeadlineAt() != null) {
            remainingSeconds = Math.max(0, Duration.between(now, submission.getDeadlineAt()).getSeconds());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("deadlineAt", submission.getDeadlineAt());
        result.put("remainingSeconds", remainingSeconds);
        return result;
    }

    @Transactional
    public Object submitExam(CurrentUser currentUser, Long examId, ExamRequests.SubmitExamRequest request) {
        // 1. 对“单学生 + 单考试”提交行加悲观写锁：并发双击时后到事务阻塞，
        //    待前一事务提交后读到非 IN_PROGRESS 状态并被拒绝，避免重复答案。
        ExamSubmission submission = examSubmissionRepository.findByExamIdAndStudentIdForUpdate(examId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40400, "未找到考试提交记录"));

        // 2. Validate submission status is IN_PROGRESS
        if (submission.getStatus() != ExamSubmissionStatus.IN_PROGRESS) {
            throw new BusinessException(40000, "考试已提交，不能重复提交");
        }

        // 2.1 Load exam once for status override and score visibility checks
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));

        // 2.2 Reject submissions beyond the grace period before any write happens.
        // Exam status is intentionally not checked: CLOSED must not override the deadline rule.
        OffsetDateTime now = OffsetDateTime.now();
        if (submission.getDeadlineAt() != null && now.isAfter(submission.getDeadlineAt().plusSeconds(SUBMIT_GRACE_SECONDS))) {
            throw new BusinessException(40000, "考试已结束，无法提交");
        }

        // 3. 仅构建 payload 条目；是否回退草稿在结算方法内逐题判定
        Map<Long, ExamRequests.SubmitAnswerItem> submitItems = new HashMap<>();
        if (request.answers() != null) {
            for (ExamRequests.SubmitAnswerItem item : request.answers()) {
                submitItems.put(item.questionId(), item);
            }
        }
        return finalizeExamSubmission(submission, exam, submitItems);
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
        OffsetDateTime now = OffsetDateTime.now();
        // 1. 状态判定：deadline 之后 → AUTO_SUBMITTED，否则 SUBMITTED
        if (submission.getDeadlineAt() != null && now.isAfter(submission.getDeadlineAt())) {
            submission.setStatus(ExamSubmissionStatus.AUTO_SUBMITTED);
        } else {
            submission.setStatus(ExamSubmissionStatus.SUBMITTED);
        }

        // 2. 删除前先快照已持久化草稿：payload 缺失的题目回退草稿，避免刷新后自动交卷提交空白卷。
        Map<Long, String> draftAnswers = new HashMap<>();
        for (ExamAnswer existing : examAnswerRepository.findByExamSubmissionId(submission.getId())) {
            draftAnswers.put(existing.getQuestionId(), existing.getAnswerJson());
        }

        // 3. Delete any stale exam_answer rows for this submission
        examAnswerRepository.deleteByExamSubmissionId(submission.getId());

        // 4. For each exam_question: score and persist ExamAnswer
        //    按 questionId 去重：存量试卷可能含重复题目，只让 sortOrder 最小的首行生效，
        //    否则会触发 uk_exam_answer_submission_question。
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySortOrder(exam.getId());
        Set<Long> processedQuestionIds = new HashSet<>();
        double autoScore = 0.0;
        boolean hasManualGrading = false;

        for (ExamQuestion eq : examQuestions) {
            if (!processedQuestionIds.add(eq.getQuestionId())) {
                continue;
            }
            QuestionSnapshot snapshot = resolveQuestionSnapshot(eq);
            String questionType = snapshot.questionType();
            boolean isObjective = examScoringService.isObjective(questionType);
            // payload 出现该题（即使字段全空）以 payload 为准；缺失时才回退已持久化草稿。
            ExamRequests.SubmitAnswerItem effective = submitItems.containsKey(eq.getQuestionId())
                    ? submitItems.get(eq.getQuestionId())
                    : resolveDraftAnswerItem(eq.getQuestionId(), questionType, draftAnswers.get(eq.getQuestionId()));
            GradingResultView gradingResult = gradingAdapter.grade(
                    snapshot,
                    AnswerPayloadNormalizer.normalize(
                            eq.getQuestionId(),
                            questionType,
                            effective.answerText(),
                            effective.answerJson(),
                            effective.selectedOptions(),
                            effective.attachmentPath()
                    )
            );

            ExamAnswer answer = new ExamAnswer();
            answer.setExamSubmissionId(submission.getId());
            answer.setQuestionId(eq.getQuestionId());
            answer.setAnswerJson(gradingResult.normalizedAnswerJson());
            answer.setAutoScore(gradingResult.autoScore());
            answer.setSuggestedScore(gradingResult.suggestedScore());
            answer.setScoreSource(gradingResult.scoreSource());
            answer.setJudgeDetail(gradingResult.judgeDetail());
            answer.setAcceptedAutoScore("AUTO".equals(gradingResult.scoreSource())
                    || "AUTO_ACCEPTED".equals(gradingResult.scoreSource()));

            if (isObjective) {
                answer.setScore(gradingResult.finalScore());
                answer.setIsCorrect(gradingResult.correct());
                autoScore += gradingResult.finalScore() != null ? gradingResult.finalScore() : 0D;
            } else {
                answer.setScore(null);
                answer.setIsCorrect(null); // null for subjective - teacher grades later
                hasManualGrading = true;
            }

            examAnswerRepository.save(answer);
        }

        // 5. Set scores and timestamps; objective-only submissions are finalized on submit
        submission.setAutoScore(autoScore);
        submission.setManualScore(0.0);
        submission.setTotalScore(autoScore);
        submission.setSubmittedAt(now);
        if (!hasManualGrading) {
            submission.setStatus(ExamSubmissionStatus.GRADED);
        }
        examSubmissionRepository.save(submission);

        // 6. 成绩记录在成绩结案（GRADED）时写入，不再依赖可见性；可见性只在读取端收敛。
        if (submission.getStatus() == ExamSubmissionStatus.GRADED) {
            Double finalScore = submission.getTotalScore();
            upsertScoreRecord(exam, submission, finalScore == null ? 0D : finalScore);
        }

        // 7. Return result：成绩不可见时不下发任何分数，前端以 resultAvailable 判定。
        boolean scoreVisible = canStudentSeeScore(exam, submission);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus().name());
        result.put("resultAvailable", scoreVisible);
        result.put("autoScore", scoreVisible ? autoScore : null);
        result.put("totalScore", scoreVisible ? autoScore : null);
        result.put("gradingInProgress",
                submission.getStatus() == ExamSubmissionStatus.SUBMITTED
                        || submission.getStatus() == ExamSubmissionStatus.AUTO_SUBMITTED);
        result.put("hasManualGrading", hasManualGrading);
        return result;
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
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        boolean isMember = classMemberRepository
                .findByClassIdAndStudentUserId(exam.getClassId(), currentUser.id())
                .isPresent();
        if (!isMember) {
            throw new BusinessException(40300, "无权限保存草稿");
        }
        ExamSubmission submission = examSubmissionRepository
                .findByExamIdAndStudentIdForUpdate(examId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40400, "未找到考试提交记录"));
        if (submission.getStatus() != ExamSubmissionStatus.IN_PROGRESS) {
            throw new BusinessException(40000, "考试已提交，无法保存草稿");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (submission.getDeadlineAt() != null && now.isAfter(submission.getDeadlineAt().plusSeconds(SUBMIT_GRACE_SECONDS))) {
            throw new BusinessException(40000, "考试已结束，无法保存草稿");
        }

        Set<Long> examQuestionIds = examQuestionRepository.findByExamIdOrderBySortOrder(examId).stream()
                .map(ExamQuestion::getQuestionId)
                .collect(Collectors.toSet());

        Map<Long, ExamRequests.SubmitAnswerItem> deduped = new LinkedHashMap<>();
        if (request.answers() != null) {
            for (ExamRequests.SubmitAnswerItem item : request.answers()) {
                if (!examQuestionIds.contains(item.questionId())) {
                    throw new BusinessException(40000, "题目不属于该考试");
                }
                deduped.put(item.questionId(), item);
            }
        }

        for (Map.Entry<Long, ExamRequests.SubmitAnswerItem> entry : deduped.entrySet()) {
            ExamAnswer answer = examAnswerRepository
                    .findByExamSubmissionIdAndQuestionId(submission.getId(), entry.getKey())
                    .orElseGet(ExamAnswer::new);
            answer.setExamSubmissionId(submission.getId());
            answer.setQuestionId(entry.getKey());
            answer.setAnswerJson(resolveAnswerJson(entry.getValue()));
            examAnswerRepository.save(answer);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("savedAt", now);
        result.put("savedCount", deduped.size());
        return result;
    }

    private QuestionSnapshot resolveQuestionSnapshot(ExamQuestion examQuestion) {
        if (examQuestion.getQuestionSnapshotJson() != null && !examQuestion.getQuestionSnapshotJson().isBlank()) {
            JsonNode snapshot = readJsonNode(examQuestion.getQuestionSnapshotJson());
            String questionType = jsonText(snapshot, "questionType");
            String stem = jsonText(snapshot, "stem");
            if (questionType != null && stem != null) {
                return new QuestionSnapshot(
                        jsonLong(snapshot, "questionBankId", examQuestion.getQuestionId()),
                        jsonText(snapshot, "sourceType", "BANK"),
                        questionType,
                        stem,
                        jsonInteger(snapshot, "sortOrder", examQuestion.getSortOrder()),
                        jsonDouble(snapshot, "score", examQuestion.getQuestionScore()),
                        jsonField(snapshot, "optionsJson"),
                        jsonField(snapshot, "answerJson"),
                        jsonField(snapshot, "scoringConfigJson"),
                        jsonBoolean(snapshot, "reusableFromBank", Boolean.TRUE)
                );
            }
        }

        QuestionBank questionBank = questionBankRepository.findById(examQuestion.getQuestionId())
                .orElseThrow(() -> new BusinessException(40400, "题库题目不存在"));
        return toSnapshot(questionBank, examQuestion.getSortOrder(), examQuestion.getQuestionScore());
    }

    private Map<String, Object> readJsonMap(String json) {
        JsonNode node = readJsonNode(json);
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private JsonNode readJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode parsed = parseJsonNode(json);
        if (parsed != null) {
            return parsed;
        }
        String normalized = normalizeJsonLiteral(json);
        if (!normalized.equals(json)) {
            return parseJsonNode(normalized);
        }
        return null;
    }

    private JsonNode parseJsonNode(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            while (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.textValue());
            }
            return node;
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeJsonLiteral(String json) {
        String normalized = json.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.contains("\\\"")) {
            normalized = normalized.replace("\\\"", "\"");
        }
        if (normalized.contains("\"\"")) {
            normalized = normalized.replace("\"\"", "\"");
        }
        return normalized;
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Object getExamResults(CurrentUser currentUser, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限查看该考试结果");
        }

        List<ExamSubmission> submissions = examSubmissionRepository.findByExamId(examId);
        List<Map<String, Object>> resultList = new ArrayList<>();
        var classRoom = classRoomRepository.findById(exam.getClassId()).orElse(null);
        for (ExamSubmission sub : submissions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("submissionId", sub.getId());
            map.put("examId", exam.getId());
            map.put("examTitle", exam.getTitle());
            map.put("classId", exam.getClassId());
            map.put("className", classRoom == null ? "" : classRoom.getName());
            map.put("studentId", sub.getStudentId());

            sysUserRepository.findById(sub.getStudentId()).ifPresent(user -> {
                map.put("studentName", user.getDisplayName());
                map.put("studentUsername", user.getUsername());
                map.put("studentNo", user.getUsername());
            });

            map.put("status", sub.getStatus().name());
            map.put("autoScore", sub.getAutoScore());
            map.put("manualScore", sub.getManualScore());
            map.put("totalScore", sub.getTotalScore());
            map.put("startedAt", sub.getStartedAt());
            map.put("submittedAt", sub.getSubmittedAt());
            resultList.add(map);
        }
        return resultList;
    }

    public Object getSubmissionDetail(CurrentUser currentUser, Long submissionId) {
        ExamSubmission submission = examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        Exam exam = examRepository.findById(submission.getExamId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限查看该提交详情");
        }

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySortOrder(submission.getExamId());
        List<ExamAnswer> answers = examAnswerRepository.findByExamSubmissionId(submissionId);
        Map<Long, ExamAnswer> answerMap = new HashMap<>();
        for (ExamAnswer a : answers) {
            answerMap.put(a.getQuestionId(), a);
        }

        List<Map<String, Object>> answerList = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            Map<String, Object> aMap = new LinkedHashMap<>();
            aMap.put("questionId", eq.getQuestionId());
            aMap.put("questionScore", eq.getQuestionScore());

            QuestionSnapshot snapshot = resolveQuestionSnapshot(eq);
            aMap.put("questionType", snapshot.questionType());
            aMap.put("stem", snapshot.stem());
            aMap.put("optionsJson", snapshot.optionsJson());
            aMap.put("standardAnswer", snapshot.answerJson());

            ExamAnswer ea = answerMap.get(eq.getQuestionId());
            if (ea != null) {
                aMap.put("studentAnswer", ea.getAnswerJson());
                aMap.put("isCorrect", ea.getIsCorrect());
                aMap.put("score", ea.getScore());
                aMap.put("autoScore", ea.getAutoScore());
                aMap.put("suggestedScore", ea.getSuggestedScore());
                aMap.put("scoreSource", ea.getScoreSource());
                aMap.put("judgeDetail", ea.getJudgeDetail());
                aMap.put("acceptedAutoScore", ea.isAcceptedAutoScore());
                aMap.put("teacherComment", ea.getTeacherComment());
            }

            answerList.add(aMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("examId", submission.getExamId());
        result.put("examTitle", exam.getTitle());
        result.put("classId", exam.getClassId());
        result.put("className", classRoomRepository.findById(exam.getClassId()).map(cr -> cr.getName()).orElse(""));
        result.put("studentId", submission.getStudentId());
        sysUserRepository.findById(submission.getStudentId()).ifPresent(user -> {
            result.put("studentName", user.getDisplayName());
            result.put("studentUsername", user.getUsername());
            result.put("studentNo", user.getUsername());
        });
        result.put("status", submission.getStatus().name());
        result.put("autoScore", submission.getAutoScore());
        result.put("manualScore", submission.getManualScore());
        result.put("totalScore", submission.getTotalScore());
        result.put("startedAt", submission.getStartedAt());
        result.put("submittedAt", submission.getSubmittedAt());
        result.put("answers", answerList);
        return result;
    }

    @Transactional
    public Object gradeSubmission(CurrentUser currentUser, Long submissionId, ExamRequests.GradeExamSubmissionRequest request) {
        ExamSubmission head = examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        Exam exam = examRepository.findById(head.getExamId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限批改该考试");
        }
        // 先取业务键 (examId, studentId)，再在锁内重读并重跑状态校验，保证成绩写入串行化。
        ExamSubmission submission = examSubmissionRepository
                .findByExamIdAndStudentIdForUpdate(exam.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        if (submission.getStatus() != ExamSubmissionStatus.SUBMITTED
                && submission.getStatus() != ExamSubmissionStatus.AUTO_SUBMITTED) {
            throw new BusinessException(40000, "当前状态不允许批改");
        }

        // Update subjective question scores; empty/null answers only trigger a recompute below
        List<ExamRequests.GradeExamAnswerItem> items = request.answers() == null
                ? List.of()
                : request.answers();
        for (ExamRequests.GradeExamAnswerItem item : items) {
            ExamAnswer answer = examAnswerRepository.findByExamSubmissionIdAndQuestionId(submissionId, item.questionId())
                    .orElseThrow(() -> new BusinessException(40400, "答题记录不存在: questionId=" + item.questionId()));

            // Only allow grading subjective questions
            QuestionSnapshot snapshot = examQuestionRepository.findByExamIdOrderBySortOrder(submission.getExamId()).stream()
                    .filter(eq -> Objects.equals(eq.getQuestionId(), item.questionId()))
                    .findFirst()
                    .map(this::resolveQuestionSnapshot)
                    .orElse(null);
            if (snapshot != null && examScoringService.isObjective(snapshot.questionType())) {
                continue; // Don't let teacher override objective auto-grades
            }

            answer.setScore(item.score());
            answer.setTeacherComment(item.teacherComment());
            examAnswerRepository.save(answer);
        }

        // Recompute scores
        List<ExamAnswer> allAnswers = examAnswerRepository.findByExamSubmissionId(submissionId);
        double manualScore = 0.0;
        for (ExamAnswer a : allAnswers) {
            QuestionSnapshot snapshot = examQuestionRepository.findByExamIdOrderBySortOrder(submission.getExamId()).stream()
                    .filter(eq -> Objects.equals(eq.getQuestionId(), a.getQuestionId()))
                    .findFirst()
                    .map(this::resolveQuestionSnapshot)
                    .orElse(null);
            if (snapshot != null && !examScoringService.isObjective(snapshot.questionType())) {
                manualScore += (a.getScore() != null ? a.getScore() : 0.0);
            }
        }

        double autoScore = submission.getAutoScore() != null ? submission.getAutoScore() : 0.0;
        double totalScore = autoScore + manualScore;

        submission.setManualScore(manualScore);
        submission.setTotalScore(totalScore);
        return recomputeSubmissionAfterTeacherConfirmation(exam, submission);
    }

    @Transactional
    public Object confirmAnswer(CurrentUser currentUser, Long submissionId, ExamRequests.ConfirmExamAnswerRequest request) {
        ExamSubmission head = examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        Exam exam = examRepository.findById(head.getExamId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限批改该考试");
        }
        // 先取业务键 (examId, studentId)，再在锁内重读并重跑状态校验。
        ExamSubmission submission = examSubmissionRepository
                .findByExamIdAndStudentIdForUpdate(exam.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        if (submission.getStatus() != ExamSubmissionStatus.SUBMITTED
                && submission.getStatus() != ExamSubmissionStatus.AUTO_SUBMITTED
                && submission.getStatus() != ExamSubmissionStatus.GRADED) {
            throw new BusinessException(40000, "当前状态不允许批改");
        }
        // 成绩已发布或已到期后不允许直接改分，避免教师单题改分绕过发布状态
        assertScoreEditable(exam);
        ExamAnswer answer = examAnswerRepository.findByExamSubmissionIdAndQuestionId(submissionId, request.questionId())
                .orElseThrow(() -> new BusinessException(40400, "答题记录不存在: questionId=" + request.questionId()));
        // 客观题由系统自动评分，禁止教师改分；快照缺失（历史题或题项已删除）按主观题处理
        QuestionSnapshot snapshot = examQuestionRepository.findByExamIdOrderBySortOrder(exam.getId()).stream()
                .filter(item -> Objects.equals(item.getQuestionId(), request.questionId()))
                .findFirst()
                .map(this::resolveQuestionSnapshot)
                .orElse(null);
        if (snapshot != null && examScoringService.isObjective(snapshot.questionType())) {
            if (request.acceptSuggested()) {
                throw new BusinessException(40000, "客观题不接受推荐分");
            }
            throw new BusinessException(40000, "客观题由系统自动评分，不支持手工改分");
        }
        if (request.acceptSuggested()) {
            if (answer.getSuggestedScore() == null) {
                throw new BusinessException(40000, "当前题目没有可采纳的推荐分");
            }
            answer.setScore(answer.getSuggestedScore());
            answer.setScoreSource("AUTO_ACCEPTED");
            answer.setAcceptedAutoScore(true);
        } else {
            if (request.score() == null) {
                throw new BusinessException(40000, "题目分数不能为空");
            }
            answer.setScore(request.score());
            answer.setScoreSource("TEACHER");
            answer.setAcceptedAutoScore(false);
        }
        answer.setTeacherComment(request.teacherComment());
        examAnswerRepository.save(answer);
        return recomputeSubmissionAfterTeacherConfirmation(exam, submission);
    }

    private boolean isFinal(ExamSubmission submission) {
        return submission != null && submission.getStatus() == ExamSubmissionStatus.GRADED;
    }

    private boolean canStudentSeeScore(Exam exam, ExamSubmission submission) {
        // 只有最终成绩（GRADED）才可能对学生公开；未批完一律不可见。
        if (!isFinal(submission)) {
            return false;
        }
        ScoreVisibilityMode mode = exam.getScoreVisibilityMode() == null
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : exam.getScoreVisibilityMode();
        return switch (mode) {
            case IMMEDIATE, AFTER_TEACHER_CONFIRM -> true;
            case AFTER_DEADLINE -> exam.getEndAt() != null
                    && !OffsetDateTime.now().isBefore(exam.getEndAt());
            case MANUAL_RELEASE -> exam.isScoreReleased();
        };
    }

    private void upsertScoreRecord(Exam exam, ExamSubmission submission, double totalScore) {
        scoreRecordWriter.write(BusinessType.EXAM, exam.getId(), submission.getStudentId(),
                exam.getClassId(), totalScore, OffsetDateTime.now());
    }

    private Map<String, Object> recomputeSubmissionAfterTeacherConfirmation(Exam exam, ExamSubmission submission) {
        ExamSubmissionStatus previousStatus = submission.getStatus();
        List<ExamAnswer> allAnswers = examAnswerRepository.findByExamSubmissionId(submission.getId());
        double autoScore = 0.0;
        double manualScore = 0.0;
        boolean allConfirmed = true;
        for (ExamAnswer answer : allAnswers) {
            boolean objective = examQuestionRepository.findByExamIdOrderBySortOrder(submission.getExamId()).stream()
                    .filter(eq -> Objects.equals(eq.getQuestionId(), answer.getQuestionId()))
                    .findFirst()
                    .map(this::resolveQuestionSnapshot)
                    .map(QuestionSnapshot::questionType)
                    .map(examScoringService::isObjective)
                    .orElse(false);
            if (objective) {
                autoScore += answer.getScore() != null ? answer.getScore() : 0.0;
            } else if (answer.getScore() != null) {
                manualScore += answer.getScore();
            } else {
                allConfirmed = false;
            }
        }
        double totalScore = autoScore + manualScore;
        submission.setAutoScore(autoScore);
        submission.setManualScore(manualScore);
        submission.setTotalScore(totalScore);
        submission.setStatus(allConfirmed ? ExamSubmissionStatus.GRADED : ExamSubmissionStatus.SUBMITTED);
        examSubmissionRepository.save(submission);
        if (allConfirmed) {
            upsertScoreRecord(exam, submission, totalScore);
        } else if (previousStatus == ExamSubmissionStatus.GRADED) {
            // 从 GRADED 降回 SUBMITTED 时删除残留成绩记录，避免成绩列表继续展示已撤销的结果
            scoreRecordWriter.remove(BusinessType.EXAM, exam.getId(), submission.getStudentId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus().name());
        result.put("autoScore", autoScore);
        result.put("manualScore", manualScore);
        result.put("totalScore", totalScore);
        return result;
    }

    private QuestionSnapshot toSnapshot(QuestionBank questionBank, Integer sortOrder, Double score) {
        return QuestionSnapshotFactory.fromBankQuestion(
                questionBank.getId(),
                questionBank.getType(),
                questionBank.getStem(),
                questionBank.getOptionsJson(),
                questionBank.getAnswerJson(),
                questionBank.getScoringConfigJson(),
                sortOrder,
                score
        );
    }

    private ExamQuestion buildExamQuestion(Long examId, ExamRequests.ExamQuestionItem item, QuestionBank questionBank) {
        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setExamId(examId);
        examQuestion.setQuestionId(item.questionId());
        examQuestion.setSortOrder(item.sortOrder());
        examQuestion.setQuestionScore(item.questionScore());
        examQuestion.setQuestionSnapshotJson(writeJson(toSnapshot(questionBank, item.sortOrder(), item.questionScore())));
        return examQuestion;
    }

    /**
     * 逐项加载当前教师拥有的题库题目；缺失或非本人统一返回 404。
     * 必须在写主表 / 批量删除题目之前调用，保证失败路径不产生任何写操作。
     */
    private Map<Long, QuestionBank> loadOwnedBankQuestions(List<ExamRequests.ExamQuestionItem> items, Long teacherId) {
        Map<Long, QuestionBank> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (ExamRequests.ExamQuestionItem item : items) {
            QuestionBank bank = questionBankRepository.findByIdAndCreatedBy(item.questionId(), teacherId)
                    .orElseThrow(() -> new BusinessException(40400, "题库题目不存在"));
            result.put(item.questionId(), bank);
        }
        return result;
    }

    /**
     * 组卷题目必须全部来自题库；内联题已下线，历史数据仅允许读取。
     */
    private void assertBankQuestions(List<ExamRequests.ExamQuestionItem> items) {
        if (items == null) {
            return;
        }
        for (ExamRequests.ExamQuestionItem item : items) {
            if (item == null || item.questionId() == null || !"BANK".equalsIgnoreCase(item.resolvedSourceType())) {
                throw new BusinessException(40000, "考试题目必须来自题库");
            }
        }
    }

    /**
     * 组卷题目不能重复。
     *
     * <p>{@code exam_answer(exam_submission_id, question_id)} 已有唯一约束，
     * 若试卷本身含重复题目，交卷会同时插入多行同 questionId 答案而触发约束。
     * 写入侧直接拒绝，存量数据由交卷链路按 questionId 去重兼容。</p>
     */
    private void assertNoDuplicateQuestions(List<ExamRequests.ExamQuestionItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (ExamRequests.ExamQuestionItem item : items) {
            if (item == null || item.questionId() == null) {
                continue;
            }
            if (!seen.add(item.questionId())) {
                throw new BusinessException(40000, "试卷题目不能重复");
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(50000, "JSON 序列化失败");
        }
    }

    private String resolveAnswerJson(ExamRequests.SubmitAnswerItem item) {
        if (item.answerJson() != null && !item.answerJson().isBlank()) {
            return item.answerJson();
        }
        if (item.selectedOptions() != null && !item.selectedOptions().isEmpty()) {
            return writeJson(item.selectedOptions());
        }
        return null;
    }

    /**
     * 把已持久化草稿（answer_json）解码为交卷用的答题条目。
     *
     * <p>草稿写入端保存的是 {@code buildAnswerPayloadJson} 的 {@code {kind:...}} 结构或
     * 旧版选项数组，评分侧依赖 {@code answerText} 与 {@code selectedOptions}，
     * 因此这里同时还原这两个字段，{@code answerJson} 原样回填草稿 JSON。</p>
     *
     * @param questionId   题目 id
     * @param questionType 快照题型（当前解码只依赖 kind，不按题型猜测）
     * @param draftJson    草稿 answer_json，可为 null
     * @return 交卷条目；草稿为空时返回全空条目，按未作答评分
     */
    private ExamRequests.SubmitAnswerItem resolveDraftAnswerItem(Long questionId, String questionType, String draftJson) {
        if (draftJson == null || draftJson.isBlank()) {
            return new ExamRequests.SubmitAnswerItem(questionId, List.of(), null, null, null);
        }
        JsonNode node = readJsonNode(draftJson);
        if (node == null) {
            return new ExamRequests.SubmitAnswerItem(questionId, List.of(), draftJson, draftJson, null);
        }
        if (node.isArray()) {
            List<String> options = new ArrayList<>();
            node.forEach(item -> options.add(item.asText()));
            return new ExamRequests.SubmitAnswerItem(questionId, options, String.join(",", options),
                    draftJson, null);
        }
        String kind = jsonText(node, "kind");
        if (kind == null) {
            return new ExamRequests.SubmitAnswerItem(questionId, List.of(), jsonText(node, "text"), draftJson, null);
        }
        return switch (kind) {
            case "single" -> {
                String key = jsonText(node, "selectedKey");
                yield new ExamRequests.SubmitAnswerItem(questionId,
                        key == null ? List.of() : List.of(key), key, draftJson, null);
            }
            case "multiple" -> {
                List<String> options = readStringArray(node, "selectedKeys");
                yield new ExamRequests.SubmitAnswerItem(questionId, options,
                        String.join(",", options), draftJson, null);
            }
            case "judge" -> {
                String value = jsonText(node, "value");
                yield new ExamRequests.SubmitAnswerItem(questionId,
                        value == null ? List.of() : List.of(value), value, draftJson, null);
            }
            case "fill" -> {
                String text = joinBlankAnswers(node);
                yield new ExamRequests.SubmitAnswerItem(questionId, List.of(), text, draftJson, null);
            }
            case "code" -> {
                String code = jsonText(node, "code");
                yield new ExamRequests.SubmitAnswerItem(questionId, List.of(), code, draftJson, null);
            }
            default -> {
                String text = jsonText(node, "text");
                yield new ExamRequests.SubmitAnswerItem(questionId, List.of(), text, draftJson, null);
            }
        };
    }

    /**
     * 读取 JSON 对象中的字符串数组字段（多选草稿专用），非数组返回空列表并过滤空串。
     */
    private List<String> readStringArray(JsonNode node, String field) {
        if (node == null) {
            return List.of();
        }
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : array) {
            String text = element.asText();
            if (text != null && !text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    /**
     * 拼接填空题草稿的作答文本（与前端 {@code buildCompatibilityAnswerText} 的空位语义一致）：
     * 按 {@code index} 升序、跳过 {@code index <= 0}、逐项 trim 后保留空串用 {@code ,} 拼接。
     *
     * @return 无 {@code blanks} 数组时返回 null
     */
    private String joinBlankAnswers(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode blanks = node.get("blanks");
        if (blanks == null || !blanks.isArray()) {
            return null;
        }
        List<Map.Entry<Integer, String>> entries = new ArrayList<>();
        for (JsonNode blank : blanks) {
            int index = blank.path("index").asInt(0);
            if (index <= 0) {
                continue;
            }
            JsonNode answerNode = blank.get("answer");
            String answer = answerNode == null || answerNode.isNull() ? "" : answerNode.asText();
            entries.add(Map.entry(index, answer.trim()));
        }
        entries.sort(Map.Entry.comparingByKey());
        return entries.stream().map(Map.Entry::getValue).collect(Collectors.joining(","));
    }

    private String mapString(Object value) {
        return mapString(value, null);
    }

    private String mapString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private Long mapLong(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Integer mapInteger(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Double mapDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Boolean mapBoolean(Object value, Boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private String mapJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return writeJson(value);
    }

    private String jsonText(JsonNode node, String fieldName) {
        return jsonText(node, fieldName, null);
    }

    private String jsonText(JsonNode node, String fieldName, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        String text = field.isTextual() ? field.textValue() : field.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private Long jsonLong(JsonNode node, String fieldName, Long fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isNumber()) {
            return field.longValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Integer jsonInteger(JsonNode node, String fieldName, Integer fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isNumber()) {
            return field.intValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Double jsonDouble(JsonNode node, String fieldName, Double fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isNumber()) {
            return field.doubleValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Boolean jsonBoolean(JsonNode node, String fieldName, Boolean fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isBoolean()) {
            return field.booleanValue();
        }
        String text = field.asText();
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(text);
    }

    private String jsonField(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (field.isTextual()) {
            return field.textValue();
        }
        return field.toString();
    }
}
