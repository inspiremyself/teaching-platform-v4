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
import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.BusinessType;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ExamService {

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
    private final ScoreRecordRepository scoreRecordRepository;
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
                       ScoreRecordRepository scoreRecordRepository,
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
        this.scoreRecordRepository = scoreRecordRepository;
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
                    boolean canStart = submissionOpt.isEmpty();
                    boolean canResume = submissionOpt.isPresent() && submissionOpt.get().getStatus() == ExamSubmissionStatus.IN_PROGRESS;
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
        try {
            exam.setStatus(ActivityStatus.valueOf(request.status()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(40000, "无效的考试状态: " + request.status());
        }
        exam.setScoreVisibilityMode(request.scoreVisibilityMode() == null ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM : request.scoreVisibilityMode());
        exam.setCreatedBy(currentUser.id());
        examRepository.save(exam);

        if (request.questions() != null) {
            for (ExamRequests.ExamQuestionItem item : request.questions()) {
                examQuestionRepository.save(buildExamQuestion(exam.getId(), item));
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

        exam.setTitle(request.title());
        exam.setDescription(request.description());
        exam.setClassId(request.classId());
        exam.setStartAt(request.startAt());
        exam.setEndAt(request.endAt());
        exam.setDurationMinutes(request.durationMinutes());
        exam.setScoreVisibilityMode(request.scoreVisibilityMode() == null ? exam.getScoreVisibilityMode() : request.scoreVisibilityMode());
        examRepository.save(exam);

        if (request.questions() != null) {
            examQuestionRepository.deleteByExamId(id);
            for (ExamRequests.ExamQuestionItem item : request.questions()) {
                examQuestionRepository.save(buildExamQuestion(exam.getId(), item));
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

        if (currentUser.role() == UserRole.STUDENT) {
            if (exam.getStatus() != ActivityStatus.PUBLISHED && exam.getStatus() != ActivityStatus.CLOSED) {
                throw new BusinessException(40300, "无权限查看该考试");
            }
            boolean isMember = classMemberRepository.findByClassIdAndStudentUserId(exam.getClassId(), currentUser.id()).isPresent();
            if (!isMember) {
                throw new BusinessException(40300, "无权限查看该考试");
            }
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
        result.put("questions", questionList);

        // For students: enrich with submission state so frontend can restore progress/result
        if (currentUser.role() == UserRole.STUDENT) {
            Optional<ExamSubmission> submissionOpt = examSubmissionRepository.findByExamIdAndStudentId(id, currentUser.id());
            if (submissionOpt.isPresent()) {
                ExamSubmission submission = submissionOpt.get();
                result.put("submissionId", submission.getId());
                result.put("submissionStatus", submission.getStatus().name());
                result.put("startedAt", submission.getStartedAt());
                result.put("submittedAt", submission.getSubmittedAt());
                result.put("autoScore", submission.getAutoScore());
                result.put("manualScore", submission.getManualScore());
                boolean scoreVisible = canStudentSeeScore(exam, submission);
                result.put("totalScore", scoreVisible ? submission.getTotalScore() : null);
                result.put("resultAvailable", scoreVisible);

                if (submission.getStatus() == ExamSubmissionStatus.IN_PROGRESS) {
                    result.put("deadlineAt", submission.getDeadlineAt());
                    long remainingSeconds = 0;
                    if (submission.getDeadlineAt() != null) {
                        remainingSeconds = Math.max(0, Duration.between(OffsetDateTime.now(), submission.getDeadlineAt()).getSeconds());
                    }
                    result.put("remainingSeconds", remainingSeconds);
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
                            qMap.put("isCorrect", ea.getIsCorrect());
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
                                    .ifPresent(item -> qMap.put("answerJson", resolveQuestionSnapshot(item).answerJson()));
                        }
                    }
                }
            }
        }

        return result;
    }

    @Transactional
    public Object changeStatus(CurrentUser currentUser, Long id, ExamRequests.ChangeExamStatusRequest request) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限修改该考试");
        }

        ActivityStatus newStatus = ActivityStatus.valueOf(request.status());
        if (exam.getStatus() == ActivityStatus.DRAFT && newStatus == ActivityStatus.PUBLISHED) {
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
        return Map.of("released", true, "scoreVisibilityMode", exam.getScoreVisibilityMode().name());
    }

    @Transactional
    public Object startExam(CurrentUser currentUser, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));

        if (exam.getStatus() != ActivityStatus.PUBLISHED) {
            throw new BusinessException(40000, "考试未发布，不能开始");
        }

        Optional<ExamSubmission> existingOpt = examSubmissionRepository.findByExamIdAndStudentId(examId, currentUser.id());
        if (existingOpt.isPresent()) {
            ExamSubmission existing = existingOpt.get();
            if (existing.getStatus() == ExamSubmissionStatus.IN_PROGRESS) {
                // Return existing in-progress submission
                long remainingSeconds = 0;
                if (existing.getDeadlineAt() != null) {
                    remainingSeconds = Math.max(0, Duration.between(OffsetDateTime.now(), existing.getDeadlineAt()).getSeconds());
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("submissionId", existing.getId());
                result.put("deadlineAt", existing.getDeadlineAt());
                result.put("remainingSeconds", remainingSeconds);
                return result;
            } else {
                // SUBMITTED, AUTO_SUBMITTED, GRADED
                throw new BusinessException(40000, "考试已提交，不能重新开始");
            }
        }

        // Create new submission
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime deadlineAt = now.plusMinutes(exam.getDurationMinutes());

        ExamSubmission submission = new ExamSubmission();
        submission.setExamId(examId);
        submission.setStudentId(currentUser.id());
        submission.setStartedAt(now);
        submission.setDeadlineAt(deadlineAt);
        submission.setStatus(ExamSubmissionStatus.IN_PROGRESS);
        examSubmissionRepository.save(submission);

        long remainingSeconds = Duration.between(now, deadlineAt).getSeconds();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("deadlineAt", deadlineAt);
        result.put("remainingSeconds", remainingSeconds);
        return result;
    }

    @Transactional
    public Object submitExam(CurrentUser currentUser, Long examId, ExamRequests.SubmitExamRequest request) {
        // 1. Load the student's IN_PROGRESS submission for this exam
        ExamSubmission submission = examSubmissionRepository.findByExamIdAndStudentId(examId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40400, "未找到考试提交记录"));

        // 2. Validate submission status is IN_PROGRESS
        if (submission.getStatus() != ExamSubmissionStatus.IN_PROGRESS) {
            throw new BusinessException(40000, "考试已提交，不能重复提交");
        }

        // 3. Determine status based on deadline
        OffsetDateTime now = OffsetDateTime.now();
        if (submission.getDeadlineAt() != null && now.isAfter(submission.getDeadlineAt())) {
            submission.setStatus(ExamSubmissionStatus.AUTO_SUBMITTED);
        } else {
            submission.setStatus(ExamSubmissionStatus.SUBMITTED);
        }

        // 4. Delete any stale exam_answer rows for this submission
        examAnswerRepository.deleteByExamSubmissionId(submission.getId());

        // 5. Build a map of student answers by questionId
        Map<Long, String> studentAnswers = new HashMap<>();
        Map<Long, ExamRequests.SubmitAnswerItem> submitItems = new HashMap<>();
        if (request.answers() != null) {
            for (ExamRequests.SubmitAnswerItem item : request.answers()) {
                submitItems.put(item.questionId(), item);
                studentAnswers.put(item.questionId(), resolveAnswerJson(item));
            }
        }

        // 6. For each exam_question: score and persist ExamAnswer
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySortOrder(examId);
        double autoScore = 0.0;
        boolean hasManualGrading = false;

        for (ExamQuestion eq : examQuestions) {
            QuestionSnapshot snapshot = resolveQuestionSnapshot(eq);
            ExamRequests.SubmitAnswerItem submitItem = submitItems.get(eq.getQuestionId());
            String studentAnswer = studentAnswers.get(eq.getQuestionId());
            String questionType = snapshot.questionType();
            boolean isObjective = examScoringService.isObjective(questionType);
            GradingResultView gradingResult = gradingAdapter.grade(
                    snapshot,
                    submitItem == null
                            ? AnswerPayloadNormalizer.normalize(eq.getQuestionId(), questionType, null, studentAnswer, List.of(), null)
                            : AnswerPayloadNormalizer.normalize(
                                    eq.getQuestionId(),
                                    questionType,
                                    submitItem.answerText(),
                                    submitItem.answerJson(),
                                    submitItem.selectedOptions(),
                                    submitItem.attachmentPath()
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

        // 7-9. Set scores and timestamps
        submission.setAutoScore(autoScore);
        submission.setManualScore(0.0);
        submission.setTotalScore(autoScore);
        submission.setSubmittedAt(now);
        examSubmissionRepository.save(submission);

        if (!hasManualGrading && canStudentSeeScore(examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在")), submission)) {
            upsertScoreRecord(examId, submission, autoScore);
        }

        // 10. Return result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus().name());
        result.put("autoScore", autoScore);
        result.put("totalScore", autoScore);
        result.put("hasManualGrading", hasManualGrading);
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
        ExamSubmission submission = examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        Exam exam = examRepository.findById(submission.getExamId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限批改该考试");
        }
        if (submission.getStatus() != ExamSubmissionStatus.SUBMITTED
                && submission.getStatus() != ExamSubmissionStatus.AUTO_SUBMITTED) {
            throw new BusinessException(40000, "当前状态不允许批改");
        }

        // Update subjective question scores
        for (ExamRequests.GradeExamAnswerItem item : request.answers()) {
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
        ExamSubmission submission = examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(40400, "提交记录不存在"));
        Exam exam = examRepository.findById(submission.getExamId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限批改该考试");
        }
        if (submission.getStatus() != ExamSubmissionStatus.SUBMITTED
                && submission.getStatus() != ExamSubmissionStatus.AUTO_SUBMITTED
                && submission.getStatus() != ExamSubmissionStatus.GRADED) {
            throw new BusinessException(40000, "当前状态不允许批改");
        }
        ExamAnswer answer = examAnswerRepository.findByExamSubmissionIdAndQuestionId(submissionId, request.questionId())
                .orElseThrow(() -> new BusinessException(40400, "答题记录不存在: questionId=" + request.questionId()));
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

    private boolean canStudentSeeScore(Exam exam, ExamSubmission submission) {
        if (submission == null) {
            return false;
        }
        ScoreVisibilityMode mode = exam.getScoreVisibilityMode() == null
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : exam.getScoreVisibilityMode();
        return switch (mode) {
            case IMMEDIATE -> true;
            case AFTER_TEACHER_CONFIRM -> submission.getStatus() == ExamSubmissionStatus.GRADED;
            case AFTER_DEADLINE -> exam.getEndAt() != null && !OffsetDateTime.now().isBefore(exam.getEndAt());
            case MANUAL_RELEASE -> exam.isScoreReleased();
        };
    }

    private void upsertScoreRecord(Long examId, ExamSubmission submission, double totalScore) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        ScoreRecord scoreRecord = scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentId(BusinessType.EXAM, exam.getId(), submission.getStudentId())
                .orElse(new ScoreRecord());
        scoreRecord.setBusinessType(BusinessType.EXAM);
        scoreRecord.setBusinessId(exam.getId());
        scoreRecord.setStudentId(submission.getStudentId());
        scoreRecord.setClassId(exam.getClassId());
        scoreRecord.setScore(totalScore);
        scoreRecord.setGradedAt(OffsetDateTime.now());
        scoreRecordRepository.save(scoreRecord);
    }

    private Map<String, Object> recomputeSubmissionAfterTeacherConfirmation(Exam exam, ExamSubmission submission) {
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
            upsertScoreRecord(exam.getId(), submission, totalScore);
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

    private QuestionSnapshot toInlineSnapshot(ExamRequests.ExamQuestionItem item) {
        return QuestionSnapshotFactory.fromInlineQuestion(
                item.questionType(),
                item.stem(),
                item.optionsJson(),
                item.answerJson(),
                item.scoringConfigJson(),
                item.sortOrder(),
                item.questionScore()
        );
    }

    private ExamQuestion buildExamQuestion(Long examId, ExamRequests.ExamQuestionItem item) {
        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setExamId(examId);
        examQuestion.setQuestionId(item.questionId());
        examQuestion.setSortOrder(item.sortOrder());
        examQuestion.setQuestionScore(item.questionScore());

        QuestionSnapshot snapshot;
        if ("INLINE".equalsIgnoreCase(item.resolvedSourceType())) {
            snapshot = toInlineSnapshot(item);
        } else {
            QuestionBank questionBank = questionBankRepository.findById(item.questionId())
                    .orElseThrow(() -> new BusinessException(40400, "题库题目不存在"));
            snapshot = toSnapshot(questionBank, item.sortOrder(), item.questionScore());
        }
        examQuestion.setQuestionSnapshotJson(writeJson(snapshot));
        return examQuestion;
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
