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
import org.springframework.stereotype.Component;

@Component
public class HomeworkServiceSupport {

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

    public HomeworkServiceSupport(HomeworkRepository homeworkRepository,
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


    public void requireTeacher(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.TEACHER) {
            throw new BusinessException(40300, "无教师权限");
        }
    }



    public void requireStudent(CurrentUser currentUser) {
        if (currentUser.role() != UserRole.STUDENT) {
            throw new BusinessException(40300, "无学生权限");
        }
    }



    public Homework ownedHomework(CurrentUser currentUser, Long homeworkId) {
        Homework homework = homeworkRepository.findByIdAndCreatedBy(homeworkId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该作业"));
        classAccessService.assertTeacherAssignedToClass(currentUser, homework.getClassId());
        return homework;
    }



    public Homework accessiblePublishedHomework(CurrentUser currentUser, Long homeworkId) {
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



    public Map<String, Object> readJsonMap(String json) {
        return readJsonValue(json, new TypeReference<Map<String, Object>>() {}, Map.of());
    }



    public <T> T readJsonValue(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new BusinessException(40000, "JSON 数据格式不正确");
        }
    }



    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(50000, "JSON 序列化失败");
        }
    }



    public void refreshSubmissionGradeState(HomeworkSubmission submission) {
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



    public void upsertScoreRecord(Homework homework, Long studentId, Double score, OffsetDateTime gradedAt) {
        scoreRecordWriter.write(BusinessType.HOMEWORK, homework.getId(), studentId,
                homework.getClassId(), score, gradedAt);
    }



    public String defaultString(String value) {
        return value == null ? "" : value;
    }



    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
