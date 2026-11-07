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
public class LabService {

    private final LabConfigService labConfigService;
    private final LabStepService labStepService;
    private final LabAnswerService labAnswerService;
    private final LabReportService labReportService;

    public LabService(
            LabConfigService labConfigService,
            LabStepService labStepService,
            LabAnswerService labAnswerService,
            LabReportService labReportService) {
        this.labConfigService = labConfigService;
        this.labStepService = labStepService;
        this.labAnswerService = labAnswerService;
        this.labReportService = labReportService;
    }


    public record PreviewAnswerImageResult(String contentType, byte[] bytes) {
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherLabs(CurrentUser currentUser) {
        return labConfigService.listTeacherLabs(currentUser);
    }


    @Transactional
    /**
     * 教师创建实验。
     *
     * <p>这里体现了典型的后端分层：controller 只转发请求，service 负责校验教师是否拥有目标班级，
     * 再把 DTO 中的业务字段映射到 {@link Lab} 实体并落库。</p>
     */
    public Map<String, Object> createLab(CurrentUser currentUser, LabRequests.CreateLabRequest request) {
        return labConfigService.createLab(currentUser, request);
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
        return labConfigService.copyLab(currentUser, sourceLabId, request);
    }


    @Transactional
    public Map<String, Object> updateLab(CurrentUser currentUser, Long labId, LabRequests.UpdateLabRequest request) {
        return labConfigService.updateLab(currentUser, labId, request);
    }


    @Transactional
    public Map<String, Object> changeLabStatus(CurrentUser currentUser, Long labId, LabRequests.ChangeLabStatusRequest request) {
        return labConfigService.changeLabStatus(currentUser, labId, request);
    }


    @Transactional
    public Map<String, Object> releaseScores(CurrentUser currentUser, Long labId) {
        return labConfigService.releaseScores(currentUser, labId);
    }


    @Transactional(readOnly = true)
    /**
     * 学生查看实验详情。
     *
     * <p>返回结果会把实验基础信息、学生当前提交状态、步骤列表、已保存答案以及分数可见性一起组装好，
     * 供前端一次性渲染页面。</p>
     */
    public Map<String, Object> getStudentLabDetail(CurrentUser currentUser, Long labId) {
        return labConfigService.getStudentLabDetail(currentUser, labId);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherSteps(CurrentUser currentUser, Long labId) {
        return labStepService.listTeacherSteps(currentUser, labId);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBlankItems(CurrentUser currentUser, Long labId) {
        return labStepService.listBlankItems(currentUser, labId);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getBlankItemAnswerDistribution(CurrentUser currentUser, Long labId, Long itemId) {
        return labStepService.getBlankItemAnswerDistribution(currentUser, labId, itemId);
    }


    @Transactional
    public Map<String, Object> saveBlankAcceptedAnswers(CurrentUser currentUser, Long labId, LabRequests.SaveBlankAcceptedAnswersRequest request) {
        return labStepService.saveBlankAcceptedAnswers(currentUser, labId, request);
    }


    @Transactional
    public Map<String, Object> createStep(CurrentUser currentUser, Long labId, LabRequests.CreateStepRequest request) {
        return labStepService.createStep(currentUser, labId, request);
    }


    @Transactional
    public Map<String, Object> updateStep(CurrentUser currentUser, Long stepId, LabRequests.UpdateStepRequest request) {
        return labStepService.updateStep(currentUser, stepId, request);
    }


    @Transactional
    public Map<String, Object> deleteStep(CurrentUser currentUser, Long stepId) {
        return labStepService.deleteStep(currentUser, stepId);
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
        return labReportService.listTeacherReports(currentUser, query);
    }


    /**
     * 教师端实验报告导航队列（全量、轻量）。
     *
     * <p>报告详情页与批量批改页需要基于全部报告做"上一份 / 下一份"跳转，
     * 分页接口会截断队列，因此单独提供无分页的全量列表，行结构与分页接口完全一致。</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeacherReportQueue(CurrentUser currentUser) {
        return labReportService.listTeacherReportQueue(currentUser);
    }


    /**
     * 教师端实验报告全局统计（不受列表筛选影响）。
     *
     * <p>队列页头部的"报告总量 / 待批改 / 已批改"三张卡需要全量口径，
     * 分页结果只能看到当前页，因此单独用一条分组计数查询提供。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherReportStats(CurrentUser currentUser) {
        return labReportService.getTeacherReportStats(currentUser);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherReportDetail(CurrentUser currentUser, Long reportId) {
        return labReportService.getTeacherReportDetail(currentUser, reportId);
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
        return labReportService.getTeacherReportBehavior(currentUser, reportId);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherLabReportView(CurrentUser currentUser, Long labId, Long studentId) {
        return labReportService.getTeacherLabReportView(currentUser, labId, studentId);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getSubmissionOverview(CurrentUser currentUser, Long labId) {
        return labReportService.getSubmissionOverview(currentUser, labId);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentLabSubmissions(CurrentUser currentUser, Long studentId) {
        return labAnswerService.listStudentLabSubmissions(currentUser, studentId);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getLabItemAnswers(CurrentUser currentUser, Long labId, Long itemId) {
        return labStepService.getLabItemAnswers(currentUser, labId, itemId);
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStudentLabs(CurrentUser currentUser) {
        return labConfigService.listStudentLabs(currentUser);
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
        return labAnswerService.saveAnswer(currentUser, labId, stepId, request);
    }


    @Transactional
    public Map<String, Object> uploadAnswerImage(CurrentUser currentUser,
                                                 Long labId,
                                                 Long stepId,
                                                 MultipartFile file) {
        return labAnswerService.uploadAnswerImage(currentUser, labId, stepId, file);
    }


    @Transactional(readOnly = true)
    public PreviewAnswerImageResult previewAnswerImage(CurrentUser currentUser, String path) {
        return labAnswerService.previewAnswerImage(currentUser, path);
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
        return labAnswerService.submitLab(currentUser, labId, request);
    }


    @Transactional
    /**
     * 教师整份批改实验报告。
     *
     * <p>这里适合解释“教师评分为什么不直接改 submission 总分”：
     * 系统先逐条更新每一步答案的得分来源与评语，再汇总总分，最后同步成绩记录。</p>
     */
    public Map<String, Object> gradeReport(CurrentUser currentUser, Long reportId, LabRequests.GradeLabReportRequest request) {
        return labReportService.gradeReport(currentUser, reportId, request);
    }


    @Transactional
    /**
     * 教师确认某一步骤分数。
     *
     * <p>如果接受系统推荐分，则 scoreSource 会标记为 AUTO_ACCEPTED；
     * 如果教师手工给分，则标记为 TEACHER。随后重新刷新整份提交的评分状态。</p>
     */
    public Map<String, Object> confirmStepScore(CurrentUser currentUser, Long reportId, LabRequests.ConfirmStepScoreRequest request) {
        return labReportService.confirmStepScore(currentUser, reportId, request);
    }


    @Transactional
    /**
     * 教师将单条已提交或已批改实验报告打回至可编辑草稿态。
     *
     * <p>保留学生作答内容，清除提交时间与自动/逐步确认/终评的评分痕迹，使学生可再次保存并提交。
     * {@code GRADED} 打回时额外删除 {@code score_record}。</p>
     */
    public Map<String, Object> returnReport(CurrentUser currentUser, Long reportId) {
        return labReportService.returnReport(currentUser, reportId);
    }
}
