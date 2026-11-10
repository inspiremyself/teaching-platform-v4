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
public class ExamReportService {

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

    public ExamReportService(ExamRepository examRepository,
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


    public Object getExamResults(CurrentUser currentUser, Long examId) {
        Exam exam = examRepository.findByIdAndSchoolId(examId, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限查看该考试结果");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());

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
        Exam exam = examRepository.findByIdAndSchoolId(submission.getExamId(), currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限查看该提交详情");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());

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

            QuestionSnapshot snapshot = support.resolveQuestionSnapshot(eq, currentUser.schoolId());
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
        Exam exam = examRepository.findByIdAndSchoolId(head.getExamId(), currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限批改该考试");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());
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
                    .map(eq -> support.resolveQuestionSnapshot(eq, currentUser.schoolId()))
                    .orElse(null);
            if (snapshot != null && examScoringService.isObjective(snapshot.questionType())) {
                continue; // Don't let teacher override objective auto-grades
            }
            assertTeacherScoreInRange(item.score(), snapshot);

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
                    .map(eq -> support.resolveQuestionSnapshot(eq, currentUser.schoolId()))
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
        Exam exam = examRepository.findByIdAndSchoolId(head.getExamId(), currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40400, "考试不存在"));
        if (!exam.getCreatedBy().equals(currentUser.id())) {
            throw new BusinessException(40300, "无权限批改该考试");
        }
        classAccessService.assertTeacherAssignedToClass(currentUser, exam.getClassId());
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
                .map(eq -> support.resolveQuestionSnapshot(eq, exam.getSchoolId()))
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
            assertTeacherScoreInRange(request.score(), snapshot);
            answer.setScore(request.score());
            answer.setScoreSource("TEACHER");
            answer.setAcceptedAutoScore(false);
        }
        answer.setTeacherComment(request.teacherComment());
        examAnswerRepository.save(answer);
        return recomputeSubmissionAfterTeacherConfirmation(exam, submission);
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

    /**
     * 教师显式给分校验：整数且 0 ~ 该题快照分值。
     * 数据保全：快照或快照分值缺失时不臆造上限，只校验非负与整数；不改写历史快照与成绩。
     */
    private void assertTeacherScoreInRange(Double score, QuestionSnapshot snapshot) {
        if (score == null) {
            throw new BusinessException(40000, "题目分数不能为空");
        }
        if (score % 1 != 0) {
            throw new BusinessException(40000, "分数必须为整数");
        }
        Double maxScore = snapshot == null ? null : snapshot.score();
        if (score < 0 || (maxScore != null && score > maxScore)) {
            throw new BusinessException(40000, "分数必须在 0 到该题分值之间");
        }
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
                    .map(eq -> support.resolveQuestionSnapshot(eq, exam.getSchoolId()))
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
            support.upsertScoreRecord(exam, submission, totalScore);
        } else if (previousStatus == ExamSubmissionStatus.GRADED) {
            // 从 GRADED 降回 SUBMITTED 时删除残留成绩记录，避免成绩列表继续展示已撤销的结果
            scoreRecordWriter.remove(BusinessType.EXAM, exam.getId(), submission.getStudentId(), exam.getSchoolId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus().name());
        result.put("autoScore", autoScore);
        result.put("manualScore", manualScore);
        result.put("totalScore", totalScore);
        return result;
    }

}
