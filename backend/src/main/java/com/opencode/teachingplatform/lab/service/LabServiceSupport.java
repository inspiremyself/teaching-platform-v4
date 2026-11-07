package com.opencode.teachingplatform.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.api.PagedResult;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.common.enums.EnumValueParser;
import com.opencode.teachingplatform.common.util.ActivityStatusParser;
import com.opencode.teachingplatform.grading.service.ScoreReleaseSupport;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
import com.opencode.teachingplatform.lab.behavior.entity.BehaviorEvent;
import com.opencode.teachingplatform.lab.behavior.entity.LabSubmissionMetrics;
import com.opencode.teachingplatform.lab.behavior.repository.BehaviorEventRepository;
import com.opencode.teachingplatform.lab.behavior.repository.LabSubmissionMetricsRepository;
import com.opencode.teachingplatform.lab.behavior.service.LabBehaviorMetricsService;
import com.opencode.teachingplatform.lab.dto.LabRequests;
import com.opencode.teachingplatform.lab.entity.ExperimentBlankAnswerOverride;
import com.opencode.teachingplatform.lab.entity.Lab;
import com.opencode.teachingplatform.lab.entity.LabStep;
import com.opencode.teachingplatform.lab.entity.LabStepAnswer;
import com.opencode.teachingplatform.lab.entity.LabStepAnswerLog;
import com.opencode.teachingplatform.lab.entity.LabSubmission;
import com.opencode.teachingplatform.lab.repository.ExperimentBlankAnswerOverrideRepository;
import com.opencode.teachingplatform.lab.repository.LabRepository;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerLogRepository;
import com.opencode.teachingplatform.lab.repository.LabStepAnswerRepository;
import com.opencode.teachingplatform.lab.repository.LabStepRepository;
import com.opencode.teachingplatform.lab.repository.LabSubmissionRepository;
import com.opencode.teachingplatform.material.entity.CourseMaterial;
import com.opencode.teachingplatform.material.repository.CourseMaterialRepository;
import com.opencode.teachingplatform.plagiarism.service.LocalPlagiarismService;
import com.opencode.teachingplatform.plagiarism.service.PlagiarismTaskWriter;
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LabServiceSupport {

    private static final Logger log = LoggerFactory.getLogger(LabService.class);
    private static final int DEFAULT_REPORT_PAGE_SIZE = 10;
    private static final int MAX_REPORT_PAGE_SIZE = 100;
    private final LabRepository labRepository;
    private final LabStepRepository labStepRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabStepAnswerRepository labStepAnswerRepository;
    private final LabStepAnswerLogRepository labStepAnswerLogRepository;
    private final ExperimentBlankAnswerOverrideRepository experimentBlankAnswerOverrideRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassAccessService classAccessService;
    private final ClassMemberRepository classMemberRepository;
    private final CourseMaterialRepository courseMaterialRepository;
    private final SysUserRepository sysUserRepository;
    private final LabScoringSupport labScoringSupport;
    private final ScoreRecordRepository scoreRecordRepository;
    private final ScoreRecordWriter scoreRecordWriter;
    private final ObjectMapper objectMapper;
    private final LocalFileStorageService localFileStorageService;
    private final LabBehaviorMetricsService labBehaviorMetricsService;
    private final LabSubmissionMetricsRepository labSubmissionMetricsRepository;
    private final BehaviorEventRepository behaviorEventRepository;
    private final Path storageRoot;
    private final LabSubmissionClaimService labSubmissionClaimService;
    private final LocalPlagiarismService localPlagiarismService;
    private final PlagiarismTaskWriter plagiarismTaskWriter;
    private static final String LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE = "实验尚未配置题项，无法发布";
    private static final String LAB_NOT_STARTED_MESSAGE = "实验尚未开始";
    private static final String LAB_DEADLINE_PASSED_MESSAGE = "实验已截止，不能保存/提交";
    private static final String LAB_TIME_ORDER_MESSAGE = "开始时间必须早于结束时间";
    private static final String LAB_DEADLINE_MODE_WITHOUT_DUE_MESSAGE = "截止后可见模式必须设置结束时间";

    public LabServiceSupport(LabRepository labRepository,
                      LabStepRepository labStepRepository,
                      LabSubmissionRepository labSubmissionRepository,
                      LabStepAnswerRepository labStepAnswerRepository,
                      LabStepAnswerLogRepository labStepAnswerLogRepository,
                      ExperimentBlankAnswerOverrideRepository experimentBlankAnswerOverrideRepository,
                      ClassRoomRepository classRoomRepository,
                      ClassAccessService classAccessService,
                      ClassMemberRepository classMemberRepository,
                      CourseMaterialRepository courseMaterialRepository,
                      SysUserRepository sysUserRepository,
                      LabScoringSupport labScoringSupport,
                      ScoreRecordRepository scoreRecordRepository,
                      ScoreRecordWriter scoreRecordWriter,
                      ObjectMapper objectMapper,
                      LocalFileStorageService localFileStorageService,
                      LabBehaviorMetricsService labBehaviorMetricsService,
                      LabSubmissionMetricsRepository labSubmissionMetricsRepository,
                      BehaviorEventRepository behaviorEventRepository,
                      LabSubmissionClaimService labSubmissionClaimService,
                      LocalPlagiarismService localPlagiarismService,
                      PlagiarismTaskWriter plagiarismTaskWriter,
                      @Value("${app.file-storage-root}") String storageRoot) {
        this.labRepository = labRepository;
        this.labStepRepository = labStepRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labStepAnswerRepository = labStepAnswerRepository;
        this.labStepAnswerLogRepository = labStepAnswerLogRepository;
        this.experimentBlankAnswerOverrideRepository = experimentBlankAnswerOverrideRepository;
        this.classRoomRepository = classRoomRepository;
        this.classAccessService = classAccessService;
        this.classMemberRepository = classMemberRepository;
        this.courseMaterialRepository = courseMaterialRepository;
        this.sysUserRepository = sysUserRepository;
        this.labScoringSupport = labScoringSupport;
        this.scoreRecordRepository = scoreRecordRepository;
        this.scoreRecordWriter = scoreRecordWriter;
        this.objectMapper = objectMapper;
        this.localFileStorageService = localFileStorageService;
        this.labBehaviorMetricsService = labBehaviorMetricsService;
        this.labSubmissionMetricsRepository = labSubmissionMetricsRepository;
        this.behaviorEventRepository = behaviorEventRepository;
        this.labSubmissionClaimService = labSubmissionClaimService;
        this.localPlagiarismService = localPlagiarismService;
        this.plagiarismTaskWriter = plagiarismTaskWriter;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
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



    public List<Map<String, Object>> toAnswerImageViews(String answerJson) {
        return LabAnswerImageSupport.extractImages(answerJson, objectMapper).stream()
                .map(image -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("path", image.path());
                    view.put("name", image.name());
                    view.put("contentType", image.contentType());
                    view.put("size", image.size());
                    return view;
                })
                .toList();
    }



    public Lab accessiblePublishedLab(CurrentUser currentUser, Long labId) {
        Lab lab = labRepository.findById(labId).orElseThrow(() -> new BusinessException(40400, "实验不存在"));
        if (lab.getStatus() == ActivityStatus.DRAFT) {
            throw new BusinessException(40300, "无权限访问该实验");
        }
        boolean enrolled = classMemberRepository.findByClassIdAndStudentUserId(lab.getClassId(), currentUser.id()).isPresent();
        if (!enrolled) {
            throw new BusinessException(40300, "无权限访问该实验");
        }
        return lab;
    }



    public Lab ownedLab(CurrentUser currentUser, Long labId) {
        Lab lab = labRepository.findByIdAndCreatedBy(labId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该实验"));
        classAccessService.assertTeacherAssignedToClass(currentUser, lab.getClassId());
        return lab;
    }



    public Map<Long, LabSubmission> indexSubmissionsByStudentId(List<LabSubmission> submissions) {
        Map<Long, LabSubmission> indexed = new LinkedHashMap<>();
        for (LabSubmission submission : submissions) {
            indexed.putIfAbsent(submission.getStudentId(), submission);
        }
        return indexed;
    }



    public String resolveStudentAnswerText(LabStepAnswer answer) {
        if (answer == null) {
            return "";
        }
        if (!defaultString(answer.getAnswerText()).isBlank()) {
            return defaultString(answer.getAnswerText());
        }
        return labStepAnswerLogRepository.findFirstByLabStepAnswerIdOrderByFillTimeDescIdDesc(answer.getId())
                .map(LabStepAnswerLog::getContent)
                .map(this::defaultString)
                .orElse("");
    }



    public boolean scoreVisible(Lab lab, LabSubmission submission) {
        if (submission == null) {
            return false;
        }
        ScoreVisibilityMode mode = lab.getScoreVisibilityMode() == null
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : lab.getScoreVisibilityMode();
        return switch (mode) {
            case IMMEDIATE -> submission.getTotalScore() != null;
            case AFTER_TEACHER_CONFIRM -> submission.getSubmitStatus() == SubmissionStatus.GRADED;
            case AFTER_DEADLINE -> lab.getEndAt() != null && !OffsetDateTime.now().isBefore(lab.getEndAt());
            case MANUAL_RELEASE -> lab.isScoreReleased();
        };
    }



    public void refreshSubmissionAfterScoring(Lab lab, LabSubmission submission) {
        SubmissionStatus previousStatus = submission.getSubmitStatus();
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        double totalScore = answers.stream()
                .map(LabStepAnswer::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        boolean allAnswered = !steps.isEmpty() && steps.stream().allMatch(step ->
                answers.stream().anyMatch(answer -> Objects.equals(answer.getLabStepId(), step.getId())));
        boolean allScored = allAnswered && answers.stream().allMatch(answer -> answer.getScore() != null);
        submission.setTotalScore(totalScore);
        if (allScored) {
            submission.setSubmitStatus(SubmissionStatus.GRADED);
            submission.setGradedAt(OffsetDateTime.now());
            upsertScoreRecord(lab, submission, totalScore);
        } else {
            submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
            submission.setGradedAt(null);
            if (previousStatus == SubmissionStatus.GRADED) {
                scoreRecordWriter.remove(BusinessType.LAB, lab.getId(), submission.getStudentId());
            }
        }
        labSubmissionRepository.save(submission);
    }



    public void refreshSavedSubmissionAfterScoring(LabSubmission submission) {
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        double totalScore = answers.stream()
                .map(LabStepAnswer::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        submission.setTotalScore(totalScore);
        submission.setSubmitStatus(SubmissionStatus.SAVED);
        submission.setGradedAt(null);
        labSubmissionRepository.save(submission);
    }



    public void applyScoringResult(LabStepAnswer answer, ScoringResult result) {
        answer.setAutoScore(result.autoScore());
        answer.setSuggestedScore(result.recommendedScore());
        answer.setScoreSource(result.scoreSource() == ScoreSource.RECOMMENDED ? "SUGGESTED" : result.scoreSource().name());
        answer.setAutoJudgeDetail(result.judgeDetail());
        if (result.scoreSource() == ScoreSource.AUTO || result.scoreSource() == ScoreSource.AUTO_ACCEPTED) {
            answer.setScore(result.finalScore());
        } else {
            answer.setScore(null);
        }
    }



    public void upsertScoreRecord(Lab lab, LabSubmission submission, Double totalScore) {
        scoreRecordWriter.write(BusinessType.LAB, lab.getId(), submission.getStudentId(),
                lab.getClassId(), totalScore, OffsetDateTime.now());
    }



    public String resolveClassName(Long classId) {
        return classRoomRepository.findById(classId)
                .map(ClassRoom::getName)
                .orElse("");
    }



    public String normalize(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }



    public String defaultString(String value) {
        return value == null ? "" : value;
    }



    public boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
