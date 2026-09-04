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
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.common.enums.SubmissionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.common.file.LocalFileStorageService;
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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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

    private final LabRepository labRepository;
    private final LabStepRepository labStepRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabStepAnswerRepository labStepAnswerRepository;
    private final LabStepAnswerLogRepository labStepAnswerLogRepository;
    private final ExperimentBlankAnswerOverrideRepository experimentBlankAnswerOverrideRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final CourseMaterialRepository courseMaterialRepository;
    private final SysUserRepository sysUserRepository;
    private final LabScoringSupport labScoringSupport;
    private final ScoreRecordRepository scoreRecordRepository;
    private final ObjectMapper objectMapper;
    private final LocalFileStorageService localFileStorageService;
    private final Path storageRoot;

    public LabService(LabRepository labRepository,
                      LabStepRepository labStepRepository,
                      LabSubmissionRepository labSubmissionRepository,
                      LabStepAnswerRepository labStepAnswerRepository,
                      LabStepAnswerLogRepository labStepAnswerLogRepository,
                      ExperimentBlankAnswerOverrideRepository experimentBlankAnswerOverrideRepository,
                      ClassRoomRepository classRoomRepository,
                      ClassMemberRepository classMemberRepository,
                      CourseMaterialRepository courseMaterialRepository,
                      SysUserRepository sysUserRepository,
                      LabScoringSupport labScoringSupport,
                      ScoreRecordRepository scoreRecordRepository,
                      ObjectMapper objectMapper,
                      LocalFileStorageService localFileStorageService,
                      @Value("${app.file-storage-root}") String storageRoot) {
        this.labRepository = labRepository;
        this.labStepRepository = labStepRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labStepAnswerRepository = labStepAnswerRepository;
        this.labStepAnswerLogRepository = labStepAnswerLogRepository;
        this.experimentBlankAnswerOverrideRepository = experimentBlankAnswerOverrideRepository;
        this.classRoomRepository = classRoomRepository;
        this.classMemberRepository = classMemberRepository;
        this.courseMaterialRepository = courseMaterialRepository;
        this.sysUserRepository = sysUserRepository;
        this.labScoringSupport = labScoringSupport;
        this.scoreRecordRepository = scoreRecordRepository;
        this.objectMapper = objectMapper;
        this.localFileStorageService = localFileStorageService;
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
        ClassRoom classRoom = classRoomRepository.findByIdAndTeacherUserId(request.classId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该班级"));
        Lab lab = new Lab();
        lab.setCreatedBy(currentUser.id());
        applyLab(lab, classRoom.getId(), request.title(), request.description(), request.experimentContent(), request.experimentType(), request.status(), request.materialId(), request.scoreVisibilityMode(), request.summaryRequired());
        return toTeacherLabView(labRepository.save(lab));
    }

    @Transactional
    public Map<String, Object> updateLab(CurrentUser currentUser, Long labId, LabRequests.UpdateLabRequest request) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        ClassRoom classRoom = classRoomRepository.findByIdAndTeacherUserId(request.classId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该班级"));
        applyLab(lab, classRoom.getId(), request.title(), request.description(), request.experimentContent(), request.experimentType(), request.status(), request.materialId(), request.scoreVisibilityMode(), request.summaryRequired());
        return toTeacherLabView(labRepository.save(lab));
    }

    @Transactional
    public Map<String, Object> changeLabStatus(CurrentUser currentUser, Long labId, LabRequests.ChangeLabStatusRequest request) {
        requireTeacher(currentUser);
        Lab lab = ownedLab(currentUser, labId);
        lab.setStatus(parseStatus(request.status()));
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherReports(CurrentUser currentUser, LabRequests.TeacherLabReportQuery query) {
        requireTeacher(currentUser);
        List<Long> ownedLabIds = labRepository.findByCreatedByOrderByIdDesc(currentUser.id()).stream().map(Lab::getId).toList();
        if (ownedLabIds.isEmpty()) {
            return List.of();
        }
        return labSubmissionRepository.findByLabIdIn(ownedLabIds).stream()
                .map(this::toReportListView)
                .filter(item -> matchesTeacherReportQuery(item, query))
                .toList();
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
        if (answer.getScore() == null) {
            answer.setScore(0D);
        }
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
        if (answer.getScore() == null) {
            answer.setScore(0D);
        }
        labStepAnswerRepository.save(answer);
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
        classRoomRepository.findByIdAndTeacherUserId(lab.getClassId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该班级"));
        return lab;
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
        } else if (result.scoreSource() != ScoreSource.TEACHER) {
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

    private Map<String, Object> toReportListView(LabSubmission submission) {
        Lab lab = labRepository.findById(submission.getLabId()).orElseThrow(() -> new BusinessException(40400, "实验不存在"));
        SysUser student = sysUserRepository.findById(submission.getStudentId()).orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", submission.getId());
        item.put("labId", lab.getId());
        item.put("labTitle", lab.getTitle());
        item.put("classId", lab.getClassId());
        item.put("className", resolveClassName(lab.getClassId()));
        item.put("studentId", student.getId());
        item.put("studentName", student.getDisplayName());
        item.put("studentUsername", student.getUsername());
        item.put("studentNo", student.getUsername());
        item.put("submitStatus", submission.getSubmitStatus().name());
        item.put("totalScore", submission.getTotalScore());
        item.put("summaryRequired", lab.isSummaryRequired());
        item.put("submittedAt", submission.getSubmittedAt());
        item.put("gradedAt", submission.getGradedAt());
        return item;
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
        return item;
    }

    private boolean matchesTeacherReportQuery(Map<String, Object> item, LabRequests.TeacherLabReportQuery query) {
        if (query == null) {
            return true;
        }
        String normalizedStatus = normalize(query.status());
        if (!isBlank(normalizedStatus) && !normalizedStatus.equals(normalize(item.get("submitStatus")))) {
            return false;
        }
        String keyword = normalize(query.keyword());
        if (isBlank(keyword)) {
            return true;
        }
        return containsNormalized(item.get("labTitle"), keyword)
                || containsNormalized(item.get("studentName"), keyword)
                || containsNormalized(item.get("studentNo"), keyword)
                || containsNormalized(item.get("studentUsername"), keyword)
                || containsNormalized(item.get("className"), keyword);
    }

    private String resolveClassName(Long classId) {
        return classRoomRepository.findById(classId)
                .map(ClassRoom::getName)
                .orElse("");
    }

    private boolean containsNormalized(Object value, String keyword) {
        return normalize(value).contains(keyword);
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
