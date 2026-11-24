package com.opencode.teachingplatform.analysis.service;

import com.opencode.teachingplatform.analysis.service.StudentScoreVisibilityResolver.VisibleScore;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.entity.Exam;
import com.opencode.teachingplatform.exam.repository.ExamRepository;
import com.opencode.teachingplatform.grading.BusinessType;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import com.opencode.teachingplatform.lab.entity.Lab;
import com.opencode.teachingplatform.lab.repository.LabRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Service
public class AnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final StudentScoreVisibilityResolver visibilityResolver;
    private final LabRepository labRepository;
    private final HomeworkRepository homeworkRepository;
    private final ExamRepository examRepository;
    private final ClassAccessService classAccessService;

    public AnalysisService(JdbcTemplate jdbcTemplate,
                           StudentScoreVisibilityResolver visibilityResolver,
                           LabRepository labRepository,
                           HomeworkRepository homeworkRepository,
                           ExamRepository examRepository,
                           ClassAccessService classAccessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.visibilityResolver = visibilityResolver;
        this.labRepository = labRepository;
        this.homeworkRepository = homeworkRepository;
        this.examRepository = examRepository;
        this.classAccessService = classAccessService;
    }

    public Map<String, Object> getDashboard(CurrentUser teacher) {
        List<ParticipationSnapshot> snapshots = buildParticipationRows(teacher.id(), teacher.schoolId());
        return buildDashboard(teacher, snapshots);
    }

    private Map<String, Object> buildDashboard(CurrentUser teacher, List<ParticipationSnapshot> snapshots) {
        Long teacherId = teacher.id();

        // activityCount: labs + homeworks + exams created by this teacher
        int labCount = count("SELECT COUNT(*) FROM t_experiment WHERE created_by = ? AND school_id = ?", teacherId, teacher.schoolId());
        int homeworkCount = count("SELECT COUNT(*) FROM homework WHERE created_by = ? AND school_id = ?", teacherId, teacher.schoolId());
        int examCount = count("SELECT COUNT(*) FROM exam WHERE created_by = ? AND school_id = ?", teacherId, teacher.schoolId());
        int activityCount = labCount + homeworkCount + examCount;

        // pendingGradeCount: SUBMITTED experiment_submissions + homework_submissions + exam_submissions for teacher's activities
        int pendingLab = count(
                "SELECT COUNT(*) FROM experiment_submission es JOIN t_experiment e ON es.experiment_id = e.experiment_id " +
                        "WHERE e.created_by = ? AND es.submit_status = 'SUBMITTED' AND e.school_id = ?",
                teacherId, teacher.schoolId());
        int pendingHomework = count(
                "SELECT COUNT(*) FROM homework_submission hs JOIN homework h ON hs.homework_id = h.id WHERE h.created_by = ? AND hs.submit_status = 'SUBMITTED' AND h.school_id = ?",
                teacherId, teacher.schoolId());
        int pendingExam = count(
                "SELECT COUNT(*) FROM exam_submission es JOIN exam e ON es.exam_id = e.id WHERE e.created_by = ? AND (es.status = 'SUBMITTED' OR es.status = 'AUTO_SUBMITTED') AND e.school_id = ?",
                teacherId, teacher.schoolId());
        int pendingGradeCount = pendingLab + pendingHomework + pendingExam;

        // averageScore: avg score from score_record for teacher's classes
        Double avgScore = jdbcTemplate.queryForObject(
                "SELECT AVG(sr.score) FROM score_record sr " +
                        "JOIN class_room cr ON sr.class_id = cr.id " +
                        "JOIN class_teacher ct ON ct.class_id = cr.id AND ct.teacher_user_id = ? " +
                        "WHERE cr.school_id = ?",
                Double.class, teacherId, teacher.schoolId());
        double averageScore = avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0;

        List<Map<String, Object>> summaryCards = new ArrayList<>(List.of(
                summaryCard("activityCount", "教学活动总数", activityCount, "实验、作业与考试总量"),
                summaryCard("pendingGradeCount", "待批改任务", pendingGradeCount, "需要教师处理的提交"),
                summaryCard("averageScore", "平均成绩", averageScore, "当前教师班级平均分")
        ));
        int totalNotStarted = snapshots.stream()
                .mapToInt(snapshot -> snapshot.expectedCount - snapshot.participatedCount)
                .sum();
        summaryCards.add(summaryCard("notStartedCount", "未开始人次", totalNotStarted,
                "我创建活动中尚无提交行的学生人次"));

        // trend: last 7 days — group in Java so H2 (UT/path-b-h2) and MySQL (path-b) both work
        List<Map<String, Object>> trend = loadScoreTrendLast7Days(teacher.id(), teacher.schoolId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summaryCards", summaryCards);
        result.put("trend", trend);
        result.put("recentTasks", buildTeacherRecentTasks(teacherId, teacher.schoolId()));
        result.put("quickLinks", List.of(
                quickLink("实验报告批改", "/teacher/lab-reports", pendingLab),
                quickLink("作业管理", "/teacher/homeworks", pendingHomework),
                quickLink("考试结果", "/teacher/exams", pendingExam)
        ));
        return result;
    }

    public Map<String, Object> getTeacherOverview(CurrentUser teacher) {
        List<ParticipationSnapshot> snapshots = buildParticipationRows(teacher.id(), teacher.schoolId());
        Map<String, Object> dashboard = buildDashboard(teacher, snapshots);

        List<Map<String, Object>> sectionComparison = jdbcTemplate.query(
                "SELECT cr.name AS class_name, ROUND(AVG(sr.score), 1) AS avg_score, COUNT(sr.id) AS graded_count " +
                        "FROM score_record sr JOIN class_room cr ON sr.class_id = cr.id " +
                        "JOIN class_teacher ct ON ct.class_id = cr.id AND ct.teacher_user_id = ? " +
                        "WHERE cr.school_id = ? GROUP BY cr.id, cr.name ORDER BY avg_score DESC",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("className", rs.getString("class_name"));
                    row.put("avgScore", rs.getDouble("avg_score"));
                    row.put("gradedCount", rs.getInt("graded_count"));
                    return row;
                },
                teacher.id(),
                teacher.schoolId()
        );

        List<Map<String, Object>> businessBreakdown = jdbcTemplate.query(
                "SELECT business_type, ROUND(AVG(score), 1) AS avg_score, COUNT(*) AS score_count " +
                        "FROM score_record sr JOIN class_room cr ON sr.class_id = cr.id " +
                        "JOIN class_teacher ct ON ct.class_id = cr.id AND ct.teacher_user_id = ? " +
                        "WHERE cr.school_id = ? GROUP BY business_type ORDER BY business_type",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String businessType = rs.getString("business_type");
                    row.put("businessType", businessType);
                    row.put("label", businessTypeLabel(businessType));
                    row.put("avgScore", rs.getDouble("avg_score"));
                    row.put("scoreCount", rs.getInt("score_count"));
                    return row;
                },
                teacher.id(),
                teacher.schoolId()
        );

        List<Map<String, Object>> insights = new ArrayList<>();
        Number averageScore = (Number) ((List<?>) dashboard.get("summaryCards")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(card -> "averageScore".equals(card.get("key")))
                .map(card -> (Number) card.get("value"))
                .findFirst()
                .orElse(0.0);
        insights.add(insight("positive", "平均成绩稳定", String.format(Locale.US, "当前班级平均成绩 %.1f 分。", averageScore.doubleValue())));
        if (!sectionComparison.isEmpty()) {
            Map<String, Object> bestSection = sectionComparison.get(0);
            insights.add(insight("highlight", "最佳班级表现", bestSection.get("className") + " 当前平均分领先。"));
        }
        if (!businessBreakdown.isEmpty()) {
            Map<String, Object> weakest = businessBreakdown.stream()
                    .min(Comparator.comparing(item -> ((Number) item.get("avgScore")).doubleValue()))
                    .orElse(businessBreakdown.get(0));
            insights.add(insight("warning", "重点关注项", weakest.get("label") + " 平均分相对偏低，建议优先复盘。"));
        }

        List<Map<String, Object>> participationOverview = new ArrayList<>();
        for (String businessType : List.of("LAB", "HOMEWORK", "EXAM")) {
            List<ParticipationSnapshot> typeSnapshots = snapshots.stream()
                    .filter(snapshot -> businessType.equals(snapshot.businessType))
                    .toList();
            participationOverview.add(toParticipationOverviewRow(businessType, typeSnapshots));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summaryCards", dashboard.get("summaryCards"));
        result.put("trend", dashboard.get("trend"));
        result.put("sectionComparison", sectionComparison);
        result.put("businessBreakdown", businessBreakdown);
        result.put("insights", insights);
        result.put("participationOverview", participationOverview);
        return result;
    }

    public List<Map<String, Object>> getMyScores(CurrentUser student) {
        return visibilityResolver.resolve(student.id(), student.schoolId()).stream()
                .map(this::toScoreItem)
                .toList();
    }

    public Map<String, Object> getMyScoreOverview(CurrentUser student) {
        List<VisibleScore> records = visibilityResolver.resolve(student.id(), student.schoolId());

        double averageScore = records.isEmpty() ? 0.0 : roundOne(
                records.stream().mapToDouble(VisibleScore::score).average().orElse(0.0));
        double highestScore = records.isEmpty() ? 0.0 : roundOne(
                records.stream().mapToDouble(VisibleScore::score).max().orElse(0.0));
        // 完成率口径：分母 = 三类活动 PUBLISHED / CLOSED；分子按 (businessType, businessId) 去重后命中分母
        Set<String> activeKeys = loadActiveBusinessKeys(student.id(), student.schoolId());
        int completedItems = (int) records.stream()
                .map(record -> record.businessType().name() + "#" + record.businessId())
                .distinct()
                .filter(activeKeys::contains)
                .count();
        int totalItems = count(
                "SELECT COUNT(*) FROM t_experiment e JOIN class_member cm ON cm.class_id = e.class_id " +
                        "WHERE cm.student_user_id = ? AND e.status IN ('PUBLISHED','CLOSED') AND e.school_id = ?",
                student.id(), student.schoolId()
        ) + count(
                "SELECT COUNT(*) FROM homework h JOIN class_member cm ON cm.class_id = h.class_id " +
                        "WHERE cm.student_user_id = ? AND h.status IN ('PUBLISHED','CLOSED') AND h.school_id = ?",
                student.id(), student.schoolId()
        ) + count(
                "SELECT COUNT(*) FROM exam ex JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "WHERE cm.student_user_id = ? AND ex.status IN ('PUBLISHED','CLOSED') AND ex.school_id = ?",
                student.id(), student.schoolId()
        );

        // 按业务类型英文名升序聚合（EXAM → HOMEWORK → LAB），只包含可见成绩
        Map<String, List<Double>> scoresByType = new TreeMap<>();
        for (VisibleScore record : records) {
            scoresByType.computeIfAbsent(record.businessType().name(), ignored -> new ArrayList<>())
                    .add(record.score());
        }
        List<Map<String, Object>> groupedScores = new ArrayList<>();
        scoresByType.forEach((businessType, scores) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessType", businessType);
            row.put("label", businessTypeLabel(businessType));
            row.put("avgScore", roundOne(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)));
            row.put("scoreCount", scores.size());
            groupedScores.add(row);
        });

        List<Map<String, Object>> recentRecords = records.stream()
                .limit(8)
                .map(this::toScoreItem)
                .toList();

        List<Map<String, Object>> summaryCards = List.of(
                summaryCard("averageScore", "平均成绩", averageScore, "来自当前学生所有已评分记录"),
                summaryCard("completedItems", "已评分项目", completedItems, "已产生分数的实验、作业与考试数量"),
                summaryCard("highestScore", "最高分", highestScore, "当前最高单项得分"),
                summaryCard("completionRate", "完成进度", totalItems > 0 ? roundOne(completedItems * 100.0 / totalItems) : 0.0, "按所有关联学习任务估算的完成率")
        );

        List<Map<String, Object>> feedbackNotes = new ArrayList<>();
        if (!groupedScores.isEmpty()) {
            Map<String, Object> strongest = groupedScores.stream()
                    .max(Comparator.comparing(item -> ((Number) item.get("avgScore")).doubleValue()))
                    .orElse(groupedScores.get(0));
            feedbackNotes.add(insight("positive", "优势项", strongest.get("label") + " 表现最佳，可继续保持。"));
            Map<String, Object> weakest = groupedScores.stream()
                    .min(Comparator.comparing(item -> ((Number) item.get("avgScore")).doubleValue()))
                    .orElse(groupedScores.get(0));
            feedbackNotes.add(insight("warning", "待提升项", weakest.get("label") + " 建议优先安排复盘。"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summaryCards", summaryCards);
        result.put("groupedScores", groupedScores);
        result.put("recentRecords", recentRecords);
        result.put("feedbackNotes", feedbackNotes);
        result.put("completedItems", completedItems);
        result.put("totalItems", totalItems);
        return result;
    }

    public Map<String, Object> getStudentDashboard(CurrentUser student) {
        Long studentId = student.id();

        // 教学资料属于学校级资源，除全局可见外还必须限定在本校范围内，避免跨校元数据泄露
        Long schoolId = student.schoolId();
        int materialCount = schoolId == null
                ? 0
                : count("SELECT COUNT(*) FROM course_material WHERE visibility = 'ALL' AND school_id = ?", schoolId);
        List<Map<String, Object>> recentMaterials = schoolId == null
                ? List.of()
                : jdbcTemplate.query(
                        "SELECT id, title, category, file_name FROM course_material WHERE visibility = 'ALL' AND school_id = ? ORDER BY updated_at DESC LIMIT 4",
                        (rs, rowNum) -> Map.<String, Object>of(
                                "id", rs.getLong("id"),
                                "title", rs.getString("title"),
                                "category", rs.getString("category"),
                                "fileName", rs.getString("file_name")
                        ),
                        schoolId
                );
        int pendingLabCount = count(
                "SELECT COUNT(*) FROM t_experiment e JOIN class_member cm ON cm.class_id = e.class_id " +
                        "LEFT JOIN experiment_submission es ON es.experiment_id = e.experiment_id AND es.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND e.status = 'PUBLISHED' " +
                        "AND (es.id IS NULL OR es.submit_status NOT IN ('SUBMITTED', 'GRADED')) AND e.school_id = ?",
                studentId, schoolId);
        int pendingHomeworkCount = count(
                "SELECT COUNT(*) FROM homework h JOIN class_member cm ON cm.class_id = h.class_id " +
                        "LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND h.status = 'PUBLISHED' " +
                        "AND (hs.id IS NULL OR hs.submit_status NOT IN ('SUBMITTED', 'GRADED')) AND h.school_id = ?",
                studentId, schoolId);
        int pendingExamCount = count(
                "SELECT COUNT(*) FROM exam ex JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "LEFT JOIN exam_submission exs ON exs.exam_id = ex.id AND exs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND ex.status = 'PUBLISHED' " +
                        "AND (exs.id IS NULL OR exs.status NOT IN ('SUBMITTED', 'AUTO_SUBMITTED', 'GRADED')) AND ex.school_id = ?",
                studentId, schoolId);
        int pendingTaskCount = pendingLabCount + pendingHomeworkCount + pendingExamCount;

        // 学生端平均值与最近反馈统一走可见性解析器，只统计当前学生此刻可见的成绩
        List<VisibleScore> visibleScores = visibilityResolver.resolve(studentId, schoolId);
        // 一次性构建最近反馈，供“最近反馈”与“我的成绩”角标复用，避免重复查询
        List<Map<String, Object>> recentFeedback = buildRecentFeedback(visibleScores);
        double averageScore = visibleScores.isEmpty() ? 0.0 : roundOne(
                visibleScores.stream().mapToDouble(VisibleScore::score).average().orElse(0.0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summaryCards", List.of(
                summaryCard("pendingTasks", "待完成任务", pendingTaskCount, "实验、作业、考试待处理总数"),
                summaryCard("materials", "可访问资料", materialCount, "当前可见教学资料数量"),
                summaryCard("averageScore", "最近平均成绩", averageScore, "来自已评分记录")
        ));
        result.put("upcomingTasks", buildStudentUpcomingTasks(studentId, schoolId));
        result.put("recentFeedback", recentFeedback);
        result.put("recentMaterials", recentMaterials);
        result.put("quickLinks", List.of(
                quickLink("我的实验", "/student/labs", pendingLabCount),
                quickLink("我的作业", "/student/homeworks", pendingHomeworkCount),
                quickLink("我的考试", "/student/exams", pendingExamCount),
                quickLink("我的成绩", "/student/scores", recentFeedback.size())
        ));
        return result;
    }

    /**
     * 把可见成绩转换为成绩列表项，字段与顺序保持既有契约。
     */
    private Map<String, Object> toScoreItem(VisibleScore record) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.id());
        item.put("businessType", record.businessType().name());
        item.put("businessName", record.businessName());
        item.put("score", record.score());
        item.put("gradedAt", record.gradedAt());
        return item;
    }

    /**
     * 近 7 天成绩趋势：只统计本校且该教师所带班级的成绩记录。
     *
     * @param teacherId 当前教师用户 id
     * @param schoolId  当前教师所属学校 id
     */
    private List<Map<String, Object>> loadScoreTrendLast7Days(Long teacherId, Long schoolId) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(7);
        Map<LocalDate, List<Double>> scoresByDay = new TreeMap<>();
        jdbcTemplate.query(
                "SELECT sr.graded_at, sr.score FROM score_record sr " +
                        "JOIN class_room cr ON cr.id = sr.class_id " +
                        "WHERE sr.graded_at >= ? AND cr.school_id = ? " +
                        "AND EXISTS (SELECT 1 FROM class_teacher ct WHERE ct.class_id = sr.class_id AND ct.teacher_user_id = ?)",
                (rs, rowNum) -> {
                    var timestamp = rs.getTimestamp("graded_at");
                    if (timestamp != null) {
                        LocalDate day = timestamp.toLocalDateTime().toLocalDate();
                        scoresByDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(rs.getDouble("score"));
                    }
                    return null;
                },
                since, schoolId, teacherId
        );

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Double>> entry : scoresByDay.entrySet()) {
            List<Double> scores = entry.getValue();
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey().toString());
            row.put("scoreCount", scores.size());
            row.put("avgScore", Math.round(avg * 10.0) / 10.0);
            trend.add(row);
        }
        return trend;
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result != null ? result : 0;
    }

    /**
     * 构造当前学生可见分母的活跃业务键集合。
     *
     * <p>元素格式与成绩记录的业务键一致：{@code LAB#id} / {@code HOMEWORK#id} / {@code EXAM#id}。
     * 只统计状态为 {@code PUBLISHED} / {@code CLOSED} 的活动，草稿不计入分母。</p>
     *
     * @param studentId 学生用户 id
     * @return 活跃业务键集合
     */
    private Set<String> loadActiveBusinessKeys(Long studentId, Long schoolId) {
        Set<String> keys = new HashSet<>();
        keys.addAll(jdbcTemplate.queryForList(
                "SELECT CONCAT('LAB#', e.experiment_id) FROM t_experiment e " +
                        "JOIN class_member cm ON cm.class_id = e.class_id " +
                        "WHERE cm.student_user_id = ? AND e.status IN ('PUBLISHED','CLOSED') AND e.school_id = ?",
                String.class, studentId, schoolId));
        keys.addAll(jdbcTemplate.queryForList(
                "SELECT CONCAT('HOMEWORK#', h.id) FROM homework h " +
                        "JOIN class_member cm ON cm.class_id = h.class_id " +
                        "WHERE cm.student_user_id = ? AND h.status IN ('PUBLISHED','CLOSED') AND h.school_id = ?",
                String.class, studentId, schoolId));
        keys.addAll(jdbcTemplate.queryForList(
                "SELECT CONCAT('EXAM#', ex.id) FROM exam ex " +
                        "JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "WHERE cm.student_user_id = ? AND ex.status IN ('PUBLISHED','CLOSED') AND ex.school_id = ?",
                String.class, studentId, schoolId));
        return keys;
    }

    private static final class ParticipationSnapshot {
        final String businessType;
        final Long businessId;
        final String businessTitle;
        final OffsetDateTime createdAt;
        final Set<Long> studentIds = new HashSet<>();
        int expectedCount;
        int participatedCount;
        int completedCount;

        ParticipationSnapshot(String businessType, Long businessId, String businessTitle, OffsetDateTime createdAt) {
            this.businessType = businessType;
            this.businessId = businessId;
            this.businessTitle = businessTitle;
            this.createdAt = createdAt;
        }
    }

    private List<ParticipationSnapshot> buildParticipationRows(Long teacherId, Long schoolId) {
        String sql = "SELECT 'LAB' AS business_type, e.experiment_id AS business_id, e.experiment_name AS business_title, " +
                "e.class_id, e.created_at, cm.student_user_id, u.username AS student_no, u.display_name AS student_name, " +
                "es.id AS submission_id, es.submit_status AS submission_status " +
                "FROM t_experiment e " +
                "JOIN class_room cr ON cr.id = e.class_id AND cr.school_id = ? " +
                "LEFT JOIN class_member cm ON cm.class_id = e.class_id " +
                "LEFT JOIN sys_user u ON u.id = cm.student_user_id " +
                "LEFT JOIN experiment_submission es ON es.experiment_id = e.experiment_id AND es.student_id = cm.student_user_id " +
                "WHERE e.created_by = ? AND e.school_id = ? AND e.status IN ('PUBLISHED','CLOSED') " +
                "UNION ALL " +
                "SELECT 'HOMEWORK' AS business_type, h.id AS business_id, h.title AS business_title, " +
                "h.class_id, h.created_at, cm.student_user_id, u.username AS student_no, u.display_name AS student_name, " +
                "hs.id AS submission_id, hs.submit_status AS submission_status " +
                "FROM homework h " +
                "JOIN class_room cr ON cr.id = h.class_id AND cr.school_id = ? " +
                "LEFT JOIN class_member cm ON cm.class_id = h.class_id " +
                "LEFT JOIN sys_user u ON u.id = cm.student_user_id " +
                "LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = cm.student_user_id " +
                "WHERE h.created_by = ? AND h.school_id = ? AND h.status IN ('PUBLISHED','CLOSED') " +
                "UNION ALL " +
                "SELECT 'EXAM' AS business_type, ex.id AS business_id, ex.title AS business_title, " +
                "ex.class_id, ex.created_at, cm.student_user_id, u.username AS student_no, u.display_name AS student_name, " +
                "exs.id AS submission_id, exs.status AS submission_status " +
                "FROM exam ex " +
                "JOIN class_room cr ON cr.id = ex.class_id AND cr.school_id = ? " +
                "LEFT JOIN class_member cm ON cm.class_id = ex.class_id " +
                "LEFT JOIN sys_user u ON u.id = cm.student_user_id " +
                "LEFT JOIN exam_submission exs ON exs.exam_id = ex.id AND exs.student_id = cm.student_user_id " +
                "WHERE ex.created_by = ? AND ex.school_id = ? AND ex.status IN ('PUBLISHED','CLOSED')";
        Map<String, ParticipationSnapshot> byKey = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String businessType = rs.getString("business_type");
            long rawBusinessId = rs.getLong("business_id");
            if (rs.wasNull()) {
                return;
            }
            Long businessId = rawBusinessId;
            String businessTitle = rs.getString("business_title");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            String key = businessType + "#" + businessId;
            ParticipationSnapshot snapshot = byKey.computeIfAbsent(key, ignored -> new ParticipationSnapshot(
                    businessType, businessId, businessTitle, createdAt));
            long studentId = rs.getLong("student_user_id");
            if (rs.wasNull() || !snapshot.studentIds.add(studentId)) {
                return;
            }
            snapshot.expectedCount++;
            if (rs.getObject("submission_id", Long.class) != null) {
                snapshot.participatedCount++;
                if (isCompleted(businessType, rs.getString("submission_status"))) {
                    snapshot.completedCount++;
                }
            }
        }, schoolId, teacherId, schoolId, schoolId, teacherId, schoolId, schoolId, teacherId, schoolId);
        return new ArrayList<>(byKey.values());
    }

    private boolean isCompleted(String businessType, String submissionStatus) {
        if (submissionStatus == null) {
            return false;
        }
        return switch (businessType) {
            case "LAB", "HOMEWORK" -> "SUBMITTED".equals(submissionStatus) || "GRADED".equals(submissionStatus);
            case "EXAM" -> "SUBMITTED".equals(submissionStatus)
                    || "AUTO_SUBMITTED".equals(submissionStatus)
                    || "GRADED".equals(submissionStatus);
            default -> false;
        };
    }

    private Map<String, Object> toParticipationActivity(ParticipationSnapshot snapshot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("businessId", snapshot.businessId);
        row.put("businessTitle", snapshot.businessTitle);
        row.put("expectedCount", snapshot.expectedCount);
        row.put("participatedCount", snapshot.participatedCount);
        row.put("completedCount", snapshot.completedCount);
        row.put("notStartedCount", snapshot.expectedCount - snapshot.participatedCount);
        row.put("participationRate", snapshot.expectedCount == 0
                ? 0.0 : roundOne(snapshot.participatedCount * 100.0 / snapshot.expectedCount));
        row.put("completionRate", snapshot.expectedCount == 0
                ? 0.0 : roundOne(snapshot.completedCount * 100.0 / snapshot.expectedCount));
        return row;
    }

    private Map<String, Object> toParticipationOverviewRow(String businessType, List<ParticipationSnapshot> snapshots) {
        List<Map<String, Object>> activities = snapshots.stream()
                .sorted(Comparator
                        .comparing((ParticipationSnapshot snapshot) -> snapshot.createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(snapshot -> snapshot.businessId))
                .map(this::toParticipationActivity)
                .toList();
        int expectedCount = snapshots.stream().mapToInt(snapshot -> snapshot.expectedCount).sum();
        int participatedCount = snapshots.stream().mapToInt(snapshot -> snapshot.participatedCount).sum();
        int completedCount = snapshots.stream().mapToInt(snapshot -> snapshot.completedCount).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("businessType", businessType);
        row.put("label", businessTypeLabel(businessType));
        row.put("activityCount", activities.size());
        row.put("expectedCount", expectedCount);
        row.put("participatedCount", participatedCount);
        row.put("completedCount", completedCount);
        row.put("notStartedCount", expectedCount - participatedCount);
        row.put("participationRate", expectedCount == 0 ? 0.0 : roundOne(participatedCount * 100.0 / expectedCount));
        row.put("completionRate", expectedCount == 0 ? 0.0 : roundOne(completedCount * 100.0 / expectedCount));
        row.put("activities", activities);
        return row;
    }

    private List<Map<String, Object>> queryParticipationStudents(String businessType, Long schoolId, Long teacherId,
                                                                 Long businessId, Map<String, Object> detailContext) {
        String baseSql;
        String idColumn;
        switch (businessType) {
            case "LAB" -> {
                baseSql = "SELECT cr.name AS class_name, cm.student_user_id, u.username AS student_no, " +
                        "u.display_name AS student_name, es.id AS submission_id, es.submit_status AS submission_status, " +
                        "es.submitted_at AS submitted_at FROM t_experiment e " +
                        "JOIN class_room cr ON cr.id = e.class_id AND cr.school_id = ? " +
                        "LEFT JOIN class_member cm ON cm.class_id = e.class_id " +
                        "LEFT JOIN sys_user u ON u.id = cm.student_user_id " +
                        "LEFT JOIN experiment_submission es ON es.experiment_id = e.experiment_id AND es.student_id = cm.student_user_id " +
                        "WHERE e.created_by = ? AND e.school_id = ? AND e.status IN ('PUBLISHED','CLOSED')";
                idColumn = "e.experiment_id";
            }
            case "HOMEWORK" -> {
                baseSql = "SELECT cr.name AS class_name, cm.student_user_id, u.username AS student_no, " +
                        "u.display_name AS student_name, hs.id AS submission_id, hs.submit_status AS submission_status, " +
                        "hs.updated_at AS submitted_at FROM homework h " +
                        "JOIN class_room cr ON cr.id = h.class_id AND cr.school_id = ? " +
                        "LEFT JOIN class_member cm ON cm.class_id = h.class_id " +
                        "LEFT JOIN sys_user u ON u.id = cm.student_user_id " +
                        "LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = cm.student_user_id " +
                        "WHERE h.created_by = ? AND h.school_id = ? AND h.status IN ('PUBLISHED','CLOSED')";
                idColumn = "h.id";
            }
            case "EXAM" -> {
                baseSql = "SELECT cr.name AS class_name, cm.student_user_id, u.username AS student_no, " +
                        "u.display_name AS student_name, exs.id AS submission_id, exs.status AS submission_status, " +
                        "exs.submitted_at AS submitted_at FROM exam ex " +
                        "JOIN class_room cr ON cr.id = ex.class_id AND cr.school_id = ? " +
                        "LEFT JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "LEFT JOIN sys_user u ON u.id = cm.student_user_id " +
                        "LEFT JOIN exam_submission exs ON exs.exam_id = ex.id AND exs.student_id = cm.student_user_id " +
                        "WHERE ex.created_by = ? AND ex.school_id = ? AND ex.status IN ('PUBLISHED','CLOSED')";
                idColumn = "ex.id";
            }
            default -> throw new BusinessException(40000, "不支持的业务类型");
        }
        String sql = baseSql + " AND " + idColumn + " = ? ORDER BY u.username ASC";
        List<Map<String, Object>> students = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        jdbcTemplate.query(sql, rs -> {
            String className = rs.getString("class_name");
            if (className != null && !detailContext.containsKey("className")) {
                detailContext.put("className", className);
            }
            long studentId = rs.getLong("student_user_id");
            if (rs.wasNull() || !seen.add(studentId)) {
                return;
            }
            long rawSubmissionId = rs.getLong("submission_id");
            boolean hasSubmission = !rs.wasNull();
            String submissionStatus = hasSubmission ? rs.getString("submission_status") : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", studentId);
            row.put("studentNo", rs.getString("student_no"));
            row.put("studentName", rs.getString("student_name"));
            row.put("participation", hasSubmission ? "PARTICIPATED" : "NOT_STARTED");
            row.put("completion", isCompleted(businessType, submissionStatus) ? "COMPLETED" : "IN_PROGRESS");
            row.put("submissionId", hasSubmission ? rawSubmissionId : null);
            row.put("submitStatus", submissionStatus);
            row.put("submittedAt", hasSubmission ? rs.getObject("submitted_at", OffsetDateTime.class) : null);
            students.add(row);
        }, schoolId, teacherId, schoolId, businessId);
        students.sort(Comparator.comparing(row -> (String) row.get("studentNo")));
        return students;
    }

    public Map<String, Object> getTeacherParticipation(CurrentUser teacher, String businessType, Long businessId) {
        if (!List.of("LAB", "HOMEWORK", "EXAM").contains(businessType)) {
            throw new BusinessException(40000, "不支持的业务类型");
        }
        Long classId;
        String businessTitle;
        switch (businessType) {
            case "LAB" -> {
                Lab lab = labRepository.findByIdAndSchoolId(businessId, teacher.schoolId())
                        .orElseThrow(() -> new BusinessException(40400, "活动不存在"));
                if (!Objects.equals(lab.getCreatedBy(), teacher.id())) {
                    throw new BusinessException(40300, "无权限查看该活动");
                }
                classId = lab.getClassId();
                businessTitle = lab.getTitle();
            }
            case "HOMEWORK" -> {
                Homework homework = homeworkRepository.findByIdAndSchoolId(businessId, teacher.schoolId())
                        .orElseThrow(() -> new BusinessException(40400, "活动不存在"));
                if (!Objects.equals(homework.getCreatedBy(), teacher.id())) {
                    throw new BusinessException(40300, "无权限查看该活动");
                }
                classId = homework.getClassId();
                businessTitle = homework.getTitle();
            }
            case "EXAM" -> {
                Exam exam = examRepository.findByIdAndSchoolId(businessId, teacher.schoolId())
                        .orElseThrow(() -> new BusinessException(40400, "活动不存在"));
                if (!Objects.equals(exam.getCreatedBy(), teacher.id())) {
                    throw new BusinessException(40300, "无权限查看该活动");
                }
                classId = exam.getClassId();
                businessTitle = exam.getTitle();
            }
            default -> throw new BusinessException(40000, "不支持的业务类型");
        }
        classAccessService.assertTeacherAssignedToClass(teacher, classId);

        Map<String, Object> detailContext = new LinkedHashMap<>();
        List<Map<String, Object>> students = queryParticipationStudents(
                businessType, teacher.schoolId(), teacher.id(), businessId, detailContext);
        int expectedCount = students.size();
        int participatedCount = (int) students.stream()
                .filter(row -> "PARTICIPATED".equals(row.get("participation")))
                .count();
        int completedCount = (int) students.stream()
                .filter(row -> "COMPLETED".equals(row.get("completion")))
                .count();
        int notStartedCount = expectedCount - participatedCount;
        String className = (String) detailContext.get("className");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessType", businessType);
        result.put("businessId", businessId);
        result.put("businessTitle", businessTitle);
        result.put("classId", classId);
        result.put("className", className == null ? "" : className);
        result.put("expectedCount", expectedCount);
        result.put("participatedCount", participatedCount);
        result.put("completedCount", completedCount);
        result.put("notStartedCount", notStartedCount);
        result.put("participationRate", expectedCount == 0 ? 0.0 : roundOne(participatedCount * 100.0 / expectedCount));
        result.put("completionRate", expectedCount == 0 ? 0.0 : roundOne(completedCount * 100.0 / expectedCount));
        result.put("students", students);
        return result;
    }

    private Map<String, Object> summaryCard(String key, String title, Object value, String description) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("key", key);
        card.put("title", title);
        card.put("value", value);
        card.put("description", description);
        return card;
    }

    private Map<String, Object> quickLink(String label, String route, Object badge) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("label", label);
        link.put("route", route);
        link.put("badge", badge);
        return link;
    }

    private Map<String, Object> insight(String tone, String title, String description) {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("tone", tone);
        insight.put("title", title);
        insight.put("description", description);
        return insight;
    }

    private String businessTypeLabel(String businessType) {
        return switch (BusinessType.valueOf(businessType)) {
            case LAB -> "实验";
            case HOMEWORK -> "作业";
            case EXAM -> "考试";
        };
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private List<Map<String, Object>> buildTeacherRecentTasks(Long teacherId, Long schoolId) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.addAll(jdbcTemplate.query(
                "SELECT es.submitted_at AS event_time, e.experiment_name AS title, u.display_name AS actor " +
                        "FROM experiment_submission es JOIN t_experiment e ON es.experiment_id = e.experiment_id " +
                        "JOIN sys_user u ON u.id = es.student_id " +
                        "WHERE e.created_by = ? AND es.submit_status = 'SUBMITTED' AND e.school_id = ?",
                (rs, rowNum) -> teacherTask("实验报告待批改", rs.getString("title"), rs.getString("actor"), rs.getObject("event_time", OffsetDateTime.class), "/teacher/lab-reports"),
                teacherId, schoolId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT hs.updated_at AS event_time, h.title AS title, u.display_name AS actor " +
                        "FROM homework_submission hs JOIN homework h ON hs.homework_id = h.id JOIN sys_user u ON u.id = hs.student_id " +
                        "WHERE h.created_by = ? AND hs.submit_status = 'SUBMITTED' AND h.school_id = ?",
                (rs, rowNum) -> teacherTask("作业待批改", rs.getString("title"), rs.getString("actor"), rs.getObject("event_time", OffsetDateTime.class), "/teacher/homeworks"),
                teacherId, schoolId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT es.submitted_at AS event_time, e.title AS title, u.display_name AS actor " +
                        "FROM exam_submission es JOIN exam e ON es.exam_id = e.id JOIN sys_user u ON u.id = es.student_id " +
                        "WHERE e.created_by = ? AND (es.status = 'SUBMITTED' OR es.status = 'AUTO_SUBMITTED') AND e.school_id = ?",
                (rs, rowNum) -> teacherTask("考试结果待处理", rs.getString("title"), rs.getString("actor"), rs.getObject("event_time", OffsetDateTime.class), "/teacher/exams"),
                teacherId, schoolId
        ));
        return tasks.stream()
                .sorted(Comparator.comparing(task -> (OffsetDateTime) task.get("eventTime"), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
    }

    private Map<String, Object> teacherTask(String label, String title, String actor, OffsetDateTime eventTime, String route) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("label", label);
        task.put("title", title);
        task.put("actor", actor);
        task.put("eventTime", eventTime);
        task.put("route", route);
        return task;
    }

    private List<Map<String, Object>> buildStudentUpcomingTasks(Long studentId, Long schoolId) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.addAll(jdbcTemplate.query(
                "SELECT e.experiment_name AS title, e.end_at AS due_at FROM t_experiment e JOIN class_member cm ON cm.class_id = e.class_id " +
                        "LEFT JOIN experiment_submission es ON es.experiment_id = e.experiment_id AND es.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND e.status = 'PUBLISHED' " +
                        "AND (es.id IS NULL OR es.submit_status NOT IN ('SUBMITTED', 'GRADED')) AND e.school_id = ?",
                (rs, rowNum) -> studentTask("实验", rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class), "/student/labs"),
                studentId, schoolId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT h.title, h.due_at AS due_at FROM homework h JOIN class_member cm ON cm.class_id = h.class_id " +
                        "LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND h.status = 'PUBLISHED' " +
                        "AND (hs.id IS NULL OR hs.submit_status NOT IN ('SUBMITTED', 'GRADED')) AND h.school_id = ?",
                (rs, rowNum) -> studentTask("作业", rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class), "/student/homeworks"),
                studentId, schoolId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT ex.title, ex.end_at AS due_at FROM exam ex JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "LEFT JOIN exam_submission exs ON exs.exam_id = ex.id AND exs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND ex.status = 'PUBLISHED' " +
                        "AND (exs.id IS NULL OR exs.status NOT IN ('SUBMITTED', 'AUTO_SUBMITTED', 'GRADED')) AND ex.school_id = ?",
                (rs, rowNum) -> studentTask("考试", rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class), "/student/exams"),
                studentId, schoolId
        ));
        return tasks.stream()
                .sorted(Comparator.comparing(task -> (OffsetDateTime) task.get("dueAt"), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .toList();
    }

    private Map<String, Object> studentTask(String type, String title, OffsetDateTime dueAt, String route) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("type", type);
        task.put("title", title);
        task.put("dueAt", dueAt);
        task.put("route", route);
        return task;
    }

    /**
     * 从可见成绩中构建最近反馈：按 {@code gradedAt DESC, id DESC} 取前 4 条。
     */
    private List<Map<String, Object>> buildRecentFeedback(List<VisibleScore> visibleScores) {
        return visibleScores.stream()
                .sorted(Comparator
                        .comparing(VisibleScore::gradedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(VisibleScore::id, Comparator.reverseOrder()))
                .limit(4)
                .map(record -> {
                    Map<String, Object> feedback = new LinkedHashMap<>();
                    feedback.put("businessType", record.businessType().name());
                    feedback.put("businessName", record.businessName());
                    feedback.put("score", record.score());
                    feedback.put("gradedAt", record.gradedAt());
                    return feedback;
                })
                .toList();
    }
}
