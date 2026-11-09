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
public class ExamAnswerService {

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

    private final ExamServiceSupport support;

    public ExamAnswerService(ExamRepository examRepository,
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
                       ObjectProvider<ObjectMapper> objectMapperProvider,
                      ExamServiceSupport support) {
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
        this.support = support;
    }


    public Object startExam(CurrentUser currentUser, Long examId) {
        Exam exam = examRepository.findByIdAndSchoolId(examId, currentUser.schoolId())
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
        Exam exam = examRepository.findByIdAndSchoolId(examId, currentUser.schoolId())
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
            QuestionSnapshot snapshot = support.resolveQuestionSnapshot(eq, exam.getSchoolId());
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
            support.upsertScoreRecord(exam, submission, finalScore == null ? 0D : finalScore);
        }

        // 7. Return result：成绩不可见时不下发任何分数，前端以 resultAvailable 判定。
        boolean scoreVisible = support.canStudentSeeScore(exam, submission);
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
        Exam exam = examRepository.findByIdAndSchoolId(examId, currentUser.schoolId())
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



    private String resolveAnswerJson(ExamRequests.SubmitAnswerItem item) {
        if (item.answerJson() != null && !item.answerJson().isBlank()) {
            return item.answerJson();
        }
        if (item.selectedOptions() != null && !item.selectedOptions().isEmpty()) {
            return support.writeJson(item.selectedOptions());
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
        JsonNode node = support.readJsonNode(draftJson);
        if (node == null) {
            return new ExamRequests.SubmitAnswerItem(questionId, List.of(), draftJson, draftJson, null);
        }
        if (node.isArray()) {
            List<String> options = new ArrayList<>();
            node.forEach(item -> options.add(item.asText()));
            return new ExamRequests.SubmitAnswerItem(questionId, options, String.join(",", options),
                    draftJson, null);
        }
        String kind = support.jsonText(node, "kind");
        if (kind == null) {
            return new ExamRequests.SubmitAnswerItem(questionId, List.of(), support.jsonText(node, "text"), draftJson, null);
        }
        return switch (kind) {
            case "single" -> {
                String key = support.jsonText(node, "selectedKey");
                yield new ExamRequests.SubmitAnswerItem(questionId,
                        key == null ? List.of() : List.of(key), key, draftJson, null);
            }
            case "multiple" -> {
                List<String> options = readStringArray(node, "selectedKeys");
                yield new ExamRequests.SubmitAnswerItem(questionId, options,
                        String.join(",", options), draftJson, null);
            }
            case "judge" -> {
                String value = support.jsonText(node, "value");
                yield new ExamRequests.SubmitAnswerItem(questionId,
                        value == null ? List.of() : List.of(value), value, draftJson, null);
            }
            case "fill" -> {
                String text = joinBlankAnswers(node);
                yield new ExamRequests.SubmitAnswerItem(questionId, List.of(), text, draftJson, null);
            }
            case "code" -> {
                String code = support.jsonText(node, "code");
                yield new ExamRequests.SubmitAnswerItem(questionId, List.of(), code, draftJson, null);
            }
            default -> {
                String text = support.jsonText(node, "text");
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

}
