package com.opencode.teachingplatform.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.api.PagedResult;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
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
import com.opencode.teachingplatform.grading.enums.ScoreSource;
import com.opencode.teachingplatform.grading.model.ScoringResult;
import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
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
/**
 * 实验模块的应用服务层。
 *
 * <p>它负责把实验、实验步骤、学生提交、步骤答案、填空覆盖答案、成绩记录等多个仓储串起来，
 * 形成教师配置实验、学生作答、自动评分、教师确认、成绩发布这一整条业务闭环。</p>
 *
 * <p>如果 controller 是“接口入口”，那么这个类就是实验模块真正的运行逻辑中心。</p>
 */
public class LabService {

    /** 教师报告队列默认每页条数。 */
    private static final int DEFAULT_REPORT_PAGE_SIZE = 10;

    /** 教师报告队列单页上限，与审计日志分页口径保持一致。 */
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
    private final ObjectMapper objectMapper;
    private final LocalFileStorageService localFileStorageService;
    private final LabBehaviorMetricsService labBehaviorMetricsService;
    private final LabSubmissionMetricsRepository labSubmissionMetricsRepository;
    private final BehaviorEventRepository behaviorEventRepository;
    private final Path storageRoot;

    public LabService(LabRepository labRepository,
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
                      ObjectMapper objectMapper,
                      LocalFileStorageService localFileStorageService,
                      LabBehaviorMetricsService labBehaviorMetricsService,
                      LabSubmissionMetricsRepository labSubmissionMetricsRepository,
                      BehaviorEventRepository behaviorEventRepository,
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
        this.objectMapper = objectMapper;
        this.localFileStorageService = localFileStorageService;
        this.labBehaviorMetricsService = labBehaviorMetricsService;
        this.labSubmissionMetricsRepository = labSubmissionMetricsRepository;
        this.behaviorEventRepository = behaviorEventRepository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public record PreviewAnswerImageResult(String contentType, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherLabs(CurrentUser currentUser) {
        requireTeacher(currentUser);
        return labRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream().map(this::toTeacherLabView).toList();
    }

    @Transactional
    /**
     * 教师创建实验。
     *
     * <p>这里体现了典型的后端分层：controller 只转发请求，service 负责校验教师是否拥有目标班级，
     * 再把 DTO 中的业务字段映射到 {@link Lab} 实体并落库。</p>
     */
    public Map<String, Object> createLab(CurrentUser currentUser, LabRequests.CreateLabRequest request) {
        requireTeacher(currentUser);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        if (parseStatus(request.status()) == ActivityStatus.PUBLISHED) {
            throw new BusinessException(40000, LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE);
        }
        Lab lab = new Lab();
        lab.setCreatedBy(currentUser.id());
        applyLab(lab, request.classId(), request.title(), request.description(), request.experimentContent(), request.experimentType(), request.status(), request.materialId(), request.scoreVisibilityMode(), request.summaryRequired());
        return toTeacherLabView(labRepository.save(lab));
    }

    @Transactional
    /**
     * 从已有实验深度拷贝创建新实验。
     *
     * <p>复制实验主表配置、全部题项与填空题可接受答案；学生提交、答案、成绩等运行数据不随复制迁移。
     * 新实验拥有独立 ID 与编号（由实体 {@code @PrePersist} 自动生成，与 {@link #createLab} 同一套机制），
     * 创建人为当前教师，成绩发布状态重置为未发布，起止时间清空待重新排期。</p>
     *
     * <p>关键步骤：
     * 1) 校验源实验归属（仅 {@code created_by}，允许把不再任教的旧班实验复制到新班）；
     * 2) 校验目标班级任教关系；
     * 3) 发布门禁前置（源实验无题项时禁止直接复制为已发布，落库前基于源题项列表判断）；
     * 4) 复制主表并落库；
     * 5) 逐题项复制并记录「源题项 ID → 新题项 ID」映射；
     * 6) 依据映射复制填空题可接受答案。</p>
     *
     * @param currentUser 当前登录教师
     * @param sourceLabId 源实验 ID
     * @param request     目标班级、新实验名称（可空，缺省为「源标题（副本）」）、发布状态（可空，缺省草稿）
     * @return 新实验的教师视图（结构与台账行一致）
     */
    public Map<String, Object> copyLab(CurrentUser currentUser, Long sourceLabId,
                                       LabRequests.CopyLabRequest request) {
        requireTeacher(currentUser);

        // 源实验只校验 ownership，不复用 ownedLab：后者还要求当前仍任教源班级，会阻断跨班复用
        Lab source = labRepository.findByIdAndCreatedBy(sourceLabId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该实验"));

        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());

        // 发布门禁前置：源实验无题项时不允许直接复制为已发布
        List<LabStep> sourceSteps = labStepRepository.findByLabIdOrderByStepNoAsc(source.getId());
        ActivityStatus targetStatus = parseStatus(request.status());
        if (targetStatus == ActivityStatus.PUBLISHED && sourceSteps.isEmpty()) {
            throw new BusinessException(40000, LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE);
        }

        // 复制主表：ID/编号/审计时间由实体自动生成，运行态字段（成绩发布、起止时间）不随源迁移
        Lab copy = new Lab();
        copy.setCreatedBy(currentUser.id());
        copy.setClassId(request.classId());
        copy.setTitle(resolveCopyTitle(source.getTitle(), request.title()));
        copy.setDescription(source.getDescription());
        copy.setExperimentType(source.getExperimentType());
        copy.setInstructionType(source.getInstructionType());
        copy.setExperimentContent(source.getExperimentContent());
        copy.setMaterialId(source.getMaterialId());
        copy.setScoreVisibilityMode(source.getScoreVisibilityMode());
        copy.setSummaryRequired(source.isSummaryRequired());
        copy.setScoreReleased(false);
        copy.setStatus(targetStatus);
        Lab saved = labRepository.save(copy);

        // 复制题项，记录「源题项 ID → 新题项 ID」映射，供填空答案外键改指
        Map<Long, Long> itemIdMapping = new LinkedHashMap<>();
        for (LabStep sourceStep : sourceSteps) {
            LabStep step = copyStep(sourceStep, saved.getId());
            itemIdMapping.put(sourceStep.getId(), labStepRepository.save(step).getId());
        }

        copyBlankOverrides(source.getId(), saved.getId(), itemIdMapping, currentUser.id());

        return toTeacherLabView(saved);
    }

    /**
     * 复制单个题项到目标实验。
     *
     * <p>{@link LabStep} 的 setter 带派生副作用：{@code setQuestionType} 会重置
     * experimentItemType / questionSnapshotJson / editorLanguage；{@code setAnswerConfigJson}
     * 会把 experimentItemAnswer 覆写为答案配置。因此必须按「先 questionType，再覆盖派生字段，
     * 最后 answerConfigJson 与 experimentItemAnswer」的顺序赋值，避免源值被默认值覆盖。</p>
     *
     * @param source      源题项
     * @param targetLabId 目标实验 ID
     * @return 待落库的新题项（ID 由实体 {@code @PrePersist} 生成）
     */
    private LabStep copyStep(LabStep source, Long targetLabId) {
        LabStep step = new LabStep();
        step.setLabId(targetLabId);
        step.setStepNo(source.getStepNo());
        step.setTitle(source.getTitle());

        step.setQuestionType(source.getQuestionType());
        step.setQuestionSnapshotJson(source.getQuestionSnapshotJson());
        step.setEditorLanguage(source.getEditorLanguage());
        step.setExperimentItemType(source.getExperimentItemType());

        step.setContent(source.getContent());
        step.setAnswerConfigJson(source.getAnswerConfigJson());
        step.setExperimentItemAnswer(source.getExperimentItemAnswer());
        step.setScoringConfigJson(source.getScoringConfigJson());

        step.setStepScore(source.getStepScore());
        step.setState(source.getState());
        step.setQuestionId(source.getQuestionId());
        step.setAllowPaste(source.isAllowPaste());
        return step;
    }

    /**
     * 复制源实验的全部填空题可接受答案，外键改指到新实验与新题项，创建人记为当前教师。
     *
     * <p>源题项已删除而答案残留的历史脏数据（映射中查不到新题项）直接跳过，不阻断复制。</p>
     */
    private void copyBlankOverrides(Long sourceLabId, Long targetLabId,
                                    Map<Long, Long> itemIdMapping, Long teacherId) {
        List<ExperimentBlankAnswerOverride> overrides =
                experimentBlankAnswerOverrideRepository.findByExperimentIdOrderByIdAsc(sourceLabId);
        for (ExperimentBlankAnswerOverride source : overrides) {
            Long targetItemId = itemIdMapping.get(source.getExperimentItemId());
            if (targetItemId == null) {
                continue;
            }
            ExperimentBlankAnswerOverride copy = new ExperimentBlankAnswerOverride();
            copy.setExperimentId(targetLabId);
            copy.setExperimentItemId(targetItemId);
            copy.setAcceptedAnswer(source.getAcceptedAnswer());
            copy.setNormalizedAnswer(source.getNormalizedAnswer());
            copy.setBlankAnswersJson(source.getBlankAnswersJson());
            copy.setCreatedBy(teacherId);
            experimentBlankAnswerOverrideRepository.save(copy);
        }
    }

    /**
     * 生成副本实验名称：优先使用请求值；缺省时为「源标题（副本）」，
     * 超长时先截断源标题再拼后缀，保证总长不超过 30（数据库列上限）。
     */
    private String resolveCopyTitle(String sourceTitle, String requestedTitle) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        String suffix = "（副本）";
        int maxBaseLength = 30 - suffix.length();
        String base = sourceTitle.length() > maxBaseLength
                ? sourceTitle.substring(0, maxBaseLength)
                : sourceTitle;
        return base + suffix;
    }

    @Transactional
    public Map<String, Object> updateLab(CurrentUser currentUser, Long labId, LabRequests.UpdateLabRequest request) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        // 发布校验必须先于 applyLab：若先写实体再抛异常，同一持久化上下文会残留被污染的 PUBLISHED 状态
        if (parseStatus(request.status()) == ActivityStatus.PUBLISHED) {
            assertLabPublishable(lab);
        }
        applyLab(lab, request.classId(), request.title(), request.description(), request.experimentContent(), request.experimentType(), request.status(), request.materialId(), request.scoreVisibilityMode(), request.summaryRequired());
        return toTeacherLabView(labRepository.save(lab));
    }

