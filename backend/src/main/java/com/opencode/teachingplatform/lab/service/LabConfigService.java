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
public class LabConfigService {

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

    public LabConfigService(LabRepository labRepository,
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
    public List<Map<String, Object>> listTeacherLabs(CurrentUser currentUser) {
        support.requireTeacher(currentUser);
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
        support.requireTeacher(currentUser);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        // 引用资料必须属于本校，校验先于任何实体写入
        assertMaterialAccessible(currentUser, request.materialId());
        if (parseStatus(request.status()) == ActivityStatus.PUBLISHED) {
            throw new BusinessException(40000, LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE);
        }
        // 时间顺序校验先于落库：开始时间必须早于结束时间，避免建出不可用的时间窗
        assertLabTimeWindowValid(request.startAt(), request.dueAt());
        Lab lab = new Lab();
        lab.setCreatedBy(currentUser.id());
        applyLab(lab, request.classId(), request.title(), request.description(), request.experimentContent(), request.experimentType(), request.status(), request.materialId(), request.scoreVisibilityMode(), request.summaryRequired(), request.startAt(), request.dueAt());
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
        support.requireTeacher(currentUser);

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
        // 成绩可见模式不能置 null（列 NOT NULL 且视图调用 .name()）；截止后可见在副本上无截止时间，重置为教师确认后可见
        copy.setScoreVisibilityMode(source.getScoreVisibilityMode() == ScoreVisibilityMode.AFTER_DEADLINE
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : source.getScoreVisibilityMode());
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



    @Transactional
    public Map<String, Object> updateLab(CurrentUser currentUser, Long labId, LabRequests.UpdateLabRequest request) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
        classAccessService.assertTeacherAssignedToClass(currentUser, request.classId());
        // 引用资料必须属于本校，校验先于 applyLab 的任何赋值
        assertMaterialAccessible(currentUser, request.materialId());
        boolean statusProvided = request.status() != null && !request.status().isBlank();
        // 不传 status 表示保持当前状态，只有显式传值才可能触发状态回退。
        ActivityStatus targetStatus = statusProvided ? parseStatus(request.status()) : lab.getStatus();
        if (statusProvided) {
            assertLabRevertible(lab, targetStatus);
        }
        if (request.classId() != null && !Objects.equals(request.classId(), lab.getClassId())
                && labSubmissionRepository.countByLabId(labId) > 0) {
            throw new BusinessException(40000, "已有学生作答，不能变更所属班级");
        }
        // 时间顺序校验先于任何赋值：命中时同持久化上下文不残留被污染的时间窗
        assertLabTimeWindowValid(request.startAt(), request.dueAt());
        // 发布校验必须先于 applyLab：若先写实体再抛异常，同一持久化上下文会残留被污染的 PUBLISHED 状态
        if (targetStatus == ActivityStatus.PUBLISHED) {
            assertLabPublishable(lab);
            // 用本次请求值判定：请求未传模式时 applyLab 会写成 AFTER_TEACHER_CONFIRM，不能用实体旧值
            ScoreVisibilityMode effectiveMode = request.scoreVisibilityMode() == null
                    ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                    : request.scoreVisibilityMode();
            assertDeadlineVisibilityPublishable(effectiveMode, request.dueAt());
        }
        applyLab(lab, request.classId(), request.title(), request.description(), request.experimentContent(), request.experimentType(), targetStatus.name(), request.materialId(), request.scoreVisibilityMode(), request.summaryRequired(), request.startAt(), request.dueAt());
        return toTeacherLabView(labRepository.save(lab));
    }



    @Transactional
    public Map<String, Object> changeLabStatus(CurrentUser currentUser, Long labId, LabRequests.ChangeLabStatusRequest request) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
        ActivityStatus nextStatus = parseStatus(request.status());
        assertLabRevertible(lab, nextStatus);
        if (nextStatus == ActivityStatus.PUBLISHED) {
            assertLabPublishable(lab);
            // 无请求侧时间字段：以实体现值判定已存时间窗与可见模式是否自洽
            assertDeadlineVisibilityPublishable(lab.getScoreVisibilityMode(), lab.getEndAt());
        }
        lab.setStatus(nextStatus);
        return toTeacherLabView(labRepository.save(lab));
    }



