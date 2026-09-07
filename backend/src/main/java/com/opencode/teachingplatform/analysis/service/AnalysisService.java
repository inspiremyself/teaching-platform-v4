package com.opencode.teachingplatform.analysis.service;

import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.BusinessType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class AnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final ScoreRecordRepository scoreRecordRepository;

    public AnalysisService(JdbcTemplate jdbcTemplate, ScoreRecordRepository scoreRecordRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.scoreRecordRepository = scoreRecordRepository;
    }

    public Map<String, Object> getDashboard(CurrentUser teacher) {
        Long teacherId = teacher.id();

        // activityCount: labs + homeworks + exams created by this teacher
        int labCount = count("SELECT COUNT(*) FROM t_experiment WHERE created_by = ?", teacherId);
        int homeworkCount = count("SELECT COUNT(*) FROM homework WHERE created_by = ?", teacherId);
        int examCount = count("SELECT COUNT(*) FROM exam WHERE created_by = ?", teacherId);
        int activityCount = labCount + homeworkCount + examCount;

        // pendingGradeCount: SUBMITTED experiment_submissions + homework_submissions + exam_submissions for teacher's activities
        int pendingLab = count(
                "SELECT COUNT(*) FROM experiment_submission es JOIN t_experiment e ON es.experiment_id = e.experiment_id " +
                        "WHERE e.created_by = ? AND es.submit_status = 'SUBMITTED'",
                teacherId);
        int pendingHomework = count(
                "SELECT COUNT(*) FROM homework_submission hs JOIN homework h ON hs.homework_id = h.id WHERE h.created_by = ? AND hs.submit_status = 'SUBMITTED'",
                teacherId);
        int pendingExam = count(
                "SELECT COUNT(*) FROM exam_submission es JOIN exam e ON es.exam_id = e.id WHERE e.created_by = ? AND (es.status = 'SUBMITTED' OR es.status = 'AUTO_SUBMITTED')",
                teacherId);
        int pendingGradeCount = pendingLab + pendingHomework + pendingExam;

        // averageScore: avg score from score_record for teacher's classes
        Double avgScore = jdbcTemplate.queryForObject(
                "SELECT AVG(sr.score) FROM score_record sr JOIN class_room cr ON sr.class_id = cr.id WHERE cr.teacher_user_id = ?",
                Double.class, teacherId);
        double averageScore = avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0;

        // highRiskPlagiarismCount: similarity_rate > 50
        int highRiskPlagiarismCount = count("SELECT COUNT(*) FROM plagiarism_task WHERE similarity_rate > 50");

        List<Map<String, Object>> summaryCards = List.of(
                summaryCard("activityCount", "教学活动总数", activityCount, "实验、作业与考试总量"),
                summaryCard("pendingGradeCount", "待批改任务", pendingGradeCount, "需要教师处理的提交"),
                summaryCard("averageScore", "平均成绩", averageScore, "当前教师班级平均分"),
                summaryCard("highRiskPlagiarismCount", "高风险查重", highRiskPlagiarismCount, "相似度超过 50% 的记录")
        );

        // trend: last 7 days score_record grouped by date
        List<Map<String, Object>> trend = jdbcTemplate.query(
                "SELECT FORMATDATETIME(graded_at, 'yyyy-MM-dd') AS dt, COUNT(*) AS score_count, AVG(score) AS avg_score " +
                        "FROM score_record WHERE graded_at >= DATEADD('DAY', -7, CURRENT_TIMESTAMP) " +
                        "GROUP BY FORMATDATETIME(graded_at, 'yyyy-MM-dd') ORDER BY dt",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", rs.getString("dt"));
                    row.put("scoreCount", rs.getInt("score_count"));
                    row.put("avgScore", Math.round(rs.getDouble("avg_score") * 10.0) / 10.0);
                    return row;
                });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summaryCards", summaryCards);
        result.put("trend", trend);
        result.put("recentTasks", buildTeacherRecentTasks(teacherId));
        result.put("quickLinks", List.of(
                quickLink("班级管理", "/teacher/classes", activityCount),
                quickLink("实验报告批改", "/teacher/lab-reports", pendingLab),
                quickLink("作业管理", "/teacher/homeworks", pendingHomework),
                quickLink("考试结果", "/teacher/exams", pendingExam)
        ));
        return result;
    }

    public Map<String, Object> getTeacherOverview(CurrentUser teacher) {
        Map<String, Object> dashboard = getDashboard(teacher);

        List<Map<String, Object>> sectionComparison = jdbcTemplate.query(
                "SELECT cr.name AS class_name, ROUND(AVG(sr.score), 1) AS avg_score, COUNT(sr.id) AS graded_count " +
                        "FROM score_record sr JOIN class_room cr ON sr.class_id = cr.id " +
                        "WHERE cr.teacher_user_id = ? GROUP BY cr.id, cr.name ORDER BY avg_score DESC",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("className", rs.getString("class_name"));
                    row.put("avgScore", rs.getDouble("avg_score"));
                    row.put("gradedCount", rs.getInt("graded_count"));
                    return row;
                },
                teacher.id()
        );

        List<Map<String, Object>> businessBreakdown = jdbcTemplate.query(
                "SELECT business_type, ROUND(AVG(score), 1) AS avg_score, COUNT(*) AS score_count " +
                        "FROM score_record sr JOIN class_room cr ON sr.class_id = cr.id " +
                        "WHERE cr.teacher_user_id = ? GROUP BY business_type ORDER BY business_type",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String businessType = rs.getString("business_type");
                    row.put("businessType", businessType);
                    row.put("label", businessTypeLabel(businessType));
                    row.put("avgScore", rs.getDouble("avg_score"));
                    row.put("scoreCount", rs.getInt("score_count"));
                    return row;
                },
                teacher.id()
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

        Map<String, Object> result = new LinkedHashMap<>(dashboard);
        result.put("sectionComparison", sectionComparison);
        result.put("businessBreakdown", businessBreakdown);
        result.put("insights", insights);
        return result;
    }

    public List<Map<String, Object>> getMyScores(CurrentUser student) {
        List<ScoreRecord> records = scoreRecordRepository.findByStudentId(student.id());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScoreRecord r : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("businessType", r.getBusinessType().name());
            item.put("businessName", lookupBusinessName(r.getBusinessType(), r.getBusinessId()));
            item.put("score", r.getScore());
            item.put("gradedAt", r.getGradedAt());
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getMyScoreOverview(CurrentUser student) {
        List<Map<String, Object>> records = getMyScores(student);

        Double avgScore = jdbcTemplate.queryForObject(
                "SELECT AVG(score) FROM score_record WHERE student_id = ?",
                Double.class,
                student.id()
        );
        Double maxScore = jdbcTemplate.queryForObject(
                "SELECT MAX(score) FROM score_record WHERE student_id = ?",
                Double.class,
                student.id()
        );
        Integer completedItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE student_id = ?",
                Integer.class,
                student.id()
        );
        int totalItems = count(
                "SELECT COUNT(*) FROM t_experiment e JOIN class_member cm ON cm.class_id = e.class_id " +
                        "WHERE cm.student_user_id = ? AND e.status = 'PUBLISHED'",
                student.id()
        ) + count(
                "SELECT COUNT(*) FROM homework h JOIN class_member cm ON cm.class_id = h.class_id " +
                        "WHERE cm.student_user_id = ? AND h.status = 'PUBLISHED'",
                student.id()
        ) + count(
                "SELECT COUNT(*) FROM exam ex JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "WHERE cm.student_user_id = ? AND ex.status = 'PUBLISHED'",
                student.id()
        );

        List<Map<String, Object>> groupedScores = jdbcTemplate.query(
                "SELECT business_type, ROUND(AVG(score), 1) AS avg_score, COUNT(*) AS score_count " +
                        "FROM score_record WHERE student_id = ? GROUP BY business_type ORDER BY business_type",
                (rs, rowNum) -> {
                    String businessType = rs.getString("business_type");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("businessType", businessType);
                    row.put("label", businessTypeLabel(businessType));
                    row.put("avgScore", rs.getDouble("avg_score"));
                    row.put("scoreCount", rs.getInt("score_count"));
                    return row;
                },
                student.id()
        );

        List<Map<String, Object>> recentRecords = records.stream().limit(8).toList();

        List<Map<String, Object>> summaryCards = List.of(
                summaryCard("averageScore", "平均成绩", avgScore != null ? roundOne(avgScore) : 0.0, "来自当前学生所有已评分记录"),
                summaryCard("completedItems", "已评分项目", completedItems != null ? completedItems : 0, "已产生分数的实验、作业与考试数量"),
                summaryCard("highestScore", "最高分", maxScore != null ? roundOne(maxScore) : 0.0, "当前最高单项得分"),
                summaryCard("completionRate", "完成进度", totalItems > 0 ? roundOne(((completedItems != null ? completedItems : 0) * 100.0) / totalItems) : 0.0, "按所有关联学习任务估算的完成率")
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
        result.put("completedItems", completedItems != null ? completedItems : 0);
        result.put("totalItems", totalItems);
        return result;
    }

    public Map<String, Object> getStudentDashboard(CurrentUser student) {
        Long studentId = student.id();

        int materialCount = count("SELECT COUNT(*) FROM course_material WHERE visibility = 'ALL'");
        int pendingLabCount = count(
                "SELECT COUNT(*) FROM t_experiment e JOIN class_member cm ON cm.class_id = e.class_id " +
                        "LEFT JOIN experiment_submission es ON es.experiment_id = e.experiment_id AND es.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND e.status = 'PUBLISHED' " +
                        "AND (es.id IS NULL OR es.submit_status NOT IN ('SUBMITTED', 'GRADED'))",
                studentId);
        int pendingHomeworkCount = count(
                "SELECT COUNT(*) FROM homework h JOIN class_member cm ON cm.class_id = h.class_id " +
                        "LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND h.status = 'PUBLISHED' " +
                        "AND (hs.id IS NULL OR hs.submit_status NOT IN ('SUBMITTED', 'GRADED'))",
                studentId);
        int pendingExamCount = count(
                "SELECT COUNT(*) FROM exam ex JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "LEFT JOIN exam_submission exs ON exs.exam_id = ex.id AND exs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND ex.status = 'PUBLISHED' " +
                        "AND (exs.id IS NULL OR exs.status NOT IN ('SUBMITTED', 'AUTO_SUBMITTED', 'GRADED'))",
                studentId);
        int pendingTaskCount = pendingLabCount + pendingHomeworkCount + pendingExamCount;

        Double avgScore = jdbcTemplate.queryForObject(
                "SELECT AVG(score) FROM score_record WHERE student_id = ?",
                Double.class,
                studentId
        );
        double averageScore = avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summaryCards", List.of(
                summaryCard("pendingTasks", "待完成任务", pendingTaskCount, "实验、作业、考试待处理总数"),
                summaryCard("materials", "可访问资料", materialCount, "当前可见教学资料数量"),
                summaryCard("averageScore", "最近平均成绩", averageScore, "来自已评分记录")
        ));
        result.put("upcomingTasks", buildStudentUpcomingTasks(studentId));
        result.put("recentFeedback", buildStudentRecentFeedback(studentId));
        result.put("recentMaterials", jdbcTemplate.query(
                "SELECT id, title, category, file_name FROM course_material WHERE visibility = 'ALL' ORDER BY updated_at DESC LIMIT 4",
                (rs, rowNum) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "title", rs.getString("title"),
                        "category", rs.getString("category"),
                        "fileName", rs.getString("file_name")
                )
        ));
        result.put("quickLinks", List.of(
                quickLink("我的实验", "/student/labs", pendingLabCount),
                quickLink("我的作业", "/student/homeworks", pendingHomeworkCount),
                quickLink("我的考试", "/student/exams", pendingExamCount),
                quickLink("我的成绩", "/student/scores", buildStudentRecentFeedback(studentId).size())
        ));
        return result;
    }

    private String lookupBusinessName(BusinessType type, Long businessId) {
        String sql = switch (type) {
            case LAB -> "SELECT experiment_name FROM t_experiment WHERE experiment_id = ?";
            case HOMEWORK -> "SELECT title FROM homework WHERE id = ?";
            case EXAM -> "SELECT title FROM exam WHERE id = ?";
        };
        try {
            return jdbcTemplate.queryForObject(sql, String.class, businessId);
        } catch (Exception e) {
            return "未知";
        }
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result != null ? result : 0;
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

    private List<Map<String, Object>> buildTeacherRecentTasks(Long teacherId) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.addAll(jdbcTemplate.query(
                "SELECT es.submitted_at AS event_time, e.experiment_name AS title, u.display_name AS actor " +
                        "FROM experiment_submission es JOIN t_experiment e ON es.experiment_id = e.experiment_id " +
                        "JOIN sys_user u ON u.id = es.student_id " +
                        "WHERE e.created_by = ? AND es.submit_status = 'SUBMITTED'",
                (rs, rowNum) -> teacherTask("实验报告待批改", rs.getString("title"), rs.getString("actor"), rs.getObject("event_time", OffsetDateTime.class), "/teacher/lab-reports"),
                teacherId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT hs.updated_at AS event_time, h.title AS title, u.display_name AS actor " +
                        "FROM homework_submission hs JOIN homework h ON hs.homework_id = h.id JOIN sys_user u ON u.id = hs.student_id " +
                        "WHERE h.created_by = ? AND hs.submit_status = 'SUBMITTED'",
                (rs, rowNum) -> teacherTask("作业待批改", rs.getString("title"), rs.getString("actor"), rs.getObject("event_time", OffsetDateTime.class), "/teacher/homeworks"),
                teacherId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT es.submitted_at AS event_time, e.title AS title, u.display_name AS actor " +
                        "FROM exam_submission es JOIN exam e ON es.exam_id = e.id JOIN sys_user u ON u.id = es.student_id " +
                        "WHERE e.created_by = ? AND (es.status = 'SUBMITTED' OR es.status = 'AUTO_SUBMITTED')",
                (rs, rowNum) -> teacherTask("考试结果待处理", rs.getString("title"), rs.getString("actor"), rs.getObject("event_time", OffsetDateTime.class), "/teacher/exams"),
                teacherId
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

    private List<Map<String, Object>> buildStudentUpcomingTasks(Long studentId) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.addAll(jdbcTemplate.query(
                "SELECT e.experiment_name AS title, e.end_at AS due_at FROM t_experiment e JOIN class_member cm ON cm.class_id = e.class_id " +
                        "LEFT JOIN experiment_submission es ON es.experiment_id = e.experiment_id AND es.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND e.status = 'PUBLISHED' " +
                        "AND (es.id IS NULL OR es.submit_status NOT IN ('SUBMITTED', 'GRADED'))",
                (rs, rowNum) -> studentTask("实验", rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class), "/student/labs"),
                studentId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT h.title, h.due_at AS due_at FROM homework h JOIN class_member cm ON cm.class_id = h.class_id " +
                        "LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND h.status = 'PUBLISHED' " +
                        "AND (hs.id IS NULL OR hs.submit_status NOT IN ('SUBMITTED', 'GRADED'))",
                (rs, rowNum) -> studentTask("作业", rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class), "/student/homeworks"),
                studentId
        ));
        tasks.addAll(jdbcTemplate.query(
                "SELECT ex.title, ex.end_at AS due_at FROM exam ex JOIN class_member cm ON cm.class_id = ex.class_id " +
                        "LEFT JOIN exam_submission exs ON exs.exam_id = ex.id AND exs.student_id = cm.student_user_id " +
                        "WHERE cm.student_user_id = ? AND ex.status = 'PUBLISHED' " +
                        "AND (exs.id IS NULL OR exs.status NOT IN ('SUBMITTED', 'AUTO_SUBMITTED', 'GRADED'))",
                (rs, rowNum) -> studentTask("考试", rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class), "/student/exams"),
                studentId
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

    private List<Map<String, Object>> buildStudentRecentFeedback(Long studentId) {
        return jdbcTemplate.query(
                "SELECT business_type, business_id, score, graded_at FROM score_record WHERE student_id = ? ORDER BY graded_at DESC LIMIT 4",
                (rs, rowNum) -> {
                    BusinessType type = BusinessType.valueOf(rs.getString("business_type"));
                    Map<String, Object> feedback = new LinkedHashMap<>();
                    feedback.put("businessType", type.name());
                    feedback.put("businessName", lookupBusinessName(type, rs.getLong("business_id")));
                    feedback.put("score", rs.getDouble("score"));
                    feedback.put("gradedAt", rs.getObject("graded_at", OffsetDateTime.class));
                    return feedback;
                },
                studentId
        );
    }
}
