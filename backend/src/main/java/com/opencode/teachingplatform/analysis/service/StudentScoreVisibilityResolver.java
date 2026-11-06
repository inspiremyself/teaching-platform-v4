package com.opencode.teachingplatform.analysis.service;

import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生成绩可见性解析器。
 *
 * <p>负责把 {@code score_record} 中的原始成绩记录，与考试 / 作业 / 实验三域业务表的
 * 状态、提交状态、成绩可见模式与发布标记做交叉过滤，只返回当前学生“现在真的能看到”的成绩。</p>
 *
 * <p>实现约束：</p>
 * <ul>
 *   <li>只依赖 {@link JdbcTemplate}，不引入 exam / homework / lab 的 Service 或 Repository，避免跨域依赖与 N+1；</li>
 *   <li>每次解析最多 4 条 SQL：1 条成绩载体 + 最多 3 条按业务类型批量查询（类型集合为空时跳过）；</li>
 *   <li>可见性矩阵与三域详情页的可见性判定保持一致，业务缺失、草稿、无提交、成绩为空一律剔除。</li>
 * </ul>
 */
@Service
public class StudentScoreVisibilityResolver {

    /**
     * 解析后的可见成绩。
     *
     * @param id           成绩记录 id
     * @param businessType 业务类型
     * @param businessId   业务 id
     * @param businessName 业务标题（取自业务表）
     * @param score        成绩
     * @param gradedAt     评分时间
     */
    public record VisibleScore(Long id, BusinessType businessType, Long businessId,
                               String businessName, Double score, OffsetDateTime gradedAt) {
    }

    /** 成绩载体行。 */
    private record ScoreRow(Long id, BusinessType businessType, Long businessId,
                            Double score, OffsetDateTime gradedAt) {
    }

    /** 业务 + 当前学生提交的合并行。 */
    private record BusinessRow(String name, String status, String visibilityMode,
                               boolean scoreReleased, OffsetDateTime deadline,
                               String submissionStatus, Double labTotalScore) {
    }

    private final JdbcTemplate jdbcTemplate;

    public StudentScoreVisibilityResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 解析指定学生的可见成绩列表。
     *
     * @param studentId 学生用户 id
     * @return 按 {@code score_record.id ASC} 排序的可见成绩；无可见记录时返回空列表
     */
    public List<VisibleScore> resolve(Long studentId) {
        List<ScoreRow> scoreRows = jdbcTemplate.query(
                "SELECT id, business_type, business_id, score, graded_at " +
                        "FROM score_record WHERE student_id = ? ORDER BY id ASC",
                (rs, rowNum) -> new ScoreRow(
                        rs.getLong("id"),
                        BusinessType.valueOf(rs.getString("business_type")),
                        rs.getLong("business_id"),
                        toDouble(rs.getObject("score")),
                        rs.getObject("graded_at", OffsetDateTime.class)
                ),
                studentId
        );
        if (scoreRows.isEmpty()) {
            return List.of();
        }

        Map<BusinessType, List<Long>> idsByType = collectBusinessIds(scoreRows);
        Map<BusinessType, Map<Long, BusinessRow>> rowsByType = new EnumMap<>(BusinessType.class);
        for (Map.Entry<BusinessType, List<Long>> entry : idsByType.entrySet()) {
            rowsByType.put(entry.getKey(), loadBusinessRows(entry.getKey(), entry.getValue(), studentId));
        }

        // 单次解析只取一次 now，保证同一批记录的时间判断口径一致
        OffsetDateTime now = OffsetDateTime.now();
        List<VisibleScore> visibleScores = new ArrayList<>();
        for (ScoreRow scoreRow : scoreRows) {
            if (scoreRow.score() == null) {
                continue;
            }
            Map<Long, BusinessRow> businessRows = rowsByType.getOrDefault(scoreRow.businessType(), Collections.emptyMap());
            BusinessRow businessRow = businessRows.get(scoreRow.businessId());
            if (!isVisible(businessRow, scoreRow.businessType(), now)) {
                continue;
            }
            visibleScores.add(new VisibleScore(
                    scoreRow.id(),
                    scoreRow.businessType(),
                    scoreRow.businessId(),
                    businessRow.name(),
                    scoreRow.score(),
                    scoreRow.gradedAt()
            ));
        }
        return visibleScores;
    }