    @Transactional
    public Map<String, Object> releaseScores(CurrentUser currentUser, Long labId) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
        lab.setScoreReleased(true);
        labRepository.save(lab);
        return ScoreReleaseSupport.releasedView(lab.getScoreVisibilityMode().name());
    }



    @Transactional(readOnly = true)
    /**
     * 学生查看实验详情。
     *
     * <p>返回结果会把实验基础信息、学生当前提交状态、步骤列表、已保存答案以及分数可见性一起组装好，
     * 供前端一次性渲染页面。</p>
     */
    public Map<String, Object> getStudentLabDetail(CurrentUser currentUser, Long labId) {
        support.requireStudent(currentUser);
        Lab lab = support.accessiblePublishedLab(currentUser, labId);
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(labId, currentUser.id()).orElse(null);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(labId);
        boolean scoreVisible = support.scoreVisible(lab, submission);
        List<Map<String, Object>> items = steps.stream().map(step -> toStudentStepView(step, submission, scoreVisible)).toList();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", lab.getId());
        detail.put("title", lab.getTitle());
        detail.put("description", support.defaultString(lab.getDescription()));
        detail.put("experimentContent", support.defaultString(lab.getExperimentContent()));
        detail.put("experimentType", lab.getExperimentType());
        detail.put("materialId", lab.getMaterialId());
        appendMaterialFields(detail, lab.getMaterialId());
        detail.put("status", lab.getStatus().name());
        detail.put("classId", lab.getClassId());
        detail.put("className", support.resolveClassName(lab.getClassId()));
        detail.put("summaryRequired", lab.isSummaryRequired());
        // 时间窗回显：该路径独立组装，不经过 toStudentLabView，必须单独补两个字段
        detail.put("startAt", lab.getStartAt());
        detail.put("dueAt", lab.getEndAt());
        detail.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        detail.put("totalScore", submission == null || !scoreVisible ? null : submission.getTotalScore());
        detail.put("teacherComment", submission == null || !scoreVisible ? null : submission.getTeacherComment());
        detail.put("summaryText", submission == null ? "" : support.defaultString(submission.getSummaryText()));
        detail.put("items", items);
        detail.put("steps", items);
        return detail;
    }



    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentLabs(CurrentUser currentUser) {
        support.requireStudent(currentUser);
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



    /**
     * 校验实验状态回退是否允许。
     *
     * <p>仅当目标状态为草稿且当前状态非草稿时，要求该实验提交数为 0：
     * 已有学生作答的实验不能退回草稿，否则学生可重新作答并覆盖已产生的成绩。</p>
     */
    private void assertLabRevertible(Lab lab, ActivityStatus nextStatus) {
        if (nextStatus == ActivityStatus.DRAFT && lab.getStatus() != ActivityStatus.DRAFT
                && labSubmissionRepository.countByLabId(lab.getId()) > 0) {
            throw new BusinessException(40000, "已有学生作答，不能退回草稿");
        }
    }



    private void assertLabPublishable(Lab lab) {
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        if (steps.isEmpty()) {
            throw new BusinessException(40000, LAB_PUBLISH_WITHOUT_ITEMS_MESSAGE);
        }
    }



    /**
     * 时间窗顺序校验：两者都非空时必须 {@code startAt < dueAt}。
     */
    private void assertLabTimeWindowValid(OffsetDateTime startAt, OffsetDateTime dueAt) {
        if (startAt != null && dueAt != null && !startAt.isBefore(dueAt)) {
            throw new BusinessException(40000, LAB_TIME_ORDER_MESSAGE);
        }
    }



    /**
     * 发布门禁：截止后可见模式依赖结束时间，没有结束时间时不允许发布。
     */
    private void assertDeadlineVisibilityPublishable(ScoreVisibilityMode mode, OffsetDateTime dueAt) {
        if (mode == ScoreVisibilityMode.AFTER_DEADLINE && dueAt == null) {
            throw new BusinessException(40000, LAB_DEADLINE_MODE_WITHOUT_DUE_MESSAGE);
        }
    }



    private ActivityStatus parseStatus(String value) {
        return ActivityStatusParser.parse(value, "实验状态");
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
                          Boolean summaryRequired,
                          OffsetDateTime startAt,
                          OffsetDateTime dueAt) {
        lab.setTitle(title);
        lab.setDescription(support.defaultString(description));
        lab.setExperimentContent(support.defaultString(experimentContent));
        lab.setExperimentType(experimentType == null || experimentType <= 0 ? 1 : experimentType);
        lab.setClassId(classId);
        lab.setMaterialId(materialId);
        lab.setStatus(parseStatus(status));
        lab.setScoreVisibilityMode(scoreVisibilityMode == null ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM : scoreVisibilityMode);
        lab.setSummaryRequired(Boolean.TRUE.equals(summaryRequired));
        // 对外字段名 dueAt，落到实体列为 end_at
        lab.setStartAt(startAt);
        lab.setEndAt(dueAt);
    }



    private Map<String, Object> toTeacherLabView(Lab lab) {
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        ClassRoom classRoom = classRoomRepository.findById(lab.getClassId()).orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", lab.getId());
        view.put("title", lab.getTitle());
        view.put("description", support.defaultString(lab.getDescription()));
        view.put("experimentContent", support.defaultString(lab.getExperimentContent()));
        view.put("experimentType", lab.getExperimentType());
        view.put("materialId", lab.getMaterialId());
        appendMaterialFields(view, lab.getMaterialId());
        view.put("classId", lab.getClassId());
        view.put("className", classRoom == null ? "" : classRoom.getName());
        view.put("status", lab.getStatus().name());
        view.put("scoreVisibilityMode", lab.getScoreVisibilityMode().name());
        view.put("summaryRequired", lab.isSummaryRequired());
        view.put("startAt", lab.getStartAt());
        view.put("dueAt", lab.getEndAt());
        view.put("stepCount", steps.size());
        return view;
    }



    private Map<String, Object> toStudentLabView(Lab lab, Long studentId) {
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentId(lab.getId(), studentId).orElse(null);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", lab.getId());
        view.put("title", lab.getTitle());
        view.put("description", support.defaultString(lab.getDescription()));
        view.put("experimentContent", support.defaultString(lab.getExperimentContent()));
        view.put("experimentType", lab.getExperimentType());
        view.put("materialId", lab.getMaterialId());
        appendMaterialFields(view, lab.getMaterialId());
        view.put("status", lab.getStatus().name());
        view.put("classId", lab.getClassId());
        view.put("className", support.resolveClassName(lab.getClassId()));
        view.put("summaryRequired", lab.isSummaryRequired());
        view.put("startAt", lab.getStartAt());
        view.put("dueAt", lab.getEndAt());
        view.put("stepCount", steps.size());
        view.put("submissionStatus", submission == null ? null : submission.getSubmitStatus().name());
        boolean scoreVisible = support.scoreVisible(lab, submission);
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

        target.put("materialTitle", support.defaultString(material.getTitle()));
        target.put("materialFileName", support.defaultString(material.getFileName()));
        target.put("materialDownloadUrl", "/api/v1/materials/" + material.getId() + "/download");
    }



    /**
     * 校验实验引用资料归属于当前教师所在学校。
     *
     * <p>资料为选填，{@code materialId} 为空时直接放行；资料不存在与非本校资料统一拒绝，
     * 避免通过实验配置越权引用他校资料。</p>
     *
     * @param currentUser 当前登录教师
     * @param materialId  资料 id，可为 null
     */
    private void assertMaterialAccessible(CurrentUser currentUser, Long materialId) {
        if (materialId == null) {
            return;
        }
        CourseMaterial material = courseMaterialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(40000, "资料不存在"));
        if (!Objects.equals(material.getSchoolId(), currentUser.schoolId())) {
            throw new BusinessException(40000, "无权限引用该资料");
        }
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
        item.put("answerText", support.resolveStudentAnswerText(answer));
        item.put("answerPayloadJson", answer == null ? null : answer.getAnswerJson());
        List<Map<String, Object>> options = projectStudentOptions(step);
        if (!options.isEmpty()) {
            item.put("options", options);
        }
        List<Map<String, Object>> blanks = projectStudentBlanks(step);
        if (!blanks.isEmpty()) {
            item.put("blanks", blanks);
        }
        if ("CODE".equalsIgnoreCase(support.defaultString(step.getQuestionType()))) {
            item.put("editorLanguage", support.defaultString(step.getEditorLanguage()));
        }
        item.put("score", answer == null || !scoreVisible ? null : answer.getScore());
        item.put("teacherComment", answer == null || !scoreVisible ? null : answer.getTeacherComment());
        return item;
    }



    private List<Map<String, Object>> projectStudentOptions(LabStep step) {
        String questionType = support.defaultString(step.getQuestionType());
        if (!isObjectiveQuestion(questionType)) {
            return List.of();
        }

        JsonNode optionsNode = firstArrayNode(step, "options");
        if (optionsNode != null && optionsNode.isArray() && !optionsNode.isEmpty()) {
            List<Map<String, Object>> options = new ArrayList<>();
            for (int i = 0; i < optionsNode.size(); i++) {
                JsonNode optionNode = optionsNode.get(i);
                String key = firstText(optionNode, "key", "value", "optionKey", "code");
                if (support.isBlank(key)) {
                    key = optionKeyByIndex(i);
                }
                String label = firstText(optionNode, "label", "content", "text", "optionLabel");
                if (support.isBlank(label)) {
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
        if (!"FILL_BLANK".equalsIgnoreCase(support.defaultString(step.getQuestionType()))) {
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
            if (support.isBlank(token)) {
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
        JsonNode snapshotNode = readJson(support.defaultString(step.getQuestionSnapshotJson()));
        JsonNode snapshotField = snapshotNode.path(fieldName);
        if (snapshotField.isArray()) {
            return snapshotField;
        }
        JsonNode configNode = readJson(support.defaultString(step.getAnswerConfigJson()));
        JsonNode configField = configNode.path(fieldName);
        if (configField.isArray()) {
            return configField;
        }
        return null;
    }



    private JsonNode readJson(String json) {
        if (support.isBlank(json)) {
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
                if (!support.isBlank(value)) {
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

}