    @Transactional
    public Map<String, Object> changeLabStatus(CurrentUser currentUser, Long labId, LabRequests.ChangeLabStatusRequest request) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        ActivityStatus nextStatus = parseStatus(request.status());
        if (nextStatus == ActivityStatus.PUBLISHED) {
            assertLabPublishable(lab);
        }
        lab.setStatus(nextStatus);
        return toTeacherLabView(labRepository.save(lab));
    }

    @Transactional
    public Map<String, Object> releaseScores(CurrentUser currentUser, Long labId) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        lab.setScoreReleased(true);
        labRepository.save(lab);
        return Map.of("released", true, "scoreVisibilityMode", lab.getScoreVisibilityMode().name());
    }

    @Transactional(readOnly = true)
    /**
     * 学生查看实验详情。
     *
     * <p>返回结果会把实验基础信息、学生当前提交状态、步骤列表、已保存答案以及分数可见性一起组装好，
     * 供前端一次性渲染页面。</p>
     */
    public Map<String, Object> getStudentLabDetail(CurrentUser currentUser, Long labId) {
        requireStudent(currentUser);
        Lab lab = accessiblePublishedLab(currentUser, labId);
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, currentUser.id()).orElse(null);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(labId);
        boolean scoreVisible = scoreVisible(lab, submission);
        List<Map<String, Object>> items = steps.stream().map(step -> toStudentStepView(step, submission, scoreVisible)).toList();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", lab.getId());
        detail.put("title", lab.getTitle());
        detail.put("description", defaultString(lab.getDescription()));
        detail.put("experimentContent", defaultString(lab.getExperimentContent()));
        detail.put("experimentType", lab.getExperimentType());
        detail.put("materialId", lab.getMaterialId());
        appendMaterialFields(detail, lab.getMaterialId());
        detail.put("status", lab.getStatus().name());
        detail.put("classId", lab.getClassId());
        detail.put("className", resolveClassName(lab.getClassId()));
        detail.put("summaryRequired", lab.isSummaryRequired());
        detail.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        detail.put("totalScore", submission == null || !scoreVisible ? null : submission.getTotalScore());
        detail.put("teacherComment", submission == null || !scoreVisible ? null : submission.getTeacherComment());
        detail.put("summaryText", submission == null ? "" : defaultString(submission.getSummaryText()));
        detail.put("items", items);
        detail.put("steps", items);
        return detail;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherSteps(CurrentUser currentUser, Long labId) {
        requireTeacher(currentUser);
        ownedLab(currentUser, labId);
        return labStepRepository.findByLabIdOrderByStepNoAsc(labId).stream().map(this::toStepView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBlankItems(CurrentUser currentUser, Long labId) {
        requireTeacher(currentUser);
        ownedLab(currentUser, labId);
        return labStepRepository.findByLabIdOrderByStepNoAsc(labId).stream()
                .filter(this::isFillBlankStep)
                .map(this::toBlankItemView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBlankItemAnswerDistribution(CurrentUser currentUser, Long labId, Long itemId) {
        requireTeacher(currentUser);
        ownedLab(currentUser, labId);
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
                buckets.put(normalizedAnswer, new DistributionBucket(defaultString(answer.getAnswerText()).trim(), normalizedAnswer, 1L));
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
        requireTeacher(currentUser);
        ownedLab(currentUser, labId);
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
                applyScoringResult(answer, labScoringSupport.score(step, answer));
                labStepAnswerRepository.save(answer);
                regradedCount++;
                if (submission.getSubmitStatus() == SubmissionStatus.SAVED) {
                    refreshSavedSubmissionAfterScoring(submission);
                } else {
                    refreshSubmissionAfterScoring(lab, submission);
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
        requireTeacher(currentUser);
        ownedLab(currentUser, labId);
        LabStep step = new LabStep();
        applyStep(step, labId, request.stepNo(), request.title(), request.questionType(), request.content(), request.answerConfigJson(), request.stepScore(), request.allowPaste());
        return toStepView(labStepRepository.save(step));
    }

    @Transactional
    public Map<String, Object> updateStep(CurrentUser currentUser, Long stepId, LabRequests.UpdateStepRequest request) {
        requireTeacher(currentUser);
        LabStep step = labStepRepository.findById(stepId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        ownedLab(currentUser, step.getLabId());
        applyStep(step, step.getLabId(), request.stepNo(), request.title(), request.questionType(), request.content(), request.answerConfigJson(), request.stepScore(), request.allowPaste());
        return toStepView(labStepRepository.save(step));
    }

    @Transactional
    public Map<String, Object> deleteStep(CurrentUser currentUser, Long stepId) {
        requireTeacher(currentUser);
        LabStep step = labStepRepository.findById(stepId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        ownedLab(currentUser, step.getLabId());
        labStepRepository.delete(step);
        return Map.of("deleted", true, "id", stepId);
    }

    /**
     * 教师端实验报告队列（分页 + 筛选）。
     *
     * <p>与改造前的区别：以前是把教师名下全部提交拉进内存再做 keyword/status 过滤，
     * 现在把过滤与分页都下沉到数据库，只把当前页的提交加载进内存。</p>
     *
     * <p>维度解析采用混合策略：教师所属实验、关键词命中的实验 / 班级 / 学生都在内存里解析
     * （基数小），提交表上只保留 {@code lab_id IN} + {@code student_id IN} + {@code submit_status =} 这类
     * 可走索引的谓词，避免跨表子查询在 MySQL / H2 双方言下出现差异。</p>
     */
    @Transactional(readOnly = true)
    public PagedResult<Map<String, Object>> listTeacherReports(CurrentUser currentUser,
                                                               LabRequests.TeacherLabReportQuery query) {
        requireTeacher(currentUser);

        // 1. 归一化并校验分页参数：空值取默认值，越界按统一错误码拒绝
        int page = query == null || query.page() == null ? 1 : query.page();
        int pageSize = query == null || query.pageSize() == null ? DEFAULT_REPORT_PAGE_SIZE : query.pageSize();
        if (page < 1 || pageSize < 1 || pageSize > MAX_REPORT_PAGE_SIZE) {
            throw new BusinessException(40000, "参数不合法");
        }

        // 2. 教师数据域：只允许查询自己创建的实验下的提交
        List<Lab> ownedLabs = labRepository.findByCreatedByOrderByIdDesc(currentUser.id());
        if (ownedLabs.isEmpty()) {
            return new PagedResult<>(List.of(), page, pageSize, 0);
        }

        // 3. 单实验筛选：与教师数据域取交集，越权或不存在时直接返回空页
        List<Long> scopeLabIds = ownedLabs.stream().map(Lab::getId).toList();
        if (query != null && query.labId() != null) {
            if (!scopeLabIds.contains(query.labId())) {
                return new PagedResult<>(List.of(), page, pageSize, 0);
            }
            scopeLabIds = List.of(query.labId());
        }

        // 4. 状态筛选：非法状态按"等值匹配不到"处理，即空结果而不是报错（与改造前一致）
        SubmissionStatus statusFilter = null;
        String rawStatus = query == null ? null : query.status();
        if (!isBlank(rawStatus)) {
            try {
                statusFilter = SubmissionStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return new PagedResult<>(List.of(), page, pageSize, 0);
            }
        }

        // 5. 行为风险等级筛选：内存命中集模式（D10）。命中集基于已落库 metrics，
        //    当前页行的懒算在装配阶段触发（toReportListViews），筛选不等待懒算
        String rawRiskLevel = query == null ? null : query.riskLevel();
        List<Long> riskHitSubmissionIds = null;
        if (!isBlank(rawRiskLevel)) {
            String normalizedRiskLevel = rawRiskLevel.trim().toUpperCase(Locale.ROOT);
            switch (normalizedRiskLevel) {
                case LabSubmissionMetrics.RISK_LOW, LabSubmissionMetrics.RISK_MEDIUM, LabSubmissionMetrics.RISK_HIGH ->
                        riskHitSubmissionIds = labSubmissionMetricsRepository
                                .findByLabIdInAndRiskLevel(scopeLabIds, normalizedRiskLevel).stream()
                                .map(LabSubmissionMetrics::getSubmissionId)
                                .toList();
                case "NONE" -> {
                    // NONE = metrics 缺失 或 risk_flags 含 NO_BEHAVIOR_DATA（懒算会为无事件行建 metrics，D10）
                    Set<Long> withBehaviorMetrics = labSubmissionMetricsRepository.findByLabIdIn(scopeLabIds).stream()
                            .filter(metrics -> !parseStringList(metrics.getRiskFlagsJson())
                                    .contains(LabSubmissionMetrics.FLAG_NO_BEHAVIOR_DATA))
                            .map(LabSubmissionMetrics::getSubmissionId)
                            .collect(Collectors.toSet());
                    riskHitSubmissionIds = labSubmissionRepository.findByLabIdIn(scopeLabIds).stream()
                            .map(LabSubmission::getId)
                            .filter(id -> !withBehaviorMetrics.contains(id))
                            .toList();
                }
                // 非法值与 status 语义对齐：按"匹配不到"处理，空结果而不是报错
                default -> {
                    return new PagedResult<>(List.of(), page, pageSize, 0);
                }
            }
            if (riskHitSubmissionIds.isEmpty()) {
                return new PagedResult<>(List.of(), page, pageSize, 0);
            }
        }

        // 6. 关键词：实验名 / 班级名在内存解析，姓名 / 学号 / 用户名走一条用户库查询
        String keyword = query == null ? "" : normalize(query.keyword());
        boolean keywordActive = !keyword.isBlank();
        List<Long> labKeywordHitIds = List.of();
        List<Long> studentKeywordHitIds = List.of();
        if (keywordActive) {
            Map<Long, String> classNameById = loadClassNames(ownedLabs);
            Set<Long> scopeLabIdSet = new LinkedHashSet<>(scopeLabIds);
            labKeywordHitIds = ownedLabs.stream()
                    .filter(lab -> scopeLabIdSet.contains(lab.getId()))
                    .filter(lab -> normalize(lab.getTitle()).contains(keyword)
                            || normalize(classNameById.getOrDefault(lab.getClassId(), "")).contains(keyword))
                    .map(Lab::getId)
                    .toList();
            studentKeywordHitIds = sysUserRepository.findIdsByKeyword("%" + keyword + "%");
        }

        // 7. 拼装提交表谓词：关键词维度之间必须是 OR，不能折叠进 labId 的 AND
        List<Long> specLabIds = scopeLabIds;
        SubmissionStatus specStatus = statusFilter;
        List<Long> specLabHitIds = labKeywordHitIds;
        List<Long> specStudentHitIds = studentKeywordHitIds;
        List<Long> specRiskHitIds = riskHitSubmissionIds;
        Specification<LabSubmission> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("labId").in(specLabIds));
            if (specStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("submitStatus"), specStatus));
            }
            if (specRiskHitIds != null) {
                predicates.add(root.get("id").in(specRiskHitIds));
            }
            if (keywordActive) {
                Predicate keywordPredicate;
                if (!specLabHitIds.isEmpty() && !specStudentHitIds.isEmpty()) {
                    keywordPredicate = criteriaBuilder.or(
                            root.get("labId").in(specLabHitIds),
                            root.get("studentId").in(specStudentHitIds));
                } else if (!specLabHitIds.isEmpty()) {
                    keywordPredicate = root.get("labId").in(specLabHitIds);
                } else if (!specStudentHitIds.isEmpty()) {
                    keywordPredicate = root.get("studentId").in(specStudentHitIds);
                } else {
                    keywordPredicate = criteriaBuilder.disjunction();
                }
                predicates.add(keywordPredicate);
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // 8. 真分页查询 + 当前页批量映射（避免逐行查实验 / 学生 / 班级）
        Page<LabSubmission> pageResult = labSubmissionRepository.findAll(
                specification,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        return new PagedResult<>(toReportListViews(pageResult.getContent()), page, pageSize, pageResult.getTotalElements());
    }

    /**
     * 教师端实验报告导航队列（全量、轻量）。
     *
     * <p>报告详情页与批量批改页需要基于全部报告做"上一份 / 下一份"跳转，
     * 分页接口会截断队列，因此单独提供无分页的全量列表，行结构与分页接口完全一致。</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherReportQueue(CurrentUser currentUser) {
        requireTeacher(currentUser);
        List<Long> ownedLabIds = labRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream()
                .map(Lab::getId)
                .toList();
        if (ownedLabIds.isEmpty()) {
            return List.of();
        }
        List<LabSubmission> submissions = labSubmissionRepository.findByLabIdIn(ownedLabIds).stream()
                .sorted(Comparator.comparing(LabSubmission::getId).reversed())
                .toList();
        return toReportListViews(submissions);
    }

    /**
     * 教师端实验报告全局统计（不受列表筛选影响）。
     *
     * <p>队列页头部的"报告总量 / 待批改 / 已批改"三张卡需要全量口径，
     * 分页结果只能看到当前页，因此单独用一条分组计数查询提供。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherReportStats(CurrentUser currentUser) {
        requireTeacher(currentUser);
        List<Long> ownedLabIds = labRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream()
                .map(Lab::getId)
                .toList();
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = 0L;
        long submitted = 0L;
        long graded = 0L;
        if (!ownedLabIds.isEmpty()) {
            for (Object[] row : labSubmissionRepository.countGroupByStatus(ownedLabIds)) {
                SubmissionStatus status = (SubmissionStatus) row[0];
                long count = ((Number) row[1]).longValue();
                // total 汇总全部状态（含 SAVED），与改造前 rows.length 的口径保持一致
                total += count;
                if (status == SubmissionStatus.SUBMITTED) {
                    submitted += count;
                } else if (status == SubmissionStatus.GRADED) {
                    graded += count;
                }
            }
        }
        stats.put("total", total);
        stats.put("submitted", submitted);
        stats.put("graded", graded);
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherReportDetail(CurrentUser currentUser, Long reportId) {
        requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId).orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = ownedLab(currentUser, submission.getLabId());
        SysUser student = sysUserRepository.findById(submission.getStudentId()).orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", submission.getId());
        detail.put("labId", lab.getId());
        detail.put("labTitle", lab.getTitle());
        detail.put("classId", lab.getClassId());
        detail.put("className", resolveClassName(lab.getClassId()));
        detail.put("studentId", student.getId());
        detail.put("studentName", student.getDisplayName());
        detail.put("studentUsername", student.getUsername());
        detail.put("studentNo", student.getUsername());
        detail.put("submitStatus", submission.getSubmitStatus().name());
        detail.put("totalScore", submission.getTotalScore());
        detail.put("summaryRequired", lab.isSummaryRequired());
        detail.put("summaryText", defaultString(submission.getSummaryText()));
        detail.put("teacherComment", submission.getTeacherComment());
        detail.put("submittedAt", submission.getSubmittedAt());
        detail.put("gradedAt", submission.getGradedAt());
        detail.put("items", steps.stream().map(step -> toTeacherReportItem(step, answers)).toList());
        return detail;
    }

    /**
     * 教师查看单份报告的行为详情（方案 §5.3 / D11）。
     *
     * <p>返回 metrics 全量（摘要字段 + stepDwellMs / firstStartedAt / lastEventAt / computedAt）
     * 与按 {@code (occurred_at, id)} 升序的行为时间线；时间线上限 500 条，
     * {@code timelineTotal} 返回真实总数供前端提示截断。</p>
     *
     * <p>读取前对该行触发一次懒算：提交后迟到的尾部事件（LAB_LEAVE、末段心跳）在此刻补齐，
     * 保证详情页看到的是最新口径。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherReportBehavior(CurrentUser currentUser, Long reportId) {
        requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        ownedLab(currentUser, submission.getLabId());

        LabSubmissionMetrics metrics = ensureMetricsFresh(List.of(submission)).get(submission.getId());
        Page<BehaviorEvent> timelinePage = behaviorEventRepository.findByBizTypeAndLabIdAndStudentId(
                BehaviorEvent.BIZ_TYPE_LAB,
                submission.getLabId(),
                submission.getStudentId(),
                PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "occurredAt", "id")));

        Map<String, Object> result = new LinkedHashMap<>();
        if (metrics == null) {
            // 防御性兜底：metrics 写入失败时时间线仍返回（§5.3）
            result.put("metrics", null);
        } else {
            Map<String, Object> metricsView = new LinkedHashMap<>(toBehaviorView(metrics, submission));
            metricsView.put("stepDwellMs", parseStepDwell(metrics.getStepDwellJson()));
            metricsView.put("firstStartedAt", metrics.getFirstStartedAt());
            metricsView.put("lastEventAt", metrics.getLastEventAt());
            metricsView.put("computedAt", metrics.getComputedAt());
            result.put("metrics", metricsView);
        }
        result.put("timeline", timelinePage.getContent().stream()
                .map(this::toBehaviorTimelineItem)
                .toList());
        result.put("timelineTotal", timelinePage.getTotalElements());
        return result;
    }

    /** 时间线单条视图：eventType / occurredAt / stepId / payload（JSON 还原为对象）。 */
    private Map<String, Object> toBehaviorTimelineItem(BehaviorEvent event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("eventType", event.getEventType());
        item.put("occurredAt", event.getOccurredAt());
        item.put("stepId", event.getStepId());
        item.put("payload", parsePayloadMap(event.getPayloadJson()));
        return item;
    }

    /** payload_json → Map；空或解析失败按空 Map 处理（防御）。 */
    private Map<String, Object> parsePayloadMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /** step_dwell_json → Map<String, Long>（key 为 stepId 字符串，契约 {"123": 45000}）。 */
    private Map<String, Long> parseStepDwell(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            Map<String, Long> dwell = new LinkedHashMap<>();
            raw.forEach((stepId, value) -> {
                if (value instanceof Number number) {
                    dwell.put(stepId, number.longValue());
                }
            });
            return dwell;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherLabReportView(CurrentUser currentUser, Long labId, Long studentId) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        LabSubmission submission = labSubmissionRepository.findFirstByLabIdAndStudentIdOrderByIdDesc(labId, studentId)
                .orElseThrow(() -> new BusinessException(40400, "未找到该学生的实验提交记录"));
        SysUser student = sysUserRepository.findById(studentId).orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(labId);
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("labId", lab.getId());
        view.put("studentId", student.getId());
        view.put("courseName", "软件设计与体系结构");
        view.put("reportTitle", "南昌航空大学实验报告");
        view.put("reportDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")));
        view.put("className", resolveClassName(lab.getClassId()));
        view.put("studentNo", student.getUsername());
        view.put("studentName", student.getDisplayName());
        view.put("labTitle", lab.getTitle());
        view.put("purpose", defaultString(lab.getDescription()));
        view.put("experimentContent", defaultString(lab.getExperimentContent()));
        view.put("totalScore", submission.getSubmitStatus() == SubmissionStatus.GRADED ? submission.getTotalScore() : null);
        view.put("teacherComment", defaultString(submission.getTeacherComment()));
        view.put("summaryText", defaultString(submission.getSummaryText()));
        view.put("steps", steps.stream().map(step -> toTeacherReportViewStep(step, answers)).toList());
        return view;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSubmissionOverview(CurrentUser currentUser, Long labId) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(labId);
        List<ClassMember> members = classMemberRepository.findByClassId(lab.getClassId());
        Map<Long, LabSubmission> submissionByStudentId = indexSubmissionsByStudentId(labSubmissionRepository.findByLabId(labId));
        // D15：提交总览与批改队列共用同一懒算 + 装配方法，数值口径防漂移
        Map<Long, LabSubmissionMetrics> behaviorBySubmissionId = ensureMetricsFresh(
                List.copyOf(submissionByStudentId.values()));

        List<Map<String, Object>> items = steps.stream().map(step -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepId", step.getId());
            item.put("stepNo", step.getStepNo());
            item.put("title", step.getTitle());
            return item;
        }).toList();

        List<Map<String, Object>> students = new ArrayList<>();
        for (ClassMember member : members) {
            SysUser student = sysUserRepository.findById(member.getStudentUserId()).orElse(null);
            if (student == null) {
                continue;
            }
            LabSubmission submission = submissionByStudentId.get(student.getId());
            List<LabStepAnswer> answers = submission == null
                    ? List.of()
                    : labStepAnswerRepository.findByLabSubmissionId(submission.getId());
            Map<Long, LabStepAnswer> answerByStepId = indexAnswersByStepId(answers);

            List<Map<String, Object>> cells = steps.stream()
                    .map(step -> buildOverviewCell(step.getId(), answerByStepId.get(step.getId())))
                    .toList();

            Map<String, Object> studentRow = new LinkedHashMap<>();
            studentRow.put("studentId", student.getId());
            studentRow.put("studentNo", student.getUsername());
            studentRow.put("studentName", student.getDisplayName());
            studentRow.put("submissionId", submission == null ? null : submission.getId());
            studentRow.put("submitStatus", submission == null ? null : submission.getSubmitStatus().name());
            studentRow.put("submittedAt", submission == null ? null : submission.getSubmittedAt());
            // 无提交的学生无行为数据，behavior 为 null（前端按 "-" 展示，区别于防御性兜底）
            studentRow.put("behavior", submission == null
                    ? null
                    : toBehaviorView(behaviorBySubmissionId.get(submission.getId()), submission));
            studentRow.put("cells", cells);
            students.add(studentRow);
        }
        students.sort(Comparator.comparing(row -> normalize(row.get("studentNo"))));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("labId", lab.getId());
        overview.put("labTitle", lab.getTitle());
        overview.put("classId", lab.getClassId());
        overview.put("items", items);
        overview.put("students", students);
        return overview;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentLabSubmissions(CurrentUser currentUser, Long studentId) {
        requireTeacher(currentUser);
        requireTeacherStudentAccess(currentUser, studentId);
        List<Long> ownedLabIds = labRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream()
                .map(Lab::getId)
                .toList();
        if (ownedLabIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ownedLabIdSet = new HashSet<>(ownedLabIds);
        return labSubmissionRepository.findByStudentId(studentId).stream()
                .filter(submission -> ownedLabIdSet.contains(submission.getLabId()))
                .sorted(Comparator.comparing(LabSubmission::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(submission -> toStudentLabSubmissionView(submission))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLabItemAnswers(CurrentUser currentUser, Long labId, Long itemId) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        LabStep step = labStepRepository.findById(itemId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        if (!Objects.equals(step.getLabId(), labId)) {
            throw new BusinessException(40000, "实验步骤不属于该实验");
        }

        List<ClassMember> members = classMemberRepository.findByClassId(lab.getClassId());
        List<LabSubmission> submissions = labSubmissionRepository.findByLabId(labId);
        Map<Long, LabSubmission> submissionByStudentId = indexSubmissionsByStudentId(submissions);
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
            row.put("answerText", answer == null ? "" : resolveStudentAnswerText(answer));
            row.put("images", answer == null ? List.of() : toAnswerImageViews(answer.getAnswerJson()));
            row.put("score", answer == null ? null : answer.getScore());
            row.put("teacherComment", answer == null ? null : answer.getTeacherComment());
            answers.add(row);
        }
        answers.sort(Comparator.comparing(row -> normalize(row.get("studentNo"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labId", lab.getId());
        result.put("labTitle", lab.getTitle());
        result.put("itemId", step.getId());
        result.put("stepNo", step.getStepNo());
        result.put("title", step.getTitle());
        result.put("content", defaultString(step.getContent()));
        result.put("answers", answers);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentLabs(CurrentUser currentUser) {
        requireStudent(currentUser);
        List<Long> classIds = classMemberRepository.findByStudentUserId(currentUser.id()).stream()
                .map(ClassMember::getClassId)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return labRepository.findByClassIdInAndStatusOrderByIdDesc(classIds, ActivityStatus.PUBLISHED).stream()
                .map(lab -> toStudentLabView(lab, currentUser.id()))
                .toList();
    }

    @Transactional
    /**
     * 学生保存单个步骤答案。
     *
     * <p>运行顺序是：校验实验与步骤 → 查找或创建实验提交记录 → 写入步骤答案 → 追加答案快照日志。</p>
     * <p>这也是你讲“数据库对应关系”时最适合举例的方法：
     * 一次保存动作会同时影响 experiment_submission、experiment_answer、lab_step_answer_log 三类数据。</p>
     */
    public Map<String, Object> saveAnswer(CurrentUser currentUser, Long labId, Long stepId, LabRequests.SaveStepAnswerRequest request) {
        requireStudent(currentUser);
        Lab lab = accessiblePublishedLab(currentUser, labId);
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能保存");
        }
        LabStep step = labStepRepository.findById(stepId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        if (!step.getLabId().equals(labId)) {
            throw new BusinessException(40000, "实验步骤不属于该实验");
        }
        LabSubmission submission = ensureEditableSubmission(currentUser, labId);
        LabStepAnswer answer = prepareStepAnswer(submission, step);
        String answerText = request.answerText() == null ? "" : request.answerText();
        String answerPayloadJson = request.answerPayloadJson();
        if (LabAnswerImageSupport.allowsImages(step.getQuestionType())) {
            LabAnswerImageSupport.TextAnswerPayload payload = LabAnswerImageSupport.parseTextPayload(objectMapper, answerPayloadJson);
            payload = new LabAnswerImageSupport.TextAnswerPayload(answerText, payload.images());
            LabAnswerImageSupport.validateImagesForSave(step.getQuestionType(), payload.images(), localFileStorageService, storageRoot);
            answerPayloadJson = LabAnswerImageSupport.canonicalizeTextPayload(objectMapper, answerText, payload);
        } else {
            LabAnswerImageSupport.TextAnswerPayload payload = LabAnswerImageSupport.parseTextPayload(objectMapper, answerPayloadJson);
            if (!payload.images().isEmpty()) {
                throw new BusinessException(40000, "当前题型不支持图片作答");
            }
        }
        answer.setAnswerText(answerText);
        answer.setAnswerJson(answerPayloadJson);
        LabStepAnswer saved = labStepAnswerRepository.saveAndFlush(answer);
        // 附加保存日志，支持回溯学生在作答过程中的输入快照。
        appendAnswerSnapshot(saved);
        return Map.of(
                "answerId", saved.getId(),
                "submissionStatus", submission.getSubmitStatus().name()
        );
    }

    @Transactional
    public Map<String, Object> uploadAnswerImage(CurrentUser currentUser,
                                                 Long labId,
                                                 Long stepId,
                                                 MultipartFile file) {
        requireStudent(currentUser);
        Lab lab = accessiblePublishedLab(currentUser, labId);
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能上传");
        }
        LabStep step = labStepRepository.findById(stepId).orElseThrow(() -> new BusinessException(40400, "实验步骤不存在"));
        if (!step.getLabId().equals(labId)) {
            throw new BusinessException(40000, "实验步骤不属于该实验");
        }
        if (!LabAnswerImageSupport.allowsImages(step.getQuestionType())) {
            throw new BusinessException(40000, "当前题型不支持图片作答");
        }
        LabSubmission submission = ensureEditableSubmission(currentUser, labId);
        LabStepAnswer answer = prepareStepAnswer(submission, step);
        LabAnswerImageSupport.TextAnswerPayload currentPayload = LabAnswerImageSupport.parseTextPayload(
                objectMapper,
                answer.getAnswerJson()
        );
        LabAnswerImageSupport.ensureUploadQuota(currentPayload);
        String contentType = file == null ? "" : defaultString(file.getContentType());
        long size = file == null ? 0L : file.getSize();
        String normalizedType = LabAnswerImageSupport.normalizeContentType(contentType);
        LabAnswerImageSupport.validateUpload(normalizedType, size);
        String folder = "lab-answers/" + LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String storedPath = localFileStorageService.store(folder, file);
        String originalFilename = file.getOriginalFilename();
        String displayName = originalFilename == null || originalFilename.isBlank()
                ? "image.bin"
                : Path.of(originalFilename).getFileName().toString();
        long storedSize;
        try {
            storedSize = Files.size(storageRoot.resolve(storedPath).normalize());
        } catch (Exception ex) {
            throw new BusinessException(50000, "文件保存失败");
        }
        LabAnswerImageSupport.ImageMeta uploadedImage = new LabAnswerImageSupport.ImageMeta(
                storedPath,
                displayName,
                normalizedType,
                storedSize
        );
        String answerText = defaultString(answer.getAnswerText());
        LabAnswerImageSupport.TextAnswerPayload updatedPayload = LabAnswerImageSupport.appendImage(currentPayload, uploadedImage);
        answer.setAnswerText(answerText);
        answer.setAnswerJson(LabAnswerImageSupport.canonicalizeTextPayload(objectMapper, answerText, updatedPayload));
        LabStepAnswer savedAnswer = labStepAnswerRepository.save(answer);
        // D17③：图片上传同样追加快照，与 saveAnswer 保持"每次服务端确认的答案变更"同一口径，
        // 覆盖"先答文字、最后统一传图"的真实场景，保存次数不再失真
        appendAnswerSnapshot(savedAnswer);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", storedPath);
        result.put("name", displayName);
        result.put("contentType", normalizedType);
        result.put("size", storedSize);
        return result;
    }

    @Transactional(readOnly = true)
    public PreviewAnswerImageResult previewAnswerImage(CurrentUser currentUser, String path) {
        String normalizedPath = LabAnswerImageSupport.normalizeRelativePath(path);
        if (!normalizedPath.matches("^lab-answers/\\d{4}-\\d{2}-\\d{2}/.+")) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        Path targetPath = storageRoot.resolve(normalizedPath).normalize();
        if (!targetPath.startsWith(storageRoot)) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        LabStepAnswer answer = findAnswerOwningImagePath(normalizedPath);
        LabSubmission submission = labSubmissionRepository.findById(answer.getLabSubmissionId())
                .orElseThrow(() -> new BusinessException(40400, "实验提交不存在"));
        Lab lab = labRepository.findById(submission.getLabId()).orElseThrow(() -> new BusinessException(40400, "实验不存在"));
        requireAnswerImageAccess(currentUser, submission, lab);
        byte[] bytes = localFileStorageService.read(normalizedPath);
        String contentType = resolveImageContentType(answer.getAnswerJson(), normalizedPath);
        return new PreviewAnswerImageResult(contentType, bytes);
    }

    private void appendAnswerSnapshot(LabStepAnswer answer) {
        LabStepAnswerLog log = new LabStepAnswerLog();
        log.setLabStepAnswerId(answer.getId());
        log.setContent(answer.getAnswerText());
        log.setFillTime(OffsetDateTime.now());
        labStepAnswerLogRepository.save(log);
    }

    @Transactional
    /**
     * 学生正式提交实验。
     *
     * <p>这一步会把“已保存草稿”推进到“已提交待评分/已评分”阶段：
     * service 会遍历当前实验的全部步骤，对每个已作答步骤调用 {@link LabScoringSupport}，
     * 再把自动评分结果回填到答案表中，最后刷新整份实验提交的总分和状态。</p>
     */
    public Map<String, Object> submitLab(CurrentUser currentUser, Long labId, LabRequests.SubmitLabRequest request) {
        requireStudent(currentUser);
        Lab lab = accessiblePublishedLab(currentUser, labId);
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能提交");
        }
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40000, "请先保存实验答案"));
        submission.setSummaryText(request == null ? "" : defaultString(request.summaryText()));
        if (lab.isSummaryRequired() && submission.getSummaryText().isBlank()) {
            throw new BusinessException(40000, "实验小结不能为空");
        }
        submission.setSubmitStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(OffsetDateTime.now());
        labSubmissionRepository.save(submission);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(labId);
        // 自动评分发生在正式提交阶段，而不是每次暂存时都触发。
        for (LabStep step : steps) {
            LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), step.getId()).orElse(null);
            if (answer == null) {
                continue;
            }
            ScoringResult result = labScoringSupport.score(step, answer);
            applyScoringResult(answer, result);
            labStepAnswerRepository.save(answer);
        }
        refreshSubmissionAfterScoring(lab, submission);
        // 行为指标联动（D8 时机①）：提交成功事务内全量算一次。
        // 提交瞬间前端缓冲中的尾部事件（LAB_LEAVE、末段心跳）可能尚未到达，
        // 本次以已到事件为准，迟到事件由教师首读的懒算兜底重算（时机②）
        labBehaviorMetricsService.computeForSubmission(submission);
        return Map.of(
                "submissionId", submission.getId(),
                "status", submission.getSubmitStatus().name()
        );
    }

    @Transactional
    /**
     * 教师整份批改实验报告。
     *
     * <p>这里适合解释“教师评分为什么不直接改 submission 总分”：
     * 系统先逐条更新每一步答案的得分来源与评语，再汇总总分，最后同步成绩记录。</p>
     */
    public Map<String, Object> gradeReport(CurrentUser currentUser, Long reportId, LabRequests.GradeLabReportRequest request) {
        requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId).orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = ownedLab(currentUser, submission.getLabId());
        List<LabStepAnswer> submissionAnswers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        List<Long> answeredAnswerIds = submissionAnswers.stream()
                .map(LabStepAnswer::getId)
                .filter(Objects::nonNull)
                .toList();
        HashSet<Long> requestedAnswerIds = request.items().stream()
                .map(LabRequests.GradeItem::answerId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!requestedAnswerIds.containsAll(answeredAnswerIds)) {
            throw new BusinessException(40000, "实验报告评分未完成，存在未覆盖的已答题项");
        }
        double totalScore = 0D;
        for (LabRequests.GradeItem item : request.items()) {
            LabStepAnswer answer = labStepAnswerRepository.findById(item.answerId()).orElseThrow(() -> new BusinessException(40400, "实验答案不存在"));
            if (!answer.getLabSubmissionId().equals(submission.getId())) {
                throw new BusinessException(40000, "评分项不属于当前实验报告");
            }
            answer.setScore(item.score());
            answer.setScoreSource("TEACHER");
            answer.setTeacherComment(defaultString(item.teacherComment()));
            labStepAnswerRepository.save(answer);
            totalScore += item.score();
        }
        submission.setTotalScore(totalScore);
        submission.setTeacherComment(defaultString(request.teacherComment()));
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(OffsetDateTime.now());
        labSubmissionRepository.save(submission);
        upsertScoreRecord(lab, submission, totalScore);
        return Map.of(
                "graded", true,
                "totalScore", totalScore,
                "labId", lab.getId()
        );
    }

    @Transactional
    /**
     * 教师确认某一步骤分数。
     *
     * <p>如果接受系统推荐分，则 scoreSource 会标记为 AUTO_ACCEPTED；
     * 如果教师手工给分，则标记为 TEACHER。随后重新刷新整份提交的评分状态。</p>
     */
    public Map<String, Object> confirmStepScore(CurrentUser currentUser, Long reportId, LabRequests.ConfirmStepScoreRequest request) {
        requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = ownedLab(currentUser, submission.getLabId());
        LabStepAnswer answer = labStepAnswerRepository.findById(request.answerId())
                .orElseThrow(() -> new BusinessException(40400, "实验答案不存在"));
        if (!Objects.equals(answer.getLabSubmissionId(), submission.getId())) {
            throw new BusinessException(40000, "评分项不属于当前实验报告");
        }
        if (request.acceptSuggested()) {
            if (answer.getSuggestedScore() == null) {
                throw new BusinessException(40000, "当前步骤没有可采纳的推荐分");
            }
            answer.setScore(answer.getSuggestedScore());
            answer.setScoreSource("AUTO_ACCEPTED");
        } else {
            if (request.score() == null) {
                throw new BusinessException(40000, "步骤分数不能为空");
            }
            answer.setScore(request.score());
            answer.setScoreSource("TEACHER");
        }
        answer.setTeacherComment(defaultString(request.teacherComment()));
        labStepAnswerRepository.save(answer);
        refreshSubmissionAfterScoring(lab, submission);
        return Map.of(
                "answerId", answer.getId(),
                "score", answer.getScore(),
                "submissionStatus", submission.getSubmitStatus().name()
        );
    }

    @Transactional
    /**
     * 教师将单条已提交或已批改实验报告打回至可编辑草稿态。
     *
     * <p>保留学生作答内容，清除提交时间与自动/逐步确认/终评的评分痕迹，使学生可再次保存并提交。
     * {@code GRADED} 打回时额外删除 {@code score_record}。</p>
     */
    public Map<String, Object> returnReport(CurrentUser currentUser, Long reportId) {
        requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = ownedLab(currentUser, submission.getLabId());
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能打回");
        }
        if (lab.getStatus() == ActivityStatus.DRAFT) {
            throw new BusinessException(40000, "实验未发布，不能打回");
        }
        SubmissionStatus status = submission.getSubmitStatus();
        if (status != SubmissionStatus.SUBMITTED && status != SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "仅已提交或已批改的报告可打回");
        }
        boolean wasGraded = status == SubmissionStatus.GRADED;

        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        for (LabStepAnswer answer : answers) {
            answer.setAutoScore(null);
            answer.setSuggestedScore(null);
            answer.setScore(null);
            answer.setScoreSource("TEACHER");
            answer.setAutoJudgeDetail(null);
            answer.setTeacherComment("");
            answer.setAcceptedAutoScore(false);
        }
        if (!answers.isEmpty()) {
            labStepAnswerRepository.saveAll(answers);
        }

        if (wasGraded) {
            scoreRecordRepository
                    .findByBusinessTypeAndBusinessIdAndStudentId(
                            BusinessType.LAB, lab.getId(), submission.getStudentId())
                    .ifPresent(scoreRecordRepository::delete);
        }

        submission.setSubmittedAt(null);
        submission.setTeacherComment("");
        refreshSavedSubmissionAfterScoring(submission);

        return Map.of(
                "submissionId", submission.getId(),
                "status", submission.getSubmitStatus().name(),
                "labId", lab.getId(),
                "studentId", submission.getStudentId()
        );
    }

    private static final String LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE = "实验尚未配置题项，无法发布";

    private void assertLabPublishable(Lab lab) {
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        if (steps.isEmpty()) {
            throw new BusinessException(40000, LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE);
        }
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

    private void requireAnswerImageAccess(CurrentUser currentUser, LabSubmission submission, Lab lab) {
        if (currentUser.role() == UserRole.STUDENT) {
            if (!Objects.equals(submission.getStudentId(), currentUser.id())) {
                throw new BusinessException(40300, "无权限访问该图片");
            }
            return;
        }
        if (currentUser.role() == UserRole.TEACHER) {
            ownedLab(currentUser, lab.getId());
            return;
        }
        throw new BusinessException(40300, "无权限访问该图片");
    }

    private LabSubmission ensureEditableSubmission(CurrentUser currentUser, Long labId) {
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, currentUser.id()).orElseGet(() -> {
            LabSubmission created = new LabSubmission();
            created.setLabId(labId);
            created.setStudentId(currentUser.id());
            created.setSubmitStatus(SubmissionStatus.SAVED);
            created.setPlagiarismRate(0D);
            created.setTotalScore(0D);
            return labSubmissionRepository.save(created);
        });
        if (submission.getSubmitStatus() == SubmissionStatus.SUBMITTED || submission.getSubmitStatus() == SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "实验已提交，不能继续修改");
        }
        submission.setSubmitStatus(SubmissionStatus.SAVED);
        return labSubmissionRepository.save(submission);
    }

    private LabStepAnswer prepareStepAnswer(LabSubmission submission, LabStep step) {
        LabStepAnswer answer = labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), step.getId()).orElseGet(LabStepAnswer::new);
        answer.setLabSubmissionId(submission.getId());
        answer.setLabStepId(step.getId());
        answer.setQuestionId(step.getQuestionId());
        answer.setQuestionSnapshotJson(defaultString(step.getQuestionSnapshotJson()));
        answer.setEditorLanguage(defaultString(step.getEditorLanguage()));
        return answer;
    }

    private LabStepAnswer findAnswerOwningImagePath(String normalizedPath) {
        List<LabStepAnswer> candidates = labStepAnswerRepository.findByAnswerJsonContaining(normalizedPath);
        for (LabStepAnswer candidate : candidates) {
            if (LabAnswerImageSupport.pathMatchesStoredImage(candidate.getAnswerJson(), normalizedPath, objectMapper)) {
                return candidate;
            }
        }
        throw new BusinessException(40400, "图片不存在");
    }

    private String resolveImageContentType(String answerJson, String normalizedPath) {
        return LabAnswerImageSupport.extractImages(answerJson, objectMapper).stream()
                .filter(image -> LabAnswerImageSupport.normalizeRelativePath(image.path()).equals(normalizedPath))
                .map(LabAnswerImageSupport.ImageMeta::contentType)
                .filter(contentType -> contentType != null && !contentType.isBlank())
                .findFirst()
                .orElse("application/octet-stream");
    }

    private List<Map<String, Object>> toAnswerImageViews(String answerJson) {
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

    private Lab accessiblePublishedLab(CurrentUser currentUser, Long labId) {
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

    private Lab ownedLab(CurrentUser currentUser, Long labId) {
        Lab lab = labRepository.findByIdAndCreatedBy(labId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该实验"));
        classAccessService.assertTeacherAssignedToClass(currentUser, lab.getClassId());
        return lab;
    }

    private void requireTeacherStudentAccess(CurrentUser currentUser, Long studentId) {
        List<Long> teacherClassIds = classAccessService.listAssignedClasses(currentUser).stream()
                .map(ClassRoom::getId)
                .toList();
        if (teacherClassIds.isEmpty()) {
            throw new BusinessException(40300, "无权限访问该学生");
        }
        boolean enrolled = classMemberRepository.findByClassIdIn(teacherClassIds).stream()
                .anyMatch(member -> Objects.equals(member.getStudentUserId(), studentId));
        if (!enrolled) {
            throw new BusinessException(40300, "无权限访问该学生");
        }
    }

    private Map<Long, LabSubmission> indexSubmissionsByStudentId(List<LabSubmission> submissions) {
        Map<Long, LabSubmission> indexed = new LinkedHashMap<>();
        for (LabSubmission submission : submissions) {
            indexed.putIfAbsent(submission.getStudentId(), submission);
        }
        return indexed;
    }

    private Map<Long, LabStepAnswer> indexAnswersByStepId(List<LabStepAnswer> answers) {
        Map<Long, LabStepAnswer> indexed = new LinkedHashMap<>();
        for (LabStepAnswer answer : answers) {
            indexed.putIfAbsent(answer.getLabStepId(), answer);
        }
        return indexed;
    }

    private Map<Long, LabStepAnswer> indexAnswersBySubmissionId(List<LabStepAnswer> answers) {
        Map<Long, LabStepAnswer> indexed = new LinkedHashMap<>();
        for (LabStepAnswer answer : answers) {
            indexed.putIfAbsent(answer.getLabSubmissionId(), answer);
        }
        return indexed;
    }

    private Map<String, Object> buildOverviewCell(Long stepId, LabStepAnswer answer) {
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("stepId", stepId);
        if (answer == null) {
            cell.put("answered", false);
            cell.put("hasText", false);
            cell.put("imageCount", 0);
            return cell;
        }
        String answerText = defaultString(answer.getAnswerText());
        boolean hasText = !answerText.isBlank();
        int imageCount = LabAnswerImageSupport.extractImages(answer.getAnswerJson(), objectMapper).size();
        cell.put("answered", hasText || imageCount > 0);
        cell.put("hasText", hasText);
        cell.put("imageCount", imageCount);
        return cell;
    }

    private Map<String, Object> toStudentLabSubmissionView(LabSubmission submission) {
        Lab lab = labRepository.findById(submission.getLabId()).orElse(null);
        if (lab == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("submissionId", submission.getId());
        view.put("labId", lab.getId());
        view.put("labTitle", lab.getTitle());
        view.put("classId", lab.getClassId());
        view.put("className", resolveClassName(lab.getClassId()));
        view.put("submitStatus", submission.getSubmitStatus().name());
        view.put("totalScore", submission.getTotalScore());
        view.put("submittedAt", submission.getSubmittedAt());
        view.put("gradedAt", submission.getGradedAt());
        return view;
    }

    private ActivityStatus parseStatus(String value) {
        return value == null || value.isBlank() ? ActivityStatus.DRAFT : ActivityStatus.valueOf(value);
    }

    private void applyLab(Lab lab,
                          Long classId,
                          String title,
                          String description,
                          String experimentContent,
                          Integer experimentType,
                          String status,
                          Long materialId,
                          ScoreVisibilityMode scoreVisibilityMode,
                          Boolean summaryRequired) {
        lab.setTitle(title);
        lab.setDescription(defaultString(description));
        lab.setExperimentContent(defaultString(experimentContent));
        lab.setExperimentType(experimentType == null || experimentType <= 0 ? 1 : experimentType);
        lab.setClassId(classId);
        lab.setMaterialId(materialId);
        lab.setStatus(parseStatus(status));
        lab.setScoreVisibilityMode(scoreVisibilityMode == null ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM : scoreVisibilityMode);
        lab.setSummaryRequired(Boolean.TRUE.equals(summaryRequired));
    }

    private Map<String, Object> toTeacherLabView(Lab lab) {
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        ClassRoom classRoom = classRoomRepository.findById(lab.getClassId()).orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", lab.getId());
        view.put("title", lab.getTitle());
        view.put("description", defaultString(lab.getDescription()));
        view.put("experimentContent", defaultString(lab.getExperimentContent()));
        view.put("experimentType", lab.getExperimentType());
        view.put("materialId", lab.getMaterialId());
        appendMaterialFields(view, lab.getMaterialId());
        view.put("classId", lab.getClassId());
        view.put("className", classRoom == null ? "" : classRoom.getName());
        view.put("status", lab.getStatus().name());
        view.put("scoreVisibilityMode", lab.getScoreVisibilityMode().name());
        view.put("summaryRequired", lab.isSummaryRequired());
        view.put("stepCount", steps.size());
        return view;
    }

    private Map<String, Object> toStudentLabView(Lab lab, Long studentId) {
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(lab.getId(), studentId).orElse(null);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", lab.getId());
        view.put("title", lab.getTitle());
        view.put("description", defaultString(lab.getDescription()));
        view.put("experimentContent", defaultString(lab.getExperimentContent()));
        view.put("experimentType", lab.getExperimentType());
        view.put("materialId", lab.getMaterialId());
        appendMaterialFields(view, lab.getMaterialId());
        view.put("status", lab.getStatus().name());
        view.put("classId", lab.getClassId());
        view.put("className", resolveClassName(lab.getClassId()));
        view.put("summaryRequired", lab.isSummaryRequired());
        view.put("stepCount", steps.size());
        view.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        boolean scoreVisible = scoreVisible(lab, submission);
        view.put("totalScore", submission == null || !scoreVisible ? null : submission.getTotalScore());
        view.put("teacherComment", submission == null || !scoreVisible ? null : submission.getTeacherComment());
        return view;
    }

    private void appendMaterialFields(Map<String, Object> target, Long materialId) {
        if (materialId == null) {
            target.put("materialTitle", "");
            target.put("materialFileName", "");
            target.put("materialDownloadUrl", "");
            return;
        }

        CourseMaterial material = courseMaterialRepository.findById(materialId).orElse(null);
        if (material == null) {
            target.put("materialTitle", "");
            target.put("materialFileName", "");
            target.put("materialDownloadUrl", "");
            return;
        }

        target.put("materialTitle", defaultString(material.getTitle()));
        target.put("materialFileName", defaultString(material.getFileName()));
        target.put("materialDownloadUrl", "/api/v1/materials/" + material.getId() + "/download");
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
                "answerConfigJson", defaultString(step.getAnswerConfigJson()),
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

    private Map<String, Object> toStudentStepView(LabStep step, LabSubmission submission, boolean scoreVisible) {
        LabStepAnswer answer = submission == null ? null : labStepAnswerRepository.findByLabSubmissionIdAndLabStepId(submission.getId(), step.getId()).orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", step.getId());
        item.put("stepNo", step.getStepNo());
        item.put("title", step.getTitle());
        item.put("questionType", step.getQuestionType());
        item.put("content", step.getContent());
        item.put("stepScore", step.getStepScore());
        item.put("allowPaste", step.isAllowPaste());
        item.put("answerText", resolveStudentAnswerText(answer));
        item.put("answerPayloadJson", answer == null ? null : answer.getAnswerJson());
        List<Map<String, Object>> options = projectStudentOptions(step);
        if (!options.isEmpty()) {
            item.put("options", options);
        }
        List<Map<String, Object>> blanks = projectStudentBlanks(step);
        if (!blanks.isEmpty()) {
            item.put("blanks", blanks);
        }
        if ("CODE".equalsIgnoreCase(defaultString(step.getQuestionType()))) {
            item.put("editorLanguage", defaultString(step.getEditorLanguage()));
        }
        item.put("score", answer == null || !scoreVisible ? null : answer.getScore());
        item.put("teacherComment", answer == null || !scoreVisible ? null : answer.getTeacherComment());
        return item;
    }

    private String resolveStudentAnswerText(LabStepAnswer answer) {
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

    private boolean scoreVisible(Lab lab, LabSubmission submission) {
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

    private void refreshSubmissionAfterScoring(Lab lab, LabSubmission submission) {
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
        }
        labSubmissionRepository.save(submission);
    }

    private void refreshSavedSubmissionAfterScoring(LabSubmission submission) {
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

    private void applyScoringResult(LabStepAnswer answer, ScoringResult result) {
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

    private void upsertScoreRecord(Lab lab, LabSubmission submission, Double totalScore) {
        ScoreRecord scoreRecord = scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentId(BusinessType.LAB, lab.getId(), submission.getStudentId())
                .orElseGet(ScoreRecord::new);
        scoreRecord.setBusinessType(BusinessType.LAB);
        scoreRecord.setBusinessId(lab.getId());
        scoreRecord.setStudentId(submission.getStudentId());
        scoreRecord.setClassId(lab.getClassId());
        scoreRecord.setScore(totalScore);
        scoreRecord.setGradedAt(OffsetDateTime.now());
        scoreRecordRepository.save(scoreRecord);
    }

    /**
     * 批量把提交行映射为教师报告列表视图。
     *
     * <p>分页接口与导航队列共用这一份实现，保证两条链路的行字段口径不会漂移；
     * 同时把"逐行查实验 / 学生 / 班级"收敛成每类对象各一次 {@code findAllById}，消除 N+1。</p>
     */
    private List<Map<String, Object>> toReportListViews(List<LabSubmission> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }
        Set<Long> labIds = submissions.stream()
                .map(LabSubmission::getLabId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> studentIds = submissions.stream()
                .map(LabSubmission::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Lab> labById = labRepository.findAllById(labIds).stream()
                .collect(Collectors.toMap(Lab::getId, Function.identity()));
        Map<Long, SysUser> studentById = sysUserRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, String> classNameById = loadClassNames(labById.values().stream().toList());
        // 行为指标懒算（D8 时机②）：当前页缺失或 watermark 落后的行批量重算，
        // 结果以内存对象直接装配（REQUIRES_NEW 内层已落库，外层 readOnly 事务不重查）
        Map<Long, LabSubmissionMetrics> behaviorBySubmissionId = ensureMetricsFresh(submissions);

        List<Map<String, Object>> items = new ArrayList<>(submissions.size());
        for (LabSubmission submission : submissions) {
            Lab lab = labById.get(submission.getLabId());
            SysUser student = studentById.get(submission.getStudentId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", submission.getId());
            item.put("labId", submission.getLabId());
            item.put("labTitle", lab == null ? "" : lab.getTitle());
            item.put("classId", lab == null ? null : lab.getClassId());
            item.put("className", lab == null ? "" : classNameById.getOrDefault(lab.getClassId(), ""));
            item.put("studentId", submission.getStudentId());
            item.put("studentName", student == null ? "" : student.getDisplayName());
            item.put("studentUsername", student == null ? "" : student.getUsername());
            item.put("studentNo", student == null ? "" : student.getUsername());
            item.put("submitStatus", submission.getSubmitStatus().name());
            item.put("totalScore", submission.getTotalScore());
            item.put("summaryRequired", lab != null && lab.isSummaryRequired());
            item.put("submittedAt", submission.getSubmittedAt());
            item.put("gradedAt", submission.getGradedAt());
            // D15：列表 / queue / overview / 详情共用同一装配方法，防止口径漂移
            item.put("behavior", toBehaviorView(behaviorBySubmissionId.get(submission.getId()), submission));
            items.add(item);
        }
        return items;
    }

    /**
     * 行为指标懒算入口（D8 时机②）。
     *
     * <p>判定陈旧的两类行：metrics 缺失，或 {@code event_watermark} 落后于该归属键的最新事件 id。
     * 陈旧行经 {@code refreshStaleMetrics}（REQUIRES_NEW）重算落库——本方法的调用方均为
     * {@code @Transactional(readOnly = true)}，直接写库会静默失败，且重算结果以内存对象
     * 返回装配（MySQL RR 快照下外层事务重查读不到内层新提交）。</p>
     *
     * @return submissionId → metrics（含新算与既有的合并视图）
     */
    private Map<Long, LabSubmissionMetrics> ensureMetricsFresh(List<LabSubmission> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(LabSubmission::getId).toList();
        Map<Long, LabSubmissionMetrics> result = new HashMap<>();
        labSubmissionMetricsRepository.findAllById(ids)
                .forEach(metrics -> result.put(metrics.getSubmissionId(), metrics));

        // 各归属键 (labId, studentId) 的最新事件 id：按 labIds 粗筛后内存对拍当前页行
        Set<Long> labIds = rows.stream()
                .map(LabSubmission::getLabId)
                .collect(Collectors.toSet());
        Map<String, Long> maxEventIdByPair = new HashMap<>();
        if (!labIds.isEmpty()) {
            for (Object[] row : behaviorEventRepository
                    .findMaxIdByBizTypeAndLabIdInGroupByStudent(BehaviorEvent.BIZ_TYPE_LAB, labIds)) {
                Long labId = ((Number) row[0]).longValue();
                Long studentId = ((Number) row[1]).longValue();
                Long maxEventId = ((Number) row[2]).longValue();
                maxEventIdByPair.put(labId + ":" + studentId, maxEventId);
            }
        }

        List<LabSubmission> stale = rows.stream()
                .filter(submission -> {
                    LabSubmissionMetrics metrics = result.get(submission.getId());
                    if (metrics == null) {
                        return true;
                    }
                    Long maxEventId = maxEventIdByPair.get(submission.getLabId() + ":" + submission.getStudentId());
                    return maxEventId != null && maxEventId > metrics.getEventWatermark();
                })
                .toList();
        if (!stale.isEmpty()) {
            result.putAll(labBehaviorMetricsService.refreshStaleMetrics(stale));
        }
        return result;
    }

    /**
     * 行为摘要视图的唯一装配方法（D15 防漂移）：批改队列 / 导航队列 / 提交总览共用。
     *
     * @param metrics 懒算后的指标行；为 null 仅作防御性兜底（正常路径懒算后恒有值）
     * @param submission 提交行，用于推导 durationOpen（SAVED 行"截至最近事件"口径）
     */
    private Map<String, Object> toBehaviorView(LabSubmissionMetrics metrics, LabSubmission submission) {
        if (metrics == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("totalDurationMs", metrics.getTotalDurationMs());
        view.put("activeDurationMs", metrics.getActiveDurationMs());
        view.put("durationOpen", submission.getSubmittedAt() == null);
        view.put("saveCount", metrics.getSaveCount());
        view.put("pasteCount", metrics.getPasteCount());
        view.put("pastedChars", metrics.getPastedChars());
        view.put("hiddenCount", metrics.getVisibilityHiddenCount());
        view.put("hiddenMs", metrics.getHiddenMs());
        view.put("pasteBlockedCount", metrics.getPasteBlockedCount());
        view.put("blurCount", metrics.getBlurCount());
        view.put("sessionCount", metrics.getSessionCount());
        view.put("riskLevel", metrics.getRiskLevel());
        view.put("riskFlags", parseStringList(metrics.getRiskFlagsJson()));
        return view;
    }

    /** risk_flags_json → List<String>；空或解析失败按空列表处理（防御）。 */
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 一次性加载一批实验所属班级的名称，避免逐行 {@code findById}。
     *
     * @return 班级 id → 班级名称（原文，关键词匹配时调用方自行归一化）
     */
    private Map<Long, String> loadClassNames(List<Lab> labs) {
        Set<Long> classIds = labs.stream()
                .map(Lab::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classRoomRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(ClassRoom::getId, ClassRoom::getName));
    }

    private Map<String, Object> toTeacherReportItem(LabStep step, List<LabStepAnswer> answers) {
        LabStepAnswer answer = answers.stream().filter(item -> Objects.equals(item.getLabStepId(), step.getId())).findFirst().orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stepId", step.getId());
        item.put("stepNo", step.getStepNo());
        item.put("stepTitle", step.getTitle());
        item.put("title", step.getTitle());
        item.put("stepScore", step.getStepScore());
        item.put("maxScore", step.getStepScore());
        item.put("questionType", step.getQuestionType());
        item.put("answerId", answer == null ? null : answer.getId());
        item.put("answerText", answer == null ? "" : defaultString(answer.getAnswerText()));
        item.put("answerPayloadJson", answer == null ? null : answer.getAnswerJson());
        item.put("images", answer == null ? List.of() : toAnswerImageViews(answer.getAnswerJson()));
        item.put("score", answer == null ? null : answer.getScore());
        item.put("autoScore", answer == null ? null : answer.getAutoScore());
        item.put("suggestedScore", answer == null ? null : answer.getSuggestedScore());
        item.put("scoreSource", answer == null ? null : answer.getScoreSource());
        item.put("autoJudgeDetail", answer == null ? null : answer.getAutoJudgeDetail());
        item.put("plagiarismRate", submissionRate(answer));
        item.put("teacherComment", answer == null ? null : answer.getTeacherComment());
        return item;
    }

    private Map<String, Object> toTeacherReportViewStep(LabStep step, List<LabStepAnswer> answers) {
        LabStepAnswer answer = answers.stream()
                .filter(item -> Objects.equals(item.getLabStepId(), step.getId()))
                .findFirst()
                .orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stepId", step.getId());
        item.put("stepNo", step.getStepNo());
        item.put("stepTitle", step.getTitle());
        item.put("content", defaultString(step.getContent()));
        item.put("questionType", step.getQuestionType());
        item.put("answerText", resolveStudentAnswerText(answer));
        item.put("images", answer == null ? List.of() : toAnswerImageViews(answer.getAnswerJson()));
        return item;
    }

    private String resolveClassName(Long classId) {
        return classRoomRepository.findById(classId)
                .map(ClassRoom::getName)
                .orElse("");
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private Double submissionRate(LabStepAnswer answer) {
        return null;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
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
        return step != null && "FILL_BLANK".equalsIgnoreCase(defaultString(step.getQuestionType()));
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
            String raw = defaultString(acceptedAnswer).trim();
            String normalized = normalizeBlankAnswer(raw);
            if (!normalized.isBlank()) {
                deduplicated.putIfAbsent(normalized, raw);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private String normalizeBlankAnswer(String value) {
        return defaultString(value)
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<Map<String, Object>> projectStudentOptions(LabStep step) {
        String questionType = defaultString(step.getQuestionType());
        if (!isObjectiveQuestion(questionType)) {
            return List.of();
        }

        JsonNode optionsNode = firstArrayNode(step, "options");
        if (optionsNode != null && optionsNode.isArray() && !optionsNode.isEmpty()) {
            List<Map<String, Object>> options = new ArrayList<>();
            for (int i = 0; i < optionsNode.size(); i++) {
                JsonNode optionNode = optionsNode.get(i);
                String key = firstText(optionNode, "key", "value", "optionKey", "code");
                if (isBlank(key)) {
                    key = optionKeyByIndex(i);
                }
                String label = firstText(optionNode, "label", "content", "text", "optionLabel");
                if (isBlank(label)) {
                    label = key;
                }
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("key", key);
                option.put("label", label);
                options.add(option);
            }
            return options;
        }

        if ("TRUE_FALSE".equalsIgnoreCase(questionType)) {
            return List.of(
                    Map.of("key", "TRUE", "label", "正确"),
                    Map.of("key", "FALSE", "label", "错误")
            );
        }
        return List.of();
    }

    private List<Map<String, Object>> projectStudentBlanks(LabStep step) {
        if (!"FILL_BLANK".equalsIgnoreCase(defaultString(step.getQuestionType()))) {
            return List.of();
        }

        JsonNode blanksNode = firstArrayNode(step, "blanks");
        if (blanksNode == null || !blanksNode.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> blanks = new ArrayList<>();
        for (int i = 0; i < blanksNode.size(); i++) {
            JsonNode blankNode = blanksNode.get(i);
            int index = blankNode.hasNonNull("index") && blankNode.get("index").canConvertToInt()
                    ? blankNode.get("index").asInt()
                    : i;
            String token = firstText(blankNode, "token", "placeholder", "label");
            if (isBlank(token)) {
                token = "Blank " + (index + 1);
            }
            Map<String, Object> blank = new LinkedHashMap<>();
            blank.put("index", index);
            blank.put("token", token);
            blanks.add(blank);
        }
        return blanks;
    }

    private JsonNode firstArrayNode(LabStep step, String fieldName) {
        JsonNode snapshotNode = readJson(defaultString(step.getQuestionSnapshotJson()));
        JsonNode snapshotField = snapshotNode.path(fieldName);
        if (snapshotField.isArray()) {
            return snapshotField;
        }
        JsonNode configNode = readJson(defaultString(step.getAnswerConfigJson()));
        JsonNode configField = configNode.path(fieldName);
        if (configField.isArray()) {
            return configField;
        }
        return null;
    }

    private JsonNode readJson(String json) {
        if (isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (!field.isMissingNode() && !field.isNull()) {
                String value = field.asText();
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private boolean isObjectiveQuestion(String questionType) {
        return "SINGLE_CHOICE".equalsIgnoreCase(questionType)
                || "MULTIPLE_CHOICE".equalsIgnoreCase(questionType)
                || "TRUE_FALSE".equalsIgnoreCase(questionType);
    }

    private String optionKeyByIndex(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private record DistributionBucket(String answerText, String normalizedAnswer, long count) {
    }
}
