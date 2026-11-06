package com.opencode.teachingplatform.plagiarism.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.PlagiarismStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.plagiarism.entity.PlagiarismTask;
import com.opencode.teachingplatform.plagiarism.repository.PlagiarismTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Locale;

@Service
/**
 * 查重任务应用服务。
 *
 * <p>它不负责真正计算文本相似度，而是负责三类工作：
 * 1. 查询并组装查重任务列表 / 详情视图；
 * 2. 根据当前用户角色做权限过滤；
 * 3. 处理教师复核后的状态更新。</p>
 *
 * <p>这里同时混用了 JPA Repository 和 JdbcTemplate：前者读取查重任务主表，
 * 后者跨作业/实验/考试表反查活动标题与归属教师。</p>
 */
public class PlagiarismService {

    private final PlagiarismTaskRepository plagiarismTaskRepository;
    private final JdbcTemplate jdbcTemplate;

    public PlagiarismService(PlagiarismTaskRepository plagiarismTaskRepository, JdbcTemplate jdbcTemplate) {
        this.plagiarismTaskRepository = plagiarismTaskRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 教师查看其有权限访问的查重任务列表。
     */
    public List<Map<String, Object>> listTasks(CurrentUser teacher) {
        List<PlagiarismTask> tasks = teacher.role() == UserRole.TEACHER
                ? plagiarismTaskRepository.findAll().stream().filter(task -> teacherOwnsTask(teacher, task)).toList()
                : plagiarismTaskRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlagiarismTask task : tasks) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", task.getId());
            view.put("businessType", task.getBusinessType().name());
            view.put("businessId", task.getBusinessId());
            view.put("studentId", task.getStudentId());
            view.put("studentName", resolveStudentName(task.getStudentId()));
            view.put("businessName", resolveBusinessName(task.getBusinessType(), task.getBusinessId()));
            view.put("similarityRate", task.getSimilarityRate());
            view.put("plagiarismRate", task.getSimilarityRate());
            view.put("riskLevel", riskLevel(task.getSimilarityRate()));
            view.put("topMatchSummary", task.getTopMatchSummary());
            view.put("status", task.getStatus().name());
            view.put("reviewStatus", task.getStatus().name());
            view.put("review", reviewPayload(task));
            view.put("createdAt", task.getCreatedAt() == null ? null : task.getCreatedAt().toString());
            result.add(view);
        }
        return result;
    }

    /**
     * 查询单条查重任务详情。
     *
     * <p>service 会先做“学生只能看自己的、教师只能看自己课程下的”权限判断，
     * 再把任务主表字段整理为前端直接可用的视图结构。</p>
     */
    public Map<String, Object> getTaskDetail(CurrentUser user, Long id) {
        PlagiarismTask task = plagiarismTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "查重任务不存在"));

        // 角色白名单 + 归属校验：只允许任务归属学生本人或任务所属活动的创建教师。
        switch (user.role()) {
            case STUDENT -> {
                if (!Objects.equals(task.getStudentId(), user.id())) {
                    throw new BusinessException(40300, "无权限访问该查重记录");
                }
            }
            case TEACHER -> {
                if (!teacherOwnsTask(user, task)) {
                    throw new BusinessException(40300, "无权限访问该查重记录");
                }
            }
            default -> throw new BusinessException(40300, "无权限访问该查重记录");
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("taskId", task.getId());
        view.put("businessType", task.getBusinessType().name());
        view.put("businessId", task.getBusinessId());
        view.put("studentId", task.getStudentId());
        view.put("studentName", resolveStudentName(task.getStudentId()));
        view.put("businessName", resolveBusinessName(task.getBusinessType(), task.getBusinessId()));
        view.put("similarityRate", task.getSimilarityRate());
        view.put("plagiarismRate", task.getSimilarityRate());
        view.put("riskLevel", riskLevel(task.getSimilarityRate()));
        view.put("topMatchSummary", task.getTopMatchSummary());
        view.put("rawResultJson", task.getRawResultJson());
        view.put("reviewStatus", task.getStatus().name());
        view.put("review", reviewPayload(task));
        view.put("createdAt", task.getCreatedAt() == null ? null : task.getCreatedAt().toString());
        return view;
    }

    /**
     * 教师复核查重结果。
     *
     * <p>当前实现没有单独的 review 表，而是把复核结论合并回任务状态与摘要文本中。</p>
     */
    public Map<String, Object> reviewTask(CurrentUser teacher, Long id, String reviewConclusion, String reviewComment) {
        PlagiarismTask task = plagiarismTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "查重任务不存在"));
        if (teacher.role() == UserRole.TEACHER && !teacherOwnsTask(teacher, task)) {
            throw new BusinessException(40300, "无权限访问该查重记录");
        }

        task.setStatus(PlagiarismStatus.REVIEWED);
        String normalizedComment = reviewComment == null ? "" : reviewComment.trim();
        String normalizedConclusion = normalizeReviewConclusion(reviewConclusion, task.getSimilarityRate());
        if (!normalizedComment.isBlank()) {
            task.setTopMatchSummary(buildReviewedSummary(task.getTopMatchSummary(), normalizedConclusion, normalizedComment));
        }
        plagiarismTaskRepository.save(task);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewed", true);
        result.put("status", task.getStatus().name());
        result.put("reviewStatus", task.getStatus().name());
        result.put("review", reviewPayload(normalizedConclusion, normalizedComment, task.getStatus().name(), task.getUpdatedAt()));
        return result;
    }

    private String riskLevel(Double similarityRate) {
        if (similarityRate == null) {
            return "UNKNOWN";
        }
        if (similarityRate >= 40D) {
            return "HIGH";
        }
        if (similarityRate >= 20D) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Map<String, Object> reviewPayload(PlagiarismTask task) {
        String comment = extractReviewComment(task.getTopMatchSummary());
        String conclusion = extractReviewConclusion(task.getTopMatchSummary(), task.getSimilarityRate());
        return reviewPayload(conclusion, comment, task.getStatus().name(), task.getUpdatedAt());
    }

    private Map<String, Object> reviewPayload(String conclusion, String comment, String status, java.time.OffsetDateTime reviewedAt) {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("status", status);
        review.put("conclusion", conclusion);
        review.put("comment", comment == null || comment.isBlank() ? null : comment);
        review.put("reviewedAt", reviewedAt == null ? null : reviewedAt.toString());
        review.put("reviewStatus", status);
        review.put("reviewConclusion", conclusion);
        review.put("reviewComment", comment == null || comment.isBlank() ? null : comment);
        return review;
    }

    private String normalizeReviewConclusion(String reviewConclusion, Double similarityRate) {
        String normalized = reviewConclusion == null ? "" : reviewConclusion.trim().toUpperCase(Locale.ROOT);
        if (!normalized.isBlank()) {
            return normalized;
        }
        if (similarityRate == null) {
            return null;
        }
        if (similarityRate >= 40D) {
            return "HIGH_RISK_CONFIRMED";
        }
        if (similarityRate >= 20D) {
            return "MEDIUM_RISK_CONFIRMED";
        }
        return "LOW_RISK_CONFIRMED";
    }

    private String buildReviewedSummary(String original, String conclusion, String reviewComment) {
        String base = original == null ? "" : original.replaceAll("\\s*\\[教师复核].*$", "").trim();
        String suffix = "[教师复核][" + conclusion + "] " + reviewComment;
        return base.isBlank() ? suffix : base + " " + suffix;
    }

    private String extractReviewComment(String summary) {
        if (summary == null) {
            return null;
        }
        int marker = summary.indexOf("[教师复核]");
        if (marker < 0) {
            return null;
        }
        String reviewed = summary.substring(marker).replaceFirst("^\\[教师复核](\\[[^]]+])?\\s*", "").trim();
        return reviewed.isBlank() ? null : reviewed;
    }

    private String extractReviewConclusion(String summary, Double similarityRate) {
        if (summary != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[教师复核]\\[([^]]+)]").matcher(summary);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (similarityRate == null) {
            return null;
        }
        if (similarityRate >= 40D) {
            return "HIGH_RISK_PENDING";
        }
        if (similarityRate >= 20D) {
            return "MEDIUM_RISK_PENDING";
        }
        return "LOW_RISK_PENDING";
    }

    private String resolveStudentName(Long studentId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT display_name FROM sys_user WHERE id = ?",
                    String.class, studentId);
        } catch (Exception e) {
            return "未知学生";
        }
    }

    private String resolveBusinessName(BusinessType businessType, Long businessId) {
        try {
            return switch (businessType) {
                case HOMEWORK -> {
                    // businessId is the homework_submission id, look up homework title through submission
                    Long homeworkId = jdbcTemplate.queryForObject(
                            "SELECT homework_id FROM homework_submission WHERE id = ?",
                            Long.class, businessId);
                    yield jdbcTemplate.queryForObject(
                            "SELECT title FROM homework WHERE id = ?",
                            String.class, homeworkId);
                }
                case LAB -> {
                    Long labId = jdbcTemplate.queryForObject(
                            "SELECT lab_id FROM lab_submission WHERE id = ?",
                            Long.class, businessId);
                    yield jdbcTemplate.queryForObject(
                            "SELECT title FROM lab WHERE id = ?",
                            String.class, labId);
                }
                case EXAM -> {
                    Long examId = jdbcTemplate.queryForObject(
                            "SELECT exam_id FROM exam_submission WHERE id = ?",
                            Long.class, businessId);
                    yield jdbcTemplate.queryForObject(
                            "SELECT title FROM exam WHERE id = ?",
                            String.class, examId);
                }
            };
        } catch (Exception e) {
            return "未知活动";
        }
    }

    private boolean teacherOwnsTask(CurrentUser teacher, PlagiarismTask task) {
        if (teacher.role() != UserRole.TEACHER) {
            return false;
        }
        return switch (task.getBusinessType()) {
            case HOMEWORK -> ownsHomeworkSubmission(teacher.id(), task.getBusinessId());
            case LAB -> ownsLabSubmission(teacher.id(), task.getBusinessId());
            case EXAM -> ownsExamSubmission(teacher.id(), task.getBusinessId());
        };
    }

    private boolean ownsHomeworkSubmission(Long teacherId, Long submissionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM homework_submission hs JOIN homework h ON hs.homework_id = h.id WHERE hs.id = ? AND h.created_by = ?",
                Integer.class,
                submissionId,
                teacherId
        );
        return count != null && count > 0;
    }

    private boolean ownsLabSubmission(Long teacherId, Long submissionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM lab_submission ls JOIN lab l ON ls.lab_id = l.id WHERE ls.id = ? AND l.created_by = ?",
                Integer.class,
                submissionId,
                teacherId
        );
        return count != null && count > 0;
    }

    private boolean ownsExamSubmission(Long teacherId, Long submissionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM exam_submission es JOIN exam e ON es.exam_id = e.id WHERE es.id = ? AND e.created_by = ?",
                Integer.class,
                submissionId,
                teacherId
        );
        return count != null && count > 0;
    }
}
