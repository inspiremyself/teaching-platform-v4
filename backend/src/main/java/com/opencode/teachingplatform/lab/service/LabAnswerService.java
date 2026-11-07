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
public class LabAnswerService {

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

    public LabAnswerService(LabRepository labRepository,
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
    public List<Map<String, Object>> listStudentLabSubmissions(CurrentUser currentUser, Long studentId) {
        support.requireTeacher(currentUser);
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



    @Transactional
    /**
     * 学生保存单个步骤答案。
     *
     * <p>运行顺序是：校验实验与步骤 → 查找或创建实验提交记录 → 写入步骤答案 → 追加答案快照日志。</p>
     * <p>这也是你讲“数据库对应关系”时最适合举例的方法：
     * 一次保存动作会同时影响 experiment_submission、experiment_answer、lab_step_answer_log 三类数据。</p>
     */
    public Map<String, Object> saveAnswer(CurrentUser currentUser, Long labId, Long stepId, LabRequests.SaveStepAnswerRequest request) {
        support.requireStudent(currentUser);
        Lab lab = support.accessiblePublishedLab(currentUser, labId);
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能保存");
        }
        // 时间窗门禁：未开始/已截止不允许产生任何写操作
        assertLabWritable(lab);
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
            LabAnswerImageSupport.validateImagesForSave(step.getQuestionType(), payload.images(), currentUser.id(), localFileStorageService, storageRoot);
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
        support.requireStudent(currentUser);
        Lab lab = support.accessiblePublishedLab(currentUser, labId);
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能上传");
        }
        // 时间窗门禁：未开始/已截止不允许上传（与保存答案同一口径）
        assertLabWritable(lab);
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
        String contentType = file == null ? "" : support.defaultString(file.getContentType());
        long size = file == null ? 0L : file.getSize();
        String normalizedType = LabAnswerImageSupport.normalizeContentType(contentType);
        LabAnswerImageSupport.validateUpload(normalizedType, size);
        // 目录带上传者学生 id，保存与预览都据此校验图片归属。
        String folder = LabAnswerImageSupport.LAB_ANSWER_ROOT
                + "/" + LocalDate.now(ZoneId.of("Asia/Shanghai"))
                + "/" + currentUser.id();
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
        String answerText = support.defaultString(answer.getAnswerText());
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
    public LabService.PreviewAnswerImageResult previewAnswerImage(CurrentUser currentUser, String path) {
        String normalizedPath = LabAnswerImageSupport.normalizeRelativePath(path);
        if (!LabAnswerImageSupport.matchesAnswerPath(normalizedPath)) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        // 落盘边界收敛到 lab-answers 根：任何逃逸出该目录的路径都拒绝。
        Path labAnswerRoot = storageRoot.resolve(LabAnswerImageSupport.LAB_ANSWER_ROOT).normalize();
        Path targetPath = storageRoot.resolve(normalizedPath).normalize();
        if (!targetPath.startsWith(labAnswerRoot)) {
            throw new BusinessException(40000, "图片路径不合法");
        }
        LabStepAnswer answer = findAnswerOwningImagePath(normalizedPath);
        LabSubmission submission = labSubmissionRepository.findById(answer.getLabSubmissionId())
                .orElseThrow(() -> new BusinessException(40400, "实验提交不存在"));
        Lab lab = labRepository.findById(submission.getLabId()).orElseThrow(() -> new BusinessException(40400, "实验不存在"));
        requireAnswerImageAccess(currentUser, submission, lab);
        // 学生只能读取自己答案中的图片；教师与历史无学生段的旧路径跳过比对。
        LabAnswerImageSupport.assertStudentSegmentMatches(normalizedPath, currentUser, submission.getStudentId());
        byte[] bytes = localFileStorageService.read(normalizedPath);
        String contentType = resolveImageContentType(answer.getAnswerJson(), normalizedPath);
        return new LabService.PreviewAnswerImageResult(contentType, bytes);
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
        support.requireStudent(currentUser);
        Lab lab = support.accessiblePublishedLab(currentUser, labId);
        if (lab.getStatus() == ActivityStatus.CLOSED) {
            throw new BusinessException(40000, "实验已关闭，不能提交");
        }
        // 时间窗门禁：必须先于行锁与任何写操作
        assertLabWritable(lab);
        // 先加行锁再判状态：并发提交时后到事务阻塞，待前一事务提交后读到 SUBMITTED 并被拒绝。
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentIdForUpdate(labId, currentUser.id())
                .orElseThrow(() -> new BusinessException(40000, "请先保存实验答案"));
        // 状态判定必须早于任何写操作（小结、状态、提交时间、自动评分、行为指标）。
        if (submission.getSubmitStatus() == SubmissionStatus.SUBMITTED) {
            throw new BusinessException(40000, "实验已提交，不能重复提交");
        }
        if (submission.getSubmitStatus() == SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "实验已批改，如需修改请联系教师打回");
        }
        submission.setSummaryText(request == null ? "" : support.defaultString(request.summaryText()));
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
            support.applyScoringResult(answer, result);
            labStepAnswerRepository.save(answer);
        }
        support.refreshSubmissionAfterScoring(lab, submission);
        // 行为指标联动（D8 时机①）：提交成功事务内全量算一次。
        // 提交瞬间前端缓冲中的尾部事件（LAB_LEAVE、末段心跳）可能尚未到达，
        // 本次以已到事件为准，迟到事件由教师首读的懒算兜底重算（时机②）
        labBehaviorMetricsService.computeForSubmission(submission);
        // 查重作为提交链路最后一个外部副作用：失败只降级查重率，不阻断提交
        refreshPlagiarism(lab, submission);
        return Map.of(
                "submissionId", submission.getId(),
                "status", submission.getSubmitStatus().name()
        );
    }



    private void appendAnswerSnapshot(LabStepAnswer answer) {
        LabStepAnswerLog log = new LabStepAnswerLog();
        log.setLabStepAnswerId(answer.getId());
        log.setContent(answer.getAnswerText());
        log.setFillTime(OffsetDateTime.now());
        labStepAnswerLogRepository.save(log);
    }



    /**
     * 学生写入口（保存答案 / 上传图片 / 提交）的时间窗门禁。
     *
     * <p>边界口径：{@code now == startAt} 允许进入；{@code now == dueAt} 视为已截止。
     * 两个边界为空时完全跳过，保持历史实验不受限。</p>
     */
    private void assertLabWritable(Lab lab) {
        OffsetDateTime now = OffsetDateTime.now();
        if (lab.getStartAt() != null && now.isBefore(lab.getStartAt())) {
            throw new BusinessException(40000, LAB_NOT_STARTED_MESSAGE);
        }
        if (lab.getEndAt() != null && !now.isBefore(lab.getEndAt())) {
            throw new BusinessException(40000, LAB_DEADLINE_PASSED_MESSAGE);
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
            support.ownedLab(currentUser, lab.getId());
            return;
        }
        throw new BusinessException(40300, "无权限访问该图片");
    }



    /**
     * 查找或创建「可编辑」的实验提交。
     *
     * <p><b>快路径：</b>自动保存是高频路径，读到既有提交时完全不进独立事务，避免每次多开连接。
     * <b>慢路径：</b>仅首存时通过 {@link LabSubmissionClaimService} 在独立事务内插入 {@code SAVED} 行，
     * 唯一键冲突只回滚内层事务。随后一律加锁重读：REPEATABLE READ 下普通读可能看不到并发事务
     * 刚提交的行，且后续题项答案依赖父行排他锁串行化。</p>
     */
    private LabSubmission ensureEditableSubmission(CurrentUser currentUser, Long labId) {
        boolean firstSave = labSubmissionRepository.findByLabIdAndStudentId(labId, currentUser.id()).isEmpty();
        if (firstSave) {
            try {
                labSubmissionClaimService.claim(labId, currentUser.id());
            } catch (DataIntegrityViolationException ignored) {
                // 并发首存：另一事务已提交，下面锁内重读
            }
        }
        LabSubmission submission = labSubmissionRepository.findByLabIdAndStudentIdForUpdate(labId, currentUser.id())
                .orElseThrow(() -> new BusinessException(50000, "实验提交创建失败，请重试"));
        if (submission.getSubmitStatus() == SubmissionStatus.SUBMITTED || submission.getSubmitStatus() == SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "实验已提交，不能继续修改");
        }
        submission.setSubmitStatus(SubmissionStatus.SAVED);
        return labSubmissionRepository.save(submission);
    }



    private LabStepAnswer prepareStepAnswer(LabSubmission submission, LabStep step) {
        // 加锁读：父提交行 X 锁已串行化同一提交的写入；此处再锁答案行，避免快照读旧值后重复插入
        LabStepAnswer answer = labStepAnswerRepository
                .findByLabSubmissionIdAndLabStepIdForUpdate(submission.getId(), step.getId())
                .orElseGet(LabStepAnswer::new);
        answer.setLabSubmissionId(submission.getId());
        answer.setLabStepId(step.getId());
        answer.setQuestionId(step.getQuestionId());
        answer.setQuestionSnapshotJson(support.defaultString(step.getQuestionSnapshotJson()));
        answer.setEditorLanguage(support.defaultString(step.getEditorLanguage()));
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
        view.put("className", support.resolveClassName(lab.getClassId()));
        view.put("submitStatus", submission.getSubmitStatus().name());
        view.put("totalScore", submission.getTotalScore());
        view.put("submittedAt", submission.getSubmittedAt());
        view.put("gradedAt", submission.getGradedAt());
        return view;
    }



    /**
     * 刷新实验提交的查重率与查重任务。
     *
     * <p>分析阶段与写入阶段分别 try/catch 降级：分析与任务写入任何失败都只把查重率置 0，
     * 不阻断提交主流程；任务写入使用独立事务（{@link PlagiarismTaskWriter}），
     * 其回滚不会污染外层提交事务。</p>
     *
     * @param lab        当前实验
     * @param submission 当前提交
     */
    private void refreshPlagiarism(Lab lab, LabSubmission submission) {
        LocalPlagiarismService.PlagiarismAnalysisResult result;
        try {
            String sourceText = labSubmissionText(submission.getId());
            if (sourceText.isBlank()) {
                submission.setPlagiarismRate(0D);
                labSubmissionRepository.save(submission);
                return;
            }
            List<LocalPlagiarismService.CandidateText> candidates =
                    labSubmissionRepository.findByLabIdAndSubmitStatusIn(
                                    lab.getId(), List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED))
                            .stream()
                            .filter(candidate -> !Objects.equals(candidate.getId(), submission.getId()))
                            .map(candidate -> Map.entry(candidate, labSubmissionText(candidate.getId())))
                            .filter(entry -> !entry.getValue().isBlank())
                            .map(entry -> new LocalPlagiarismService.CandidateText(
                                    resolveLabReference(lab, entry.getKey().getStudentId()), entry.getValue()))
                            .toList();
            result = localPlagiarismService.analyze(sourceText, candidates);
        } catch (Exception ex) {
            log.warn("实验查重分析失败，降级为 0: labId={}, submissionId={}", lab.getId(), submission.getId(), ex);
            submission.setPlagiarismRate(0D);
            labSubmissionRepository.save(submission);
            return;
        }

        try {
            submission.setPlagiarismRate(result.similarityRate());
            labSubmissionRepository.save(submission);
            plagiarismTaskWriter.write(BusinessType.LAB, submission.getId(), submission.getStudentId(),
                    result.similarityRate(), result.topMatchSummary(), result.rawResultJson());
        } catch (Exception ex) {
            // writer 为 REQUIRES_NEW，其回滚不污染外层事务；此处只降级查重率
            log.warn("实验查重任务写入失败，降级为 0: labId={}, submissionId={}", lab.getId(), submission.getId(), ex);
            submission.setPlagiarismRate(0D);
            labSubmissionRepository.save(submission);
        }
    }



    /**
     * 拼接整份实验提交的作答文本，供查重比对。
     *
     * <p>与作业查重同口径：{@code answerText} 为空时回退 {@code answerJson}，
     * 避免选择题 / JSON 作答被当成空文本导致查重恒为 0。</p>
     */
    private String labSubmissionText(Long submissionId) {
        return labStepAnswerRepository.findByLabSubmissionId(submissionId).stream()
                .map(answer -> answer.getAnswerText() == null || answer.getAnswerText().isBlank()
                        ? answer.getAnswerJson()
                        : answer.getAnswerText())
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
    }



    /**
     * 生成实验查重候选的可读来源标识，便于教师定位相似报告。
     */
    private String resolveLabReference(Lab lab, Long studentId) {
        String studentName = sysUserRepository.findById(studentId)
                .map(SysUser::getDisplayName)
                .orElse("学生#" + studentId);
        return "实验《" + lab.getTitle() + "》-" + studentName;
    }

}