    /**
     * 按业务类型收集去重后的业务 id（成绩为空的记录不参与业务查询）。
     */
    private Map<BusinessType, List<Long>> collectBusinessIds(List<ScoreRow> scoreRows) {
        Map<BusinessType, List<Long>> idsByType = new EnumMap<>(BusinessType.class);
        for (ScoreRow row : scoreRows) {
            if (row.score() == null) {
                continue;
            }
            idsByType.computeIfAbsent(row.businessType(), ignored -> new ArrayList<>()).add(row.businessId());
        }
        Map<BusinessType, List<Long>> distinctIdsByType = new EnumMap<>(BusinessType.class);
        idsByType.forEach((type, ids) -> distinctIdsByType.put(type, ids.stream().distinct().toList()));
        return distinctIdsByType;
    }

    /**
     * 按业务类型批量加载业务行与当前学生提交行（一条 LEFT JOIN SQL）。
     */
    private Map<Long, BusinessRow> loadBusinessRows(BusinessType type, List<Long> businessIds, Long studentId) {
        if (businessIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(", ", Collections.nCopies(businessIds.size(), "?"));
        Object[] args = new Object[businessIds.size() + 1];
        args[0] = studentId;
        for (int i = 0; i < businessIds.size(); i++) {
            args[i + 1] = businessIds.get(i);
        }
        String sql = switch (type) {
            case EXAM -> "SELECT ex.id AS business_id, ex.title AS name, ex.status, ex.score_visibility_mode AS visibility_mode, " +
                    "ex.score_released, ex.end_at AS deadline, es.status AS submission_status, NULL AS lab_total_score " +
                    "FROM exam ex LEFT JOIN exam_submission es ON es.exam_id = ex.id AND es.student_id = ? " +
                    "WHERE ex.id IN (" + placeholders + ")";
            case HOMEWORK -> "SELECT h.id AS business_id, h.title AS name, h.status, h.score_visibility_mode AS visibility_mode, " +
                    "h.score_released, h.due_at AS deadline, hs.submit_status AS submission_status, NULL AS lab_total_score " +
                    "FROM homework h LEFT JOIN homework_submission hs ON hs.homework_id = h.id AND hs.student_id = ? " +
                    "WHERE h.id IN (" + placeholders + ")";
            case LAB -> "SELECT e.experiment_id AS business_id, e.experiment_name AS name, e.status, " +
                    "e.score_visibility_mode AS visibility_mode, e.score_released, e.end_at AS deadline, " +
                    "es.submit_status AS submission_status, es.total_score AS lab_total_score " +
                    "FROM t_experiment e LEFT JOIN experiment_submission es " +
                    "ON es.experiment_id = e.experiment_id AND es.student_id = ? " +
                    "WHERE e.experiment_id IN (" + placeholders + ")";
        };
        Map<Long, BusinessRow> rows = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            rows.put(rs.getLong("business_id"), new BusinessRow(
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getString("visibility_mode"),
                    rs.getBoolean("score_released"),
                    rs.getObject("deadline", OffsetDateTime.class),
                    rs.getString("submission_status"),
                    toDouble(rs.getObject("lab_total_score"))
            ));
        }, args);
        return rows;
    }

    /**
     * 可见性矩阵：业务缺失 / 草稿 / 无提交一律不可见，其余按成绩可见模式判定。
     */
    private boolean isVisible(BusinessRow row, BusinessType type, OffsetDateTime now) {
        if (row == null) {
            return false;
        }
        if ("DRAFT".equals(row.status())) {
            return false;
        }
        if (row.submissionStatus() == null) {
            return false;
        }
        ScoreVisibilityMode mode = (row.visibilityMode() == null || row.visibilityMode().isBlank())
                ? ScoreVisibilityMode.AFTER_TEACHER_CONFIRM
                : ScoreVisibilityMode.valueOf(row.visibilityMode());
        return switch (mode) {
            // 实验的“立即可见”以教师已算出总分为前提，避免空分值提前泄露
            case IMMEDIATE -> type != BusinessType.LAB || row.labTotalScore() != null;
            case AFTER_TEACHER_CONFIRM -> "GRADED".equals(row.submissionStatus());
            case AFTER_DEADLINE -> row.deadline() != null && !now.isBefore(row.deadline());
            case MANUAL_RELEASE -> row.scoreReleased();
        };
    }

    private Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
