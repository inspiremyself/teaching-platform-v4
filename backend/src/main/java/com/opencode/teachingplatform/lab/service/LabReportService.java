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
public class LabReportService {

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

    public LabReportService(LabRepository labRepository,
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
        support.requireTeacher(currentUser);

        // 1. 归一化并校验分页参数：空值取默认值，越界按统一错误码拒绝
        int page = query == null || query.page() == null ? 1 : query.page();
        int pageSize = query == null || query.pageSize() == null ? DEFAULT_REPORT_PAGE_SIZE : query.pageSize();
        if (page < 1 || pageSize < 1 || pageSize > MAX_REPORT_PAGE_SIZE) {
            throw new BusinessException(40000, "参数不合法");
        }

        // 2. 教师数据域：只允许查询自己创建的实验下的提交
        List<Lab> ownedLabs = labRepository.findBySchoolIdAndCreatedByOrderByIdDesc(currentUser.schoolId(), currentUser.id());
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

        // 4. 状态筛选：非法状态统一抛 40000，不再静默返回空页（FR-10）
        SubmissionStatus statusFilter = null;
        String rawStatus = query == null ? null : query.status();
        if (!support.isBlank(rawStatus)) {
            statusFilter = EnumValueParser.parse(SubmissionStatus.class, rawStatus, "报告状态");
        }

        // 5. 行为风险等级筛选：内存命中集模式（D10）。命中集基于已落库 metrics，
        //    当前页行的懒算在装配阶段触发（toReportListViews），筛选不等待懒算
        String rawRiskLevel = query == null ? null : query.riskLevel();
        List<Long> riskHitSubmissionIds = null;
        if (!support.isBlank(rawRiskLevel)) {
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
        String keyword = query == null ? "" : support.normalize(query.keyword());
        boolean keywordActive = !keyword.isBlank();
        List<Long> labKeywordHitIds = List.of();
        List<Long> studentKeywordHitIds = List.of();
        if (keywordActive) {
            Map<Long, String> classNameById = loadClassNames(ownedLabs);
            Set<Long> scopeLabIdSet = new LinkedHashSet<>(scopeLabIds);
            labKeywordHitIds = ownedLabs.stream()
                    .filter(lab -> scopeLabIdSet.contains(lab.getId()))
                    .filter(lab -> support.normalize(lab.getTitle()).contains(keyword)
                            || support.normalize(classNameById.getOrDefault(lab.getClassId(), "")).contains(keyword))
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
        return new PagedResult<>(toReportListViews(pageResult.getContent(), currentUser.schoolId()), page, pageSize, pageResult.getTotalElements());
    }



    /**
     * 教师端实验报告导航队列（全量、轻量）。
     *
     * <p>报告详情页与批量批改页需要基于全部报告做"上一份 / 下一份"跳转，
     * 分页接口会截断队列，因此单独提供无分页的全量列表，行结构与分页接口完全一致。</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherReportQueue(CurrentUser currentUser) {
        support.requireTeacher(currentUser);
        List<Long> ownedLabIds = labRepository.findBySchoolIdAndCreatedByOrderByIdDesc(currentUser.schoolId(), currentUser.id()).stream()
                .map(Lab::getId)
                .toList();
        if (ownedLabIds.isEmpty()) {
            return List.of();
        }
        List<LabSubmission> submissions = labSubmissionRepository.findByLabIdIn(ownedLabIds).stream()
                .sorted(Comparator.comparing(LabSubmission::getId).reversed())
                .toList();
        return toReportListViews(submissions, currentUser.schoolId());
    }



    /**
     * 教师端实验报告全局统计（不受列表筛选影响）。
     *
     * <p>队列页头部的"报告总量 / 待批改 / 已批改"三张卡需要全量口径，
     * 分页结果只能看到当前页，因此单独用一条分组计数查询提供。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherReportStats(CurrentUser currentUser) {
        support.requireTeacher(currentUser);
        List<Long> ownedLabIds = labRepository.findBySchoolIdAndCreatedByOrderByIdDesc(currentUser.schoolId(), currentUser.id()).stream()
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
        support.requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId).orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = support.ownedLab(currentUser, submission.getLabId());
        SysUser student = sysUserRepository.findById(submission.getStudentId()).orElseThrow(() -> new BusinessException(40400, "学生不存在"));
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(lab.getId());
        List<LabStepAnswer> answers = labStepAnswerRepository.findByLabSubmissionId(submission.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", submission.getId());
        detail.put("labId", lab.getId());
        detail.put("labTitle", lab.getTitle());
        detail.put("classId", lab.getClassId());
        detail.put("className", support.resolveClassName(lab.getClassId()));
        detail.put("studentId", student.getId());
        detail.put("studentName", student.getDisplayName());
        detail.put("studentUsername", student.getUsername());
        detail.put("studentNo", student.getUsername());
        detail.put("submitStatus", submission.getSubmitStatus().name());
        detail.put("totalScore", submission.getTotalScore());
        detail.put("summaryRequired", lab.isSummaryRequired());
        detail.put("summaryText", support.defaultString(submission.getSummaryText()));
        detail.put("teacherComment", submission.getTeacherComment());
        detail.put("submittedAt", submission.getSubmittedAt());
        detail.put("gradedAt", submission.getGradedAt());
        // 查重率统一读取提交行上的真实结果（草稿未提交为 0）
        detail.put("plagiarismRate", submission.getPlagiarismRate());
        detail.put("items", steps.stream()
                .map(step -> toTeacherReportItem(step, answers, submission.getPlagiarismRate()))
                .toList());
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
        support.requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        support.ownedLab(currentUser, submission.getLabId());

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



    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherLabReportView(CurrentUser currentUser, Long labId, Long studentId) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
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
        view.put("className", support.resolveClassName(lab.getClassId()));
        view.put("studentNo", student.getUsername());
        view.put("studentName", student.getDisplayName());
        view.put("labTitle", lab.getTitle());
        view.put("purpose", support.defaultString(lab.getDescription()));
        view.put("experimentContent", support.defaultString(lab.getExperimentContent()));
        view.put("totalScore", submission.getSubmitStatus() == SubmissionStatus.GRADED ? submission.getTotalScore() : null);
        view.put("teacherComment", support.defaultString(submission.getTeacherComment()));
        view.put("summaryText", support.defaultString(submission.getSummaryText()));
        view.put("steps", steps.stream().map(step -> toTeacherReportViewStep(step, answers)).toList());
        return view;
    }



    @Transactional(readOnly = true)
    public Map<String, Object> getSubmissionOverview(CurrentUser currentUser, Long labId) {
        support.requireTeacher(currentUser);
        Lab lab = support.ownedLab(currentUser, labId);
        List<LabStep> steps = labStepRepository.findByLabIdOrderByStepNoAsc(labId);
        List<ClassMember> members = classMemberRepository.findByClassId(lab.getClassId());
        Map<Long, LabSubmission> submissionByStudentId = support.indexSubmissionsByStudentId(labSubmissionRepository.findByLabId(labId));
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
        students.sort(Comparator.comparing(row -> support.normalize(row.get("studentNo"))));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("labId", lab.getId());
        overview.put("labTitle", lab.getTitle());
        overview.put("classId", lab.getClassId());
        overview.put("items", items);
        overview.put("students", students);
        return overview;
    }



    @Transactional
    /**
     * 教师整份批改实验报告。
     *
     * <p>这里适合解释“教师评分为什么不直接改 submission 总分”：
     * 系统先逐条更新每一步答案的得分来源与评语，再汇总总分，最后同步成绩记录。</p>
     */
    public Map<String, Object> gradeReport(CurrentUser currentUser, Long reportId, LabRequests.GradeLabReportRequest request) {
        support.requireTeacher(currentUser);
        LabSubmission head = labSubmissionRepository.findById(reportId).orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = support.ownedLab(currentUser, head.getLabId());
        // 先取业务键 (labId, studentId)，再在锁内重读，保证成绩写入与并发批改串行化。
        LabSubmission submission = labSubmissionRepository
                .findByLabIdAndStudentIdForUpdate(lab.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        // 批改门禁：仅学生已提交（SUBMITTED）或已批改（GRADED）的报告允许写分。
        assertReportSubmitted(submission);
        // 同一题项重复提交会重复累加总分，直接拒绝而不是静默去重。
        long distinctAnswerIds = request.items().stream()
                .map(LabRequests.GradeItem::answerId)
                .distinct()
                .count();
        if (distinctAnswerIds != request.items().size()) {
            throw new BusinessException(40000, "评分项重复");
        }
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
            // 分数上限以题项配置为准，Service 层兜底，不依赖 Controller 的 Bean Validation。
            LabStep step = labStepRepository.findByIdAndLabId(answer.getLabStepId(), lab.getId())
                    .orElseThrow(() -> new BusinessException(40400, "实验题项不存在"));
            Integer stepScore = step.getStepScore();
            if (stepScore == null) {
                throw new BusinessException(40000, "实验题项分值未配置");
            }
            if (item.score() == null || item.score() < 0 || item.score() > stepScore) {
                throw new BusinessException(40000, "分数必须在 0 到该题分值之间");
            }
            answer.setScore(item.score());
            answer.setScoreSource("TEACHER");
            answer.setTeacherComment(support.defaultString(item.teacherComment()));
            labStepAnswerRepository.save(answer);
            totalScore += item.score();
        }
        submission.setTotalScore(totalScore);
        submission.setTeacherComment(support.defaultString(request.teacherComment()));
        submission.setSubmitStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(OffsetDateTime.now());
        labSubmissionRepository.save(submission);
        support.upsertScoreRecord(lab, submission, totalScore);
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
        support.requireTeacher(currentUser);
        LabSubmission head = labSubmissionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = support.ownedLab(currentUser, head.getLabId());
        // 先取业务键 (labId, studentId)，再在锁内重读，保证总分与成绩记录写入串行化。
        LabSubmission submission = labSubmissionRepository
                .findByLabIdAndStudentIdForUpdate(lab.getId(), head.getStudentId())
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        // 批改门禁：仅学生已提交（SUBMITTED）或已批改（GRADED）的报告允许写分。
        assertReportSubmitted(submission);
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
        answer.setTeacherComment(support.defaultString(request.teacherComment()));
        labStepAnswerRepository.save(answer);
        support.refreshSubmissionAfterScoring(lab, submission);
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
        support.requireTeacher(currentUser);
        LabSubmission submission = labSubmissionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(40400, "实验报告不存在"));
        Lab lab = support.ownedLab(currentUser, submission.getLabId());
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
                    .findByBusinessTypeAndBusinessIdAndStudentIdAndSchoolId(
                            BusinessType.LAB, lab.getId(), submission.getStudentId(), lab.getSchoolId())
                    .ifPresent(scoreRecordRepository::delete);
        }

        submission.setSubmittedAt(null);
        submission.setTeacherComment("");
        support.refreshSavedSubmissionAfterScoring(submission);

        return Map.of(
                "submissionId", submission.getId(),
                "status", submission.getSubmitStatus().name(),
                "labId", lab.getId(),
                "studentId", submission.getStudentId()
        );
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



    /**
     * 教师批改前的提交状态门禁：学生尚未提交的报告不允许写分。
     *
     * @param submission 已加锁重读的实验提交
     */
    private void assertReportSubmitted(LabSubmission submission) {
        SubmissionStatus status = submission.getSubmitStatus();
        if (status != SubmissionStatus.SUBMITTED && status != SubmissionStatus.GRADED) {
            throw new BusinessException(40000, "学生尚未提交实验报告，不能批改");
        }
    }



    private Map<Long, LabStepAnswer> indexAnswersByStepId(List<LabStepAnswer> answers) {
        Map<Long, LabStepAnswer> indexed = new LinkedHashMap<>();
        for (LabStepAnswer answer : answers) {
            indexed.putIfAbsent(answer.getLabStepId(), answer);
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
        String answerText = support.defaultString(answer.getAnswerText());
        boolean hasText = !answerText.isBlank();
        int imageCount = LabAnswerImageSupport.extractImages(answer.getAnswerJson(), objectMapper).size();
        cell.put("answered", hasText || imageCount > 0);
        cell.put("hasText", hasText);
        cell.put("imageCount", imageCount);
        return cell;
    }



    /**
     * 批量把提交行映射为教师报告列表视图。
     *
     * <p>分页接口与导航队列共用这一份实现，保证两条链路的行字段口径不会漂移；
     * 同时把"逐行查实验 / 学生 / 班级"收敛成每类对象各一次 {@code findAllById}，消除 N+1。</p>
     */
    private List<Map<String, Object>> toReportListViews(List<LabSubmission> submissions, Long schoolId) {
        if (submissions.isEmpty()) {
            return List.of();
        }
        Set<Long> labIds = submissions.stream()
                .map(LabSubmission::getLabId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> studentIds = submissions.stream()
                .map(LabSubmission::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Lab> labById = labRepository.findByIdInAndSchoolId(labIds, schoolId).stream()
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



    private Map<String, Object> toTeacherReportItem(LabStep step, List<LabStepAnswer> answers, Double plagiarismRate) {
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
        item.put("answerText", answer == null ? "" : support.defaultString(answer.getAnswerText()));
        item.put("answerPayloadJson", answer == null ? null : answer.getAnswerJson());
        item.put("images", answer == null ? List.of() : support.toAnswerImageViews(answer.getAnswerJson()));
        item.put("score", answer == null ? null : answer.getScore());
        item.put("autoScore", answer == null ? null : answer.getAutoScore());
        item.put("suggestedScore", answer == null ? null : answer.getSuggestedScore());
        item.put("scoreSource", answer == null ? null : answer.getScoreSource());
        item.put("autoJudgeDetail", answer == null ? null : answer.getAutoJudgeDetail());
        item.put("plagiarismRate", plagiarismRate);
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
        item.put("content", support.defaultString(step.getContent()));
        item.put("questionType", step.getQuestionType());
        item.put("answerText", support.resolveStudentAnswerText(answer));
        item.put("images", answer == null ? List.of() : support.toAnswerImageViews(answer.getAnswerJson()));
        return item;
    }

}
