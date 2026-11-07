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

@Service
public class LabStepService {

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

    private final LabServiceSupport support;

    public LabStepService(LabRepository labRepository,
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
                      @Value("${app.file-storage-root}") String storageRoot,
                      LabServiceSupport support) {
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
        this.support = support;
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherSteps(CurrentUser currentUser, Long labId) {
        support.requireTeacher(currentUser);
        support.ownedLab(currentUser, labId);
        return labStepRepository.findByLabIdOrderByStepNoAsc(labId).stream().map(this::toStepView).toList();
    }



    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBlankItems(CurrentUser currentUser, Long labId) {
        support.requireTeacher(currentUser);
        support.ownedLab(currentUser, labId);
        return labStepRepository.findByLabIdOrderByStepNoAsc(labId).stream()
                .filter(this::isFillBlankStep)
                .map(this::toBlankItemView)
                .toList();
    }



    @Transactional(readOnly = true)
    public Map<String, Object> getBlankItemAnswerDistribution(CurrentUser currentUser, Long labId, Long itemId) {
        support.requireTeacher(currentUser);
        support.ownedLab(currentUser, labId);
        LabStep step = fillBlankStep(labId, itemId);
        List<Long> submissionIds = labSubmissionRepository.findByLabId(labId).stream().map(LabSubmission::getId).toList();
        List<LabStepAnswer> answers = submissionIds.isEmpty()
                ? List.of()
                : labStepAnswerRepository.findByLabSubmissionIdInAndLabStepId(submissionIds, itemId);
        List<String> acceptedAnswers = loadAcceptedAnswers(labId, itemId);

        Map<String, DistributionBucket> buckets = new LinkedHashMap<>();
        for (LabStepAnswer answer : answers) {
            String normalizedAnswer = normalizeBlankAnswer(answer.getAnswerText());
            if (normalizedAnswer.isBlank()) {
                continue;
            }
            DistributionBucket existing = buckets.get(normalizedAnswer);
            if (existing == null) {
                buckets.put(normalizedAnswer, new DistributionBucket(support.defaultString(answer.getAnswerText()).trim(), normalizedAnswer, 1L));
            } else {
                buckets.put(normalizedAnswer, new DistributionBucket(existing.answerText(), existing.normalizedAnswer(), existing.count() + 1));
            }
        }

        List<Map<String, Object>> distribution = buckets.values().stream()
                .sorted(Comparator.comparingLong(DistributionBucket::count).reversed().thenComparing(DistributionBucket::normalizedAnswer))
                .map(bucket -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("answerText", bucket.answerText());
                    item.put("normalizedAnswer", bucket.normalizedAnswer());
                    item.put("count", bucket.count());
                    item.put("accepted", isCurrentlyAcceptedBlankAnswer(step, bucket.answerText()));
                    return item;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", toBlankItemView(step));
        result.put("acceptedAnswers", acceptedAnswers);
        result.put("answerDistribution", distribution);
        return result;
    }



    @Transactional
    public Map<String, Object> saveBlankAcceptedAnswers(CurrentUser currentUser, Long labId, LabRequests.SaveBlankAcceptedAnswersRequest request) {
        support.requireTeacher(currentUser);
        support.ownedLab(currentUser, labId);
        LabStep step = fillBlankStep(labId, request.experimentItemId());
        List<String> acceptedAnswers = sanitizeAcceptedAnswers(request.acceptedAnswers());

        experimentBlankAnswerOverrideRepository.deleteByExperimentIdAndExperimentItemId(labId, step.getId());
        experimentBlankAnswerOverrideRepository.flush();
        List<ExperimentBlankAnswerOverride> overrides = new ArrayList<>();
        for (String acceptedAnswer : acceptedAnswers) {
            ExperimentBlankAnswerOverride override = new ExperimentBlankAnswerOverride();
            override.setExperimentId(labId);
            override.setExperimentItemId(step.getId());
            override.setAcceptedAnswer(acceptedAnswer);
            override.setNormalizedAnswer(normalizeBlankAnswer(acceptedAnswer));
            override.setBlankAnswersJson(serializeBlankAnswerSlots(acceptedAnswer));
            override.setCreatedBy(currentUser.id());
            overrides.add(override);
        }
        if (!overrides.isEmpty()) {
            experimentBlankAnswerOverrideRepository.saveAllAndFlush(overrides);
        }

        Lab lab = labRepository.findById(labId).orElseThrow(() -> new BusinessException(40400, "实验不存在"));
        List<LabSubmission> submissions = labSubmissionRepository.findByLabId(labId);
        List<Long> submissionIds = submissions.stream().map(LabSubmission::getId).toList();
        int regradedCount = 0;
        if (!submissionIds.isEmpty()) {
            Map<Long, LabStepAnswer> answersBySubmissionId = labStepAnswerRepository.findByLabSubmissionIdInAndLabStepId(submissionIds, step.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(LabStepAnswer::getLabSubmissionId, answer -> answer, (left, right) -> left, LinkedHashMap::new));
            for (LabSubmission submission : submissions) {
                LabStepAnswer answer = answersBySubmissionId.get(submission.getId());
                if (answer == null) {
                    continue;
                }
                // 重判会改写总分与成绩记录：先锁提交行再重读，避免与并发提交/批改互相覆盖。
                LabSubmission lockedSubmission = labSubmissionRepository
                        .findByLabIdAndStudentIdForUpdate(labId, submission.getStudentId())
                        .orElse(null);
                if (lockedSubmission == null) {
                    continue;
                }
                ScoringResult result = labScoringSupport.score(step, answer);
                boolean visible = support.scoreVisible(lab, lockedSubmission);
                if (visible) {
                    // 成绩已对学生可见：冻结分数与人工评语，只刷新自动分/建议分/判分明细，
                    // 既不重算总分，也不写成绩记录，避免教师改可接受答案直接改写已发布成绩。
                    applySuggestedScoringResult(answer, result);
                } else if (lockedSubmission.getSubmitStatus() == SubmissionStatus.SAVED) {
                    // 草稿态尚未提交：仍允许全量重判，草稿分数只在教师侧可见。
                    support.applyScoringResult(answer, result);
                } else if ("TEACHER".equals(answer.getScoreSource())) {
                    // 已提交且教师已完成人工给分：保留原分数与评语，只刷新建议字段供教师参考。
                    applySuggestedScoringResult(answer, result);
                } else {
                    support.applyScoringResult(answer, result);
                }
                labStepAnswerRepository.save(answer);
                regradedCount++;
                if (visible) {
                    continue;
                }
                if (lockedSubmission.getSubmitStatus() == SubmissionStatus.SAVED) {
                    support.refreshSavedSubmissionAfterScoring(lockedSubmission);
                } else {
                    support.refreshSubmissionAfterScoring(lab, lockedSubmission);
                }
            }
        }

        return Map.of(
                "saved", true,
                "experimentItemId", step.getId(),
                "regradedCount", regradedCount,
                "acceptedAnswers", acceptedAnswers
        );
    }



    @Transactional
    public Map<String, Object> createStep(CurrentUser currentUser, Long labId, LabRequests.CreateStepRequest request) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
        assertLabStructureEditable(lab);
        LabStep step = new LabStep();
        applyStep(step, labId, request.stepNo(), request.title(), request.questionType(), request.content(), request.answerConfigJson(), request.stepScore(), request.allowPaste());
        return toStepView(labStepRepository.save(step));
    }



    @Transactional
    public Map<String, Object> updateStep(CurrentUser currentUser, Long stepId, LabRequests.UpdateStepRequest request) {
        support.requireTeacher(currentUser);
        LabStep step = labStepRepository.findById(stepId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        Lab lab = support.ownedLab(currentUser, step.getLabId());
        assertLabStructureEditable(lab);
        applyStep(step, step.getLabId(), request.stepNo(), request.title(), request.questionType(), request.content(), request.answerConfigJson(), request.stepScore(), request.allowPaste());
        return toStepView(labStepRepository.save(step));
    }



    @Transactional
    public Map<String, Object> deleteStep(CurrentUser currentUser, Long stepId) {
        support.requireTeacher(currentUser);
        LabStep step = labStepRepository.findById(stepId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        Lab lab = support.ownedLab(currentUser, step.getLabId());
        assertLabStructureEditable(lab);
        labStepRepository.delete(step);
        return Map.of("deleted", true, "id", stepId);
    }



    @Transactional(readOnly = true)
    public Map<String, Object> getLabItemAnswers(CurrentUser currentUser, Long labId, Long itemId) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
        LabStep step = labStepRepository.findById(itemId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        if (!Objects.equals(step.getLabId(), labId)) {
            throw new BusinessException(40000, "实验步骤不属于该实验");
        }

        List<ClassMember> members = classMemberRepository.findByClassId(lab.getClassId());
        List<LabSubmission> submissions = labSubmissionRepository.findByLabId(labId);
        Map<Long, LabSubmission> submissionByStudentId = support.indexSubmissionsByStudentId(submissions);
        List<Long> submissionIds = submissions.stream().map(LabSubmission::getId).toList();
        Map<Long, LabStepAnswer> answerBySubmissionId = indexAnswersBySubmissionId(
                submissionIds.isEmpty()
                        ? List.of()
                        : labStepAnswerRepository.findByLabSubmissionIdInAndLabStepId(submissionIds, itemId)
        );

        List<Map<String, Object>> answers = new ArrayList<>();
        for (ClassMember member : members) {
            SysUser student = sysUserRepository.findById(member.getStudentUserId()).orElse(null);
            if (student == null) {
                continue;
            }
            LabSubmission submission = submissionByStudentId.get(student.getId());
            LabStepAnswer answer = submission == null ? null : answerBySubmissionId.get(submission.getId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", student.getId());
            row.put("studentNo", student.getUsername());
            row.put("studentName", student.getDisplayName());
            row.put("submissionId", submission == null ? null : submission.getId());
            row.put("submitStatus", submission == null ? null : submission.getSubmitStatus().name());
            row.put("answerText", answer == null ? "" : support.resolveStudentAnswerText(answer));
            row.put("images", answer == null ? List.of() : support.toAnswerImageViews(answer.getAnswerJson()));
            row.put("score", answer == null ? null : answer.getScore());
            row.put("teacherComment", answer == null ? null : answer.getTeacherComment());
            answers.add(row);
        }
        answers.sort(Comparator.comparing(row -> support.normalize(row.get("studentNo"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labId", lab.getId());
        result.put("labTitle", lab.getTitle());
        result.put("itemId", step.getId());
        result.put("stepNo", step.getStepNo());
        result.put("title", step.getTitle());
        result.put("content", support.defaultString(step.getContent()));
        result.put("answers", answers);
        return result;
    }



    /**
     * 校验实验题项结构是否可变更。
     *
     * <p>题项的新增/修改/删除会破坏已产生的答案与成绩，因此仅允许在草稿态且零提交时执行。</p>
     */
    private void assertLabStructureEditable(Lab lab) {
        if (lab.getStatus() != ActivityStatus.DRAFT) {
            throw new BusinessException(40000, "实验已发布或已关闭，不能修改题项");
        }
        if (labSubmissionRepository.countByLabId(lab.getId()) > 0) {
            throw new BusinessException(40000, "已有学生作答，不能修改题项");
        }
    }



    private Map<Long, LabStepAnswer> indexAnswersBySubmissionId(List<LabStepAnswer> answers) {
        Map<Long, LabStepAnswer> indexed = new LinkedHashMap<>();
        for (LabStepAnswer answer : answers) {
            indexed.putIfAbsent(answer.getLabSubmissionId(), answer);
        }
        return indexed;
    }



    private void applyStep(LabStep step, Long labId, Integer stepNo, String title, String questionType, String content, String answerConfigJson, Integer stepScore, boolean allowPaste) {
        step.setLabId(labId);
        step.setStepNo(stepNo);
        step.setTitle(title);
        step.setQuestionType(questionType);
        step.setContent(content);
        step.setAnswerConfigJson(answerConfigJson == null || answerConfigJson.isBlank() ? "{}" : answerConfigJson);
        step.setStepScore(stepScore);
        step.setAllowPaste(allowPaste);
    }



    private Map<String, Object> toStepView(LabStep step) {
        return Map.of(
                "id", step.getId(),
                "labId", step.getLabId(),
                "stepNo", step.getStepNo(),
                "title", step.getTitle(),
                "questionType", step.getQuestionType(),
                "content", step.getContent(),
                "answerConfigJson", support.defaultString(step.getAnswerConfigJson()),
                "stepScore", step.getStepScore(),
                "allowPaste", step.isAllowPaste()
        );
    }



    private Map<String, Object> toBlankItemView(LabStep step) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", step.getId());
        item.put("labId", step.getLabId());
        item.put("stepId", step.getId());
        item.put("stepNo", step.getStepNo());
        item.put("title", step.getTitle());
        item.put("questionType", step.getQuestionType());
        item.put("stepScore", step.getStepScore());
        item.put("questionId", step.getQuestionId());
        item.put("content", step.getContent());
        return item;
    }



    /**
     * 只刷新自动分、建议分与判分明细，不动最终分、评分来源与教师评语。
     *
     * <p>用于两类冻结场景：成绩已对学生可见，或教师已完成人工给分。
     * 这两类情况下重新计算可接受答案只能影响教师侧的参考信息，
     * 不允许回写 {@code score / scoreSource / teacherComment}。</p>
     */
    private void applySuggestedScoringResult(LabStepAnswer answer, ScoringResult result) {
        answer.setAutoScore(result.autoScore());
        answer.setSuggestedScore(result.recommendedScore());
        answer.setAutoJudgeDetail(result.judgeDetail());
    }



    private LabStep fillBlankStep(Long labId, Long itemId) {
        LabStep step = labStepRepository.findById(itemId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        if (!Objects.equals(step.getLabId(), labId)) {
            throw new BusinessException(40000, "实验步骤不属于该实验");
        }
        if (!isFillBlankStep(step)) {
            throw new BusinessException(40000, "当前步骤不是填空题");
        }
        return step;
    }



    private boolean isFillBlankStep(LabStep step) {
        return step != null && "FILL_BLANK".equalsIgnoreCase(support.defaultString(step.getQuestionType()));
    }



    private List<String> loadAcceptedAnswers(Long labId, Long itemId) {
        return experimentBlankAnswerOverrideRepository.findByExperimentIdAndExperimentItemIdOrderByIdAsc(labId, itemId).stream()
                .map(ExperimentBlankAnswerOverride::getAcceptedAnswer)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }



    private boolean isCurrentlyAcceptedBlankAnswer(LabStep step, String answerText) {
        String normalized = normalizeBlankAnswer(answerText);
        if (normalized.isBlank()) {
            return false;
        }
        return experimentBlankAnswerOverrideRepository.findByExperimentIdAndExperimentItemIdOrderByIdAsc(step.getLabId(), step.getId()).stream()
                .map(ExperimentBlankAnswerOverride::getNormalizedAnswer)
                .anyMatch(normalized::equals);
    }



    private List<String> sanitizeAcceptedAnswers(List<String> acceptedAnswers) {
        if (acceptedAnswers == null || acceptedAnswers.isEmpty()) {
            return List.of();
        }
        Map<String, String> deduplicated = new LinkedHashMap<>();
        for (String acceptedAnswer : acceptedAnswers) {
            String raw = support.defaultString(acceptedAnswer).trim();
            String normalized = normalizeBlankAnswer(raw);
            if (!normalized.isBlank()) {
                deduplicated.putIfAbsent(normalized, raw);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }



    private String normalizeBlankAnswer(String value) {
        return support.defaultString(value)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }



    private List<String> parseBlankAnswerSlots(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return List.of();
        }
        String normalized = answerText.replace('，', ',');
        String[] tokens = normalized.split("\\s*,\\s*|\\s+");
        List<String> values = new ArrayList<>();
        for (String token : tokens) {
            String item = token == null ? "" : token.trim();
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }



    private String serializeBlankAnswerSlots(String answerText) {
        List<String> blankAnswers = parseBlankAnswerSlots(answerText);
        if (blankAnswers.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < blankAnswers.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"')
                    .append(blankAnswers.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        builder.append(']');
        return builder.toString();
    }



    private record DistributionBucket(String answerText, String normalizedAnswer, long count) {
    }

}
