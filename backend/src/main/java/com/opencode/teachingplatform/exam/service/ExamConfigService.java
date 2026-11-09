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
public class ExamConfigService {

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

    public ExamConfigService(ExamRepository examRepository,
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


    public Object listExams(CurrentUser currentUser) {
        if (currentUser.role() == UserRole.TEACHER) {
            List<Exam> exams = examRepository.findBySchoolIdAndCreatedBy(currentUser.schoolId(), currentUser.id());
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
                List<Exam> exams = examRepository.findBySchoolIdAndClassId(currentUser.schoolId(), membership.getClassId());
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
                    boolean canViewResult = submissionOpt.isPresent() && support.canStudentSeeScore(exam, submissionOpt.get());

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
        if (currentUser.schoolId() == null) {
            throw new BusinessException(40000, "账号未绑定学校，无法创建教学数据");
        }
        exam.setSchoolId(currentUser.schoolId());
        assertBankQuestions(request.questions());
        assertNoDuplicateQuestions(request.questions());
        Map<Long, QuestionBank> bankQuestions = loadOwnedBankQuestions(request.questions(), currentUser.id(), currentUser.schoolId());
        if (exam.getStatus() == ActivityStatus.PUBLISHED) {
            assertPublishableTimeWindow(request.startAt(), request.endAt(), OffsetDateTime.now());
        } else {
            assertTimeWindowOrder(request.startAt(), request.endAt());
        }
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
        Exam exam = examRepository.findByIdAndSchoolId(id, currentUser.schoolId())
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
        Map<Long, QuestionBank> bankQuestions = loadOwnedBankQuestions(request.questions(), currentUser.id(), currentUser.schoolId());
        assertTimeWindowOrder(request.startAt(), request.endAt());

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
        Exam exam = examRepository.findByIdAndSchoolId(id, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));

        if (currentUser.role() == UserRole.TEACHER && !exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限查看该考试");
        }
        if (currentUser.role() == UserRole.TEACHER) {
            classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());
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
            QuestionSnapshot snapshot = support.resolveQuestionSnapshot(eq, currentUser.schoolId());
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
                    questionBankRepository.findByIdAndSchoolId(eq.getQuestionId(), currentUser.schoolId()).ifPresent(qb -> qMap.put("analysisText", qb.getAnalysisText()));
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
                boolean scoreVisible = support.canStudentSeeScore(exam, submission);
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
                                        qMap.put("answerJson", support.resolveQuestionSnapshot(item, currentUser.schoolId()).answerJson());
                                        if (item.getQuestionId() != null) {
                                            questionBankRepository.findByIdAndSchoolId(item.getQuestionId(), currentUser.schoolId())
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



    @Transactional
    public Object changeStatus(CurrentUser currentUser, Long id, ExamRequests.ChangeExamStatusRequest request) {
        Exam exam = examRepository.findByIdAndSchoolId(id, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限修改该考试");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());

        ActivityStatus newStatus = EnumValueParser.parse(ActivityStatus.class, request.status(), "考试状态");
        if (exam.getStatus() == ActivityStatus.DRAFT && newStatus == ActivityStatus.PUBLISHED) {
            boolean hasLegacyInlineQuestion = examQuestionRepository
                    .findByExamIdOrderBySortOrder(id).stream()
                    .anyMatch(item -> item.getQuestionId() == null);
            if (hasLegacyInlineQuestion) {
                throw new BusinessException(40000, "请先将题目保存到题库后再组卷");
            }
            assertPublishableTimeWindow(exam.getStartAt(), exam.getEndAt(), OffsetDateTime.now());
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
        Exam exam = examRepository.findByIdAndSchoolId(examId, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限发布该考试成绩");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());
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
                    exam.getClassId(), lockedScore == null ? 0D : lockedScore, now, exam.getSchoolId());
        }
        return ScoreReleaseSupport.releasedView(exam.getScoreVisibilityMode().name());
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



    private ExamQuestion buildExamQuestion(Long examId, ExamRequests.ExamQuestionItem item, QuestionBank questionBank) {
        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setExamId(examId);
        examQuestion.setQuestionId(item.questionId());
        examQuestion.setSortOrder(item.sortOrder());
        examQuestion.setQuestionScore(item.questionScore());
        examQuestion.setQuestionSnapshotJson(support.writeJson(support.toSnapshot(questionBank, item.sortOrder(), item.questionScore())));
        return examQuestion;
    }



    /**
     * 逐项加载当前教师拥有的题库题目；缺失或非本人统一返回 404。
     * 必须在写主表 / 批量删除题目之前调用，保证失败路径不产生任何写操作。
     */
    private Map<Long, QuestionBank> loadOwnedBankQuestions(List<ExamRequests.ExamQuestionItem> items, Long teacherId, Long schoolId) {
        Map<Long, QuestionBank> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (ExamRequests.ExamQuestionItem item : items) {
            QuestionBank bank = questionBankRepository.findByIdAndSchoolIdAndCreatedBy(item.questionId(), schoolId, teacherId)
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

    private void assertTimeWindowOrder(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new BusinessException(40000, "开始时间必须早于结束时间");
        }
    }

    private void assertPublishableTimeWindow(OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime now) {
        if (endAt == null) {
            throw new BusinessException(40000, "请先设置考试结束时间");
        }
        if (!endAt.isAfter(now)) {
            throw new BusinessException(40000, "结束时间必须晚于当前时间");
        }
        assertTimeWindowOrder(startAt, endAt);
    }

}
