package com.opencode.teachingplatform.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.exam.dto.ExamRequests;
import com.opencode.teachingplatform.grading.service.ScoringEngine;
import com.opencode.teachingplatform.grading.strategy.MultipleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SingleChoiceScoringStrategy;
import com.opencode.teachingplatform.grading.strategy.SubjectiveRecommendationStrategy;
import com.opencode.teachingplatform.grading.strategy.TrueFalseScoringStrategy;
import com.opencode.teachingplatform.exam.service.ExamScoringService;
import com.opencode.teachingplatform.exam.service.ExamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        ExamService.class,
        com.opencode.teachingplatform.exam.service.ExamConfigService.class,
        com.opencode.teachingplatform.exam.service.ExamAnswerService.class,
        com.opencode.teachingplatform.exam.service.ExamReportService.class,
        com.opencode.teachingplatform.exam.service.ExamServiceSupport.class,
        ExamScoringService.class,
        com.opencode.teachingplatform.clazz.service.ClassAccessService.class,
        com.opencode.teachingplatform.grading.service.ScoreRecordWriter.class,
        ScoringEngine.class,
        SingleChoiceScoringStrategy.class,
        MultipleChoiceScoringStrategy.class,
        TrueFalseScoringStrategy.class,
        SubjectiveRecommendationStrategy.class
})
class ExamServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExamService examService;

    @Autowired
    private EntityManager entityManager;

    private final CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
    private final CurrentUser student = new CurrentUser(2L, "20260001", "演示学生", UserRole.STUDENT);
    private final CurrentUser anotherTeacher = new CurrentUser(3L, "t9003", "其他教师", UserRole.TEACHER);

    @SuppressWarnings("unchecked")
    private Long createPublishedExamForStudent() {
        return createPublishedExamForStudent(ScoreVisibilityMode.IMMEDIATE);
    }

    @SuppressWarnings("unchecked")
    private Long createPublishedExamForStudent(ScoreVisibilityMode scoreVisibilityMode) {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "测试考试-" + System.nanoTime(),
                        "用于 service 测试的独立考试",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "PUBLISHED",
                        scoreVisibilityMode,
                        List.of(
                                new ExamRequests.ExamQuestionItem(1L, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(2L, 2, 3.0),
                                new ExamRequests.ExamQuestionItem(3L, 3, 10.0)
                        ),
                        null
                )
        );
        return ((Number) created.get("id")).longValue();
    }

    @Test
    void examSchemaSupportsSingleAttemptWorkflow() throws Exception {
        // Verify EXAM table has CLASS_ID column
        Set<String> examColumns = getColumnNames("EXAM");
        assertThat(examColumns).contains("CLASS_ID", "SHUFFLE_QUESTIONS");

        // Verify EXAM_SUBMISSION table has required columns
        Set<String> submissionColumns = getColumnNames("EXAM_SUBMISSION");
        assertThat(submissionColumns)
                .contains("DEADLINE_AT", "STATUS", "AUTO_SCORE", "MANUAL_SCORE", "TOTAL_SCORE");

        // Verify seed exam data exists
        Integer examCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE id = -1", Integer.class);
        assertThat(examCount).isEqualTo(1);

        // Verify seed exam_question data exists
        Integer questionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_question WHERE exam_id = -1", Integer.class);
        assertThat(questionCount).isEqualTo(3);

        Integer seededSubmissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_submission WHERE exam_id = -1 AND student_id = 2", Integer.class);
        assertThat(seededSubmissionCount).isEqualTo(1);

        // Verify unique constraint on exam_submission (exam_id, student_id)
        // Insert one row for a NEW exam - should succeed
        Long freshExamId = createPublishedExamForStudent();
        jdbcTemplate.update(
                """
                INSERT INTO exam_submission (id, exam_id, student_id, status, created_at, updated_at)
                VALUES (999, ?, 2, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, freshExamId);

        // Insert duplicate (exam_id, student_id) - should fail
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO exam_submission (id, exam_id, student_id, status, created_at, updated_at)
                        VALUES (1000, ?, 2, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, freshExamId)
        );
    }

    @Test
    void submitExamAutoGradesSingleMultipleAndTrueFalseQuestions() {
        ExamScoringService scoringService = new ExamScoringService();

        // SINGLE: correct answer "A" → full score 5
        double singleScore = scoringService.scoreObjectiveQuestion("SINGLE", "[\"A\"]", "[\"A\"]", 5.0);
        assertThat(singleScore).isEqualTo(5.0);

        // SINGLE: wrong answer "B" → 0
        double singleWrong = scoringService.scoreObjectiveQuestion("SINGLE", "[\"A\"]", "[\"B\"]", 5.0);
        assertThat(singleWrong).isEqualTo(0.0);

        // JUDGE: correct answer "true" → full score 3
        double judgeScore = scoringService.scoreObjectiveQuestion("JUDGE", "[\"true\"]", "[\"true\"]", 3.0);
        assertThat(judgeScore).isEqualTo(3.0);

        // JUDGE: wrong answer → 0
        double judgeWrong = scoringService.scoreObjectiveQuestion("JUDGE", "[\"true\"]", "[\"false\"]", 3.0);
        assertThat(judgeWrong).isEqualTo(0.0);

        // SHORT: always returns 0 (manual grading)
        double shortScore = scoringService.scoreObjectiveQuestion("SHORT", "[\"统一错误处理与前端解析\"]", "[\"统一错误处理与前端解析\"]", 10.0);
        assertThat(shortScore).isEqualTo(0.0);

        // Total autoScore = 5 + 3 + 0 = 8
        double autoScore = singleScore + judgeScore + shortScore;
        assertThat(autoScore).isEqualTo(8.0);
    }

    @Test
    void submitExamStoresShortAnswerForTeacherGrading() {
        ExamScoringService scoringService = new ExamScoringService();

        // SHORT type → score=0, isObjective=false (means isCorrect should be null)
        assertThat(scoringService.isObjective("SHORT")).isFalse();
        assertThat(scoringService.scoreObjectiveQuestion("SHORT", "[\"answer\"]", "[\"student answer\"]", 10.0)).isEqualTo(0.0);

        // Objective types are correctly identified
        assertThat(scoringService.isObjective("SINGLE")).isTrue();
        assertThat(scoringService.isObjective("SINGLE_CHOICE")).isTrue();
        assertThat(scoringService.isObjective("MULTIPLE")).isTrue();
        assertThat(scoringService.isObjective("MULTIPLE_CHOICE")).isTrue();
        assertThat(scoringService.isObjective("JUDGE")).isTrue();
        assertThat(scoringService.isObjective("TRUE_FALSE")).isTrue();
        assertThat(scoringService.isObjective("TEXT")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitExamWritesSharedScoringMetadataAndKeepsSubjectiveScorePending() {
        Long examId = createPublishedExamForStudent();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();

        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));

        assertThat(submitResult.get("status")).isEqualTo("SUBMITTED");
        // 含主观题时提交仅到 SUBMITTED，未进入最终态，分数一律不下发
        assertThat(submitResult.get("autoScore")).isNull();
        assertThat(submitResult.get("totalScore")).isNull();
        assertThat(submitResult.get("hasManualGrading")).isEqualTo(true);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT question_id, score, auto_score, suggested_score, score_source, judge_detail, accepted_auto_score
                FROM exam_answer
                WHERE exam_submission_id = ?
                ORDER BY question_id
                """,
                submissionId
        );

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
                .containsEntry("QUESTION_ID", 1L)
                .containsEntry("SCORE", 5.0)
                .containsEntry("AUTO_SCORE", 5.0)
                .containsEntry("SUGGESTED_SCORE", null)
                .containsEntry("SCORE_SOURCE", "AUTO")
                .containsEntry("ACCEPTED_AUTO_SCORE", true);
        assertThat(rows.get(0).get("JUDGE_DETAIL")).asString().contains("matched");

        assertThat(rows.get(1))
                .containsEntry("QUESTION_ID", 2L)
                .containsEntry("SCORE", 3.0)
                .containsEntry("AUTO_SCORE", 3.0)
                .containsEntry("SUGGESTED_SCORE", null)
                .containsEntry("SCORE_SOURCE", "AUTO")
                .containsEntry("ACCEPTED_AUTO_SCORE", true);
        assertThat(rows.get(1).get("JUDGE_DETAIL")).asString().contains("matched");

        assertThat(rows.get(2))
                .containsEntry("QUESTION_ID", 3L)
                .containsEntry("SCORE", null)
                .containsEntry("AUTO_SCORE", null)
                .containsEntry("SCORE_SOURCE", "RECOMMENDED")
                .containsEntry("ACCEPTED_AUTO_SCORE", false);
        assertThat(((Number) rows.get(2).get("SUGGESTED_SCORE")).doubleValue()).isGreaterThanOrEqualTo(0.0);
        assertThat(rows.get(2).get("JUDGE_DETAIL")).asString().contains("matched");

        // 含主观题的答卷不自动结案，也不写入共享成绩记录
        entityManager.flush();
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class,
                examId,
                student.id()
        );
        assertThat(scoreRecordCount).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submittedExamShouldHideCurrentScoreFromStudentUntilTeacherConfirmation() {
        Long examId = createPublishedExamForStudent(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        examService.startExam(student, examId);

        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        List<Map<String, Object>> studentList = (List<Map<String, Object>>) examService.listExams(student);
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class,
                examId,
                student.id()
        );

        assertThat(detail.get("submissionStatus")).isEqualTo("SUBMITTED");
        assertThat(detail.get("resultAvailable")).isEqualTo(false);
        assertThat(detail.get("totalScore")).isNull();
        assertThat(studentList).anySatisfy(item -> {
            if (examId.equals(item.get("id"))) {
                assertThat(item.get("submissionStatus")).isEqualTo("SUBMITTED");
                assertThat(item.get("totalScore")).isNull();
                assertThat(item.get("canViewResult")).isEqualTo(false);
            }
        });
        assertThat(scoreRecordCount).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherCanAcceptSuggestedExamScoreAndThenStudentsCanSeeFinalResult() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = ((Number) ((Map<String, Object>) examService.startExam(student, examId)).get("submissionId")).longValue();

        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));

        Map<String, Object> confirmResult = (Map<String, Object>) examService.confirmAnswer(
                teacher,
                submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "接受推荐分", null, true)
        );
        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        Double scoreRecord = jdbcTemplate.queryForObject(
                "SELECT score FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Double.class,
                examId,
                student.id()
        );

        assertThat(confirmResult.get("status")).isEqualTo("GRADED");
        assertThat(((Number) confirmResult.get("totalScore")).doubleValue()).isEqualTo(scoreRecord);
        assertThat(detail.get("resultAvailable")).isEqualTo(true);
        assertThat(detail.get("totalScore")).isEqualTo(scoreRecord);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmAnswerRejectsObjectiveQuestionManualScoring() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = ((Number) ((Map<String, Object>) examService.startExam(student, examId))
                .get("submissionId")).longValue();
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));

        // 客观题：手工给分与采纳推荐分都必须被拒绝
        assertThatThrownBy(() -> examService.confirmAnswer(
                teacher,
                submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(1L, "尝试手工改分", 0D, false)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客观题由系统自动评分，不支持手工改分");
        assertThatThrownBy(() -> examService.confirmAnswer(
                teacher,
                submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(1L, "尝试采纳推荐分", null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客观题不接受推荐分");

        // 拒绝后客观题分数仍是系统自动评分结果，未被教师请求影响
        Double objectiveScore = jdbcTemplate.queryForObject(
                "SELECT score FROM exam_answer WHERE exam_submission_id = ? AND question_id = ?",
                Double.class,
                submissionId,
                1L
        );
        assertThat(objectiveScore).isEqualTo(5.0D);
    }

    @Test
    void multipleChoiceScoringRequiresExactMatch() {
        ExamScoringService scoringService = new ExamScoringService();

        // Exact match (order doesn't matter) → full score
        double correct = scoringService.scoreObjectiveQuestion("MULTIPLE", "[\"A\",\"C\"]", "[\"C\",\"A\"]", 6.0);
        assertThat(correct).isEqualTo(6.0);

        // Partial match → 0
        double partial = scoringService.scoreObjectiveQuestion("MULTIPLE", "[\"A\",\"C\"]", "[\"A\"]", 6.0);
        assertThat(partial).isEqualTo(0.0);

        // Extra selection → 0
        double extra = scoringService.scoreObjectiveQuestion("MULTIPLE", "[\"A\",\"C\"]", "[\"A\",\"B\",\"C\"]", 6.0);
        assertThat(extra).isEqualTo(0.0);
    }

    @Test
    void scoringHandlesNullAndEmptyAnswers() {
        ExamScoringService scoringService = new ExamScoringService();

        // Null answer → 0
        double nullAnswer = scoringService.scoreObjectiveQuestion("SINGLE", "[\"A\"]", null, 5.0);
        assertThat(nullAnswer).isEqualTo(0.0);

        // Empty string answer → 0
        double emptyAnswer = scoringService.scoreObjectiveQuestion("SINGLE", "[\"A\"]", "", 5.0);
        assertThat(emptyAnswer).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradingSubjectiveQuestionUpdatesScoresAndCreatesScoreRecord() {
        Long examId = createPublishedExamForStudent();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();

        // 2. Student submits answers: Q1=A(correct), Q2=true(correct), Q3=short answer
        ExamRequests.SubmitExamRequest submitReq = new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        ));
        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId, submitReq);
        assertThat(submitResult.get("status")).isEqualTo("SUBMITTED");
        // 含主观题时提交仅到 SUBMITTED，未进入最终态，响应不下发分数
        assertThat(submitResult.get("autoScore")).isNull();
        assertThat(submitResult.get("totalScore")).isNull();

        // 3. Teacher grades: give SHORT question (id=3) a score of 7.0
        ExamRequests.GradeExamSubmissionRequest gradeReq = new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(3L, 7.0, "回答正确")
        ));
        Map<String, Object> gradeResult = (Map<String, Object>) examService.gradeSubmission(teacher, submissionId, gradeReq);

        // 4. Assert grading result
        assertThat(gradeResult.get("status")).isEqualTo("GRADED");
        assertThat(((Number) gradeResult.get("autoScore")).doubleValue()).isEqualTo(8.0);
        assertThat(((Number) gradeResult.get("manualScore")).doubleValue()).isEqualTo(7.0);
        assertThat(((Number) gradeResult.get("totalScore")).doubleValue()).isEqualTo(15.0);

        // 5. Verify score_record was created
        Integer scoreRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = -1 AND student_id = 2",
                Integer.class);
        Integer scoreRecordCountByExam = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = 2",
                Integer.class, examId);
        assertThat(scoreRecordCountByExam).isEqualTo(1);

        Double recordScore = jdbcTemplate.queryForObject(
                "SELECT score FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = 2",
                Double.class, examId);
        assertThat(recordScore).isEqualTo(15.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradingObjectiveQuestionScoreIsNotOverridden() {
        Long examId = createPublishedExamForStudent();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();

        ExamRequests.SubmitExamRequest submitReq = new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),       // SINGLE correct → 5.0
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),    // JUDGE correct → 3.0
                new ExamRequests.SubmitAnswerItem(3L, null, "我的回答", null, null) // SHORT → 0.0
        ));
        examService.submitExam(student, examId, submitReq);

        // 2. Teacher attempts to override objective Q1 score to 0 and grade Q3
        ExamRequests.GradeExamSubmissionRequest gradeReq = new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(1L, 0.0, "想改成0"),  // objective - should be skipped
                new ExamRequests.GradeExamAnswerItem(3L, 8.0, "不错")     // subjective - should be applied
        ));
        Map<String, Object> gradeResult = (Map<String, Object>) examService.gradeSubmission(teacher, submissionId, gradeReq);

        // 3. Objective Q1 score remains 5.0 (not overridden to 0), autoScore still 8.0
        assertThat(((Number) gradeResult.get("autoScore")).doubleValue()).isEqualTo(8.0);
        assertThat(((Number) gradeResult.get("manualScore")).doubleValue()).isEqualTo(8.0);
        // totalScore = autoScore(8.0) + manualScore(8.0) = 16.0
        assertThat(((Number) gradeResult.get("totalScore")).doubleValue()).isEqualTo(16.0);

        // 4. Verify Q1 answer score is still 5.0 in DB
        Double q1Score = jdbcTemplate.queryForObject(
                "SELECT score FROM exam_answer WHERE exam_submission_id = ? AND question_id = 1",
                Double.class, submissionId);
        assertThat(q1Score).isEqualTo(5.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradingSubjectiveScoreOutsideSnapshotRangeIsRejected() {
        Long examId = createPublishedExamForStudent();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "回答", null, null)
        )));

        assertThatThrownBy(() -> examService.gradeSubmission(teacher, submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, 11.0, "超出上限")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数必须在 0 到该题分值之间");
        assertThatThrownBy(() -> examService.gradeSubmission(teacher, submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, -1.0, "负数")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数必须在 0 到该题分值之间");
        assertThatThrownBy(() -> examService.gradeSubmission(teacher, submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, 0.5, "小数")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数必须为整数");

        Map<String, Object> gradeResult = (Map<String, Object>) examService.gradeSubmission(
                teacher, submissionId, new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, 10.0, "满分"))));
        assertThat(((Number) gradeResult.get("manualScore")).doubleValue()).isEqualTo(10.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmAnswerWithOutOfRangeExplicitScoreIsRejected() {
        Long examId = createPublishedExamForStudent();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "回答", null, null)
        )));

        assertThatThrownBy(() -> examService.confirmAnswer(teacher, submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "越界", 11.0, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数必须在 0 到该题分值之间");

        // 采纳推荐分时忽略请求中的自填分数，不做越界校验
        Map<String, Object> result = (Map<String, Object>) examService.confirmAnswer(teacher, submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "采纳推荐", 999.0, true));
        assertThat(((Number) result.get("manualScore")).doubleValue()).isBetween(0.0, 10.0);
    }

    @Test
    void gradingNonSubmittedSubmissionIsRejected() {
        Long examId = createPublishedExamForStudent();
        @SuppressWarnings("unchecked")
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();

        // 2. Teacher tries to grade an IN_PROGRESS submission → expect rejection
        ExamRequests.GradeExamSubmissionRequest gradeReq = new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(3L, 7.0, "Good")
        ));
        assertThatThrownBy(() -> examService.gradeSubmission(teacher, submissionId, gradeReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许批改");
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradingAlreadyGradedSubmissionIsRejected() {
        Long examId = createPublishedExamForStudent();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();

        ExamRequests.SubmitExamRequest submitReq = new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "回答", null, null)
        ));
        examService.submitExam(student, examId, submitReq);

        ExamRequests.GradeExamSubmissionRequest gradeReq = new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(3L, 5.0, "OK")
        ));
        examService.gradeSubmission(teacher, submissionId, gradeReq);

        // 2. Try to grade again → expect rejection (status is now GRADED)
        ExamRequests.GradeExamSubmissionRequest secondGradeReq = new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(3L, 9.0, "改主意了")
        ));
        assertThatThrownBy(() -> examService.gradeSubmission(teacher, submissionId, secondGradeReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许批改");
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherListAndResultsExposeExamContextFields() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "期末考试",
                        "覆盖第 1-6 章",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(2),
                        90,
                        "PUBLISHED",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        );

        Long examId = ((Number) created.get("id")).longValue();
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        Long submissionId = ((Number) startResult.get("submissionId")).longValue();
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        )));

        List<Map<String, Object>> teacherList = (List<Map<String, Object>>) examService.listExams(teacher);
        List<Map<String, Object>> teacherResults = (List<Map<String, Object>>) examService.getExamResults(teacher, examId);
        Map<String, Object> submissionDetail = (Map<String, Object>) examService.getSubmissionDetail(teacher, submissionId);

        assertThat(teacherList)
                .anySatisfy(item -> {
                    assertThat(item.get("title")).isEqualTo("期末考试");
                    assertThat(item.get("id")).isEqualTo(examId);
                    assertThat(item.get("className")).isNotNull().asString().isNotBlank();
                    assertThat(item.get("startAt")).isNotNull();
                    assertThat(item.get("endAt")).isNotNull();
                    assertThat(((Number) item.get("questionCount")).longValue()).isEqualTo(1L);
                });

        assertThat(teacherResults).singleElement().satisfies(item -> {
            assertThat(item.get("examTitle")).isEqualTo("期末考试");
            assertThat(item.get("className")).isNotNull().asString().isNotBlank();
            assertThat(item.get("studentNo")).isEqualTo("20260001");
        });

        assertThat(submissionDetail.get("examTitle")).isEqualTo("期末考试");
        assertThat(submissionDetail.get("className")).isNotNull().asString().isNotBlank();
        assertThat(submissionDetail.get("studentName")).isEqualTo("演示学生");
        assertThat(submissionDetail.get("studentNo")).isEqualTo("20260001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentListAndDetailExposeScheduleAndResultFields() {
        List<Map<String, Object>> studentList = (List<Map<String, Object>>) examService.listExams(student);
        Map<String, Object> initialDetail = (Map<String, Object>) examService.getExamDetail(student, -3L);

        assertThat(studentList).anySatisfy(item -> {
            assertThat(item.get("id")).isEqualTo(-3L);
            assertThat(item.get("className")).isNotNull().asString().isNotBlank();
            assertThat(item.get("startAt")).isNotNull();
            assertThat(item.get("endAt")).isNotNull();
        });
        assertThat(initialDetail.get("className")).isNotNull().asString().isNotBlank();

        assertThat(initialDetail.get("submissionStatus")).isEqualTo("SUBMITTED");
        assertThat(initialDetail.get("resultAvailable")).isEqualTo(false);

        Long seededSubmissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM exam_submission WHERE exam_id = -3 AND student_id = 2",
                Long.class
        );
        examService.gradeSubmission(teacher, seededSubmissionId, new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(3L, 7.0, "回答正确")
        )));
        Map<String, Object> gradedDetail = (Map<String, Object>) examService.getExamDetail(student, -3L);

        assertThat(gradedDetail.get("submissionStatus")).isEqualTo("GRADED");
        assertThat(gradedDetail.get("resultAvailable")).isEqualTo(true);
        assertThat(gradedDetail.get("className")).isNotNull().asString().isNotBlank();

        List<Map<String, Object>> gradedStudentList = (List<Map<String, Object>>) examService.listExams(student);
        assertThat(gradedStudentList).anySatisfy(item -> {
            assertThat(item.get("id")).isEqualTo(-3L);
            assertThat(item.get("submissionStatus")).isEqualTo("GRADED");
            assertThat(item.get("canViewResult")).isEqualTo(true);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void manualReleaseModeShowsExamResultAfterTeacherRelease() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "手动发布考试",
                        "发布后才可见",
                        1L,
                        OffsetDateTime.now().minusHours(2),
                        OffsetDateTime.now().plusHours(1),
                        60,
                        "PUBLISHED",
                        com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.MANUAL_RELEASE,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        );
        Long examId = ((Number) created.get("id")).longValue();

        examService.startExam(student, examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        )));

        Map<String, Object> hiddenDetail = (Map<String, Object>) examService.getExamDetail(student, examId);
        assertThat(hiddenDetail.get("resultAvailable")).isEqualTo(false);

        examService.releaseScores(teacher, examId);
        Map<String, Object> visibleDetail = (Map<String, Object>) examService.getExamDetail(student, examId);
        assertThat(visibleDetail.get("resultAvailable")).isEqualTo(true);
        assertThat(visibleDetail.get("totalScore")).isEqualTo(5.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void examDetailShouldPreferStoredQuestionSnapshotOverMutatedQuestionBankData() {
        Long examId = createPublishedExamForStudent();
        overwriteExamQuestionSnapshot(examId, 1L,
                buildExamSnapshotJson("Original snapshot stem", 1, 5.0));
        jdbcTemplate.update("UPDATE question_bank SET stem = ? WHERE id = ?", "Mutated bank stem", 1L);
        entityManager.clear();

        String storedSnapshot = jdbcTemplate.queryForObject(
                "SELECT question_snapshot_json FROM exam_question WHERE exam_id = ? AND question_id = ?",
                String.class,
                examId,
                1L
        );
        assertThat(storedSnapshot).contains("Original snapshot stem");

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");

        assertThat(questions).isNotEmpty();
        assertThat(findQuestion(questions, 1L).get("stem")).isEqualTo("Original snapshot stem");
    }

    @Test
    void createExamRejectsInlineQuestion() {
        assertThatThrownBy(() -> examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "内联题考试",
                        "内联题已下线",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        45,
                        "DRAFT",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(null, "INLINE", 1, 12.0)),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试题目必须来自题库")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createExamDefaultsShuffleQuestionsToFalse() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "默认顺序考试",
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "DRAFT",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        );
        Long examId = ((Number) created.get("id")).longValue();

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        assertThat(detail.get("shuffleQuestions")).isEqualTo(false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT shuffle_questions FROM exam WHERE id = ?", Boolean.class, examId))
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createExamPersistsShuffleQuestionsWhenTrue() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "乱序考试",
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "DRAFT",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        true
                )
        );
        Long examId = ((Number) created.get("id")).longValue();

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        assertThat(detail.get("shuffleQuestions")).isEqualTo(true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT shuffle_questions FROM exam WHERE id = ?", Boolean.class, examId))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateExamKeepsShuffleQuestionsWhenOmitted() {
        Long examId = createDraftExam(true);

        examService.updateExam(teacher, examId, buildUpdateRequest("保留乱序", null, List.of(1L)));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        assertThat(detail.get("shuffleQuestions")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateExamUpdatesShuffleQuestionsWhenProvided() {
        Long examId = createDraftExam(false);

        examService.updateExam(teacher, examId, buildUpdateRequest("改为乱序", true, List.of(1L)));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        assertThat(detail.get("shuffleQuestions")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateExamClearsTimeWindowWhenBothBoundsNull() {
        Long examId = createDraftExam(false);

        examService.updateExam(teacher, examId, new ExamRequests.UpdateExamRequest(
                "清空时间窗",
                "",
                1L,
                null,
                null,
                30,
                null,
                List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                null
        ));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        assertThat(detail.get("startAt")).isNull();
        assertThat(detail.get("endAt")).isNull();
    }

    @Test
    void updateExamRejectsInlineQuestion() {
        Long examId = createDraftExam(false);

        assertThatThrownBy(() -> examService.updateExam(
                teacher,
                examId,
                new ExamRequests.UpdateExamRequest(
                        "更新为内联题",
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(null, "INLINE", 1, 5.0)),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试题目必须来自题库")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40000);
    }

    @Test
    void publishExamRejectsLegacyInlineQuestion() {
        insertLegacyInlineExam(901L, "DRAFT");

        assertThatThrownBy(() -> examService.changeStatus(
                teacher, 901L, new ExamRequests.ChangeExamStatusRequest("PUBLISHED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先将题目保存到题库后再组卷")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40000);
    }

    @Test
    void startExamRejectsLegacyInlineQuestion() {
        insertLegacyInlineExam(902L, "PUBLISHED");

        assertThatThrownBy(() -> examService.startExam(student, 902L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试卷包含不支持的题目，请联系教师")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40000);
    }

    @Test
    void startExamRejectsStudentOutsideClass() {
        insertLegacyInlineExam(903L, "PUBLISHED");
        CurrentUser outsider = new CurrentUser(999L, "20269999", "非本班学生", UserRole.STUDENT);

        assertThatThrownBy(() -> examService.startExam(outsider, 903L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限参加该考试")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40300);
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentDetailIncludesAnalysisTextWhenScoreVisible() {
        Long examId = createExamWithVisibility(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.IMMEDIATE);
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitFullAnswers());
        // 含主观题的考试必须先批改进入 GRADED 最终态，解析与结果才对可见
        examService.gradeSubmission(teacher, submissionId, new ExamRequests.GradeExamSubmissionRequest(List.of(
                new ExamRequests.GradeExamAnswerItem(3L, 7.0, "回答正确")
        )));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        assertThat(detail.get("resultAvailable")).isEqualTo(true);
        assertThat(findQuestion(questions, 1L).get("analysisText")).isEqualTo("强调控制复杂度");
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentDetailOmitsAnalysisTextWhenScoreHidden() {
        Long examId = createExamWithVisibility(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        examService.startExam(student, examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        )));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        assertThat(detail.get("resultAvailable")).isEqualTo(false);
        assertThat(findQuestion(questions, 1L)).doesNotContainKey("analysisText");
    }

    @Test
    void shuffleFlagDoesNotAffectScoring() {
        Long examId = createExamWithVisibility(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.AFTER_TEACHER_CONFIRM, true);
        examService.startExam(student, examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));
        // 提交后分数仅写入持久化上下文，JdbcTemplate 直连查询前需显式刷库
        entityManager.flush();

        Double autoScore = jdbcTemplate.queryForObject(
                "SELECT auto_score FROM exam_submission WHERE exam_id = ? AND student_id = 2",
                Double.class,
                examId
        );
        assertThat(autoScore).isEqualTo(8.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitObjectiveOnlyExamMarksGradedAndWritesScoreRecord() {
        Long examId = createObjectiveOnlyExam(null);
        startExamAndGetSubmissionId(examId);
        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId,
                submitObjectiveAnswers());
        entityManager.flush();

        assertThat(submitResult.get("status")).isEqualTo("GRADED");
        assertThat(submitResult.get("hasManualGrading")).isEqualTo(false);
        assertThat(((Number) submitResult.get("autoScore")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) submitResult.get("totalScore")).doubleValue()).isEqualTo(5.0);
        assertThat(countScoreRecords(examId)).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitObjectiveOnlyExamAfterDeadlineMarksGraded() {
        Long examId = createObjectiveOnlyExam(null);
        Long submissionId = startExamAndGetSubmissionId(examId);
        expireSubmissionDeadline(submissionId);

        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId,
                submitObjectiveAnswers());

        assertThat(submitResult.get("status")).isEqualTo("GRADED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitObjectiveOnlyExamUnderManualReleaseWritesHiddenScoreRecord() {
        Long examId = createObjectiveOnlyExam(
                com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.MANUAL_RELEASE);
        startExamAndGetSubmissionId(examId);
        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId,
                submitObjectiveAnswers());
        entityManager.flush();

        assertThat(submitResult.get("status")).isEqualTo("GRADED");
        // GRADED 即写成绩记录；MANUAL_RELEASE 只影响读取端可见性，不影响落库
        assertThat(countScoreRecords(examId)).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitObjectiveOnlyExamUnderImmediateVisibilityWritesScoreRecord() {
        Long examId = createObjectiveOnlyExam(
                com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.IMMEDIATE);
        startExamAndGetSubmissionId(examId);
        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId,
                submitObjectiveAnswers());
        entityManager.flush();

        assertThat(submitResult.get("status")).isEqualTo("GRADED");
        Double recordScore = jdbcTemplate.queryForObject(
                "SELECT score FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Double.class,
                examId,
                student.id()
        );
        assertThat(recordScore).isEqualTo(((Number) submitResult.get("autoScore")).doubleValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradeSubmissionAcceptsEmptyAnswersForObjectiveOnlySubmission() {
        Long examId = createObjectiveOnlyExam(null);
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitObjectiveAnswers());
        resetSubmissionStatus(submissionId, "SUBMITTED");

        Map<String, Object> gradeResult = (Map<String, Object>) examService.gradeSubmission(
                teacher,
                submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of())
        );

        assertThat(gradeResult.get("status")).isEqualTo("GRADED");
        assertThat(((Number) gradeResult.get("totalScore")).doubleValue()).isEqualTo(5.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradeSubmissionEmptyAnswersKeepsSubmittedWhenSubjectiveUnscored() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitFullAnswers());

        Map<String, Object> gradeResult = (Map<String, Object>) examService.gradeSubmission(
                teacher,
                submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of())
        );
        entityManager.flush();

        assertThat(gradeResult.get("status")).isEqualTo("SUBMITTED");
        assertThat(countScoreRecords(examId)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradeSubmissionEmptyAnswersDowngradesAutoSubmittedToSubmitted() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        expireSubmissionDeadline(submissionId);
        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId, submitFullAnswers());
        assertThat(submitResult.get("status")).isEqualTo("AUTO_SUBMITTED");

        Map<String, Object> gradeResult = (Map<String, Object>) examService.gradeSubmission(
                teacher,
                submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of())
        );

        assertThat(gradeResult.get("status")).isEqualTo("SUBMITTED");
    }

    @Test
    void gradeSubmissionRejectsAlreadyGradedSubmission() {
        Long examId = createObjectiveOnlyExam(null);
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitObjectiveAnswers());

        assertThatThrownBy(() -> examService.gradeSubmission(
                teacher,
                submissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of())
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许批改")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSubmissionDetailIncludesScoringMetadata() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitFullAnswers());

        Map<String, Object> detail = (Map<String, Object>) examService.getSubmissionDetail(teacher, submissionId);
        List<Map<String, Object>> answers = (List<Map<String, Object>>) detail.get("answers");
        Map<String, Object> firstAnswer = null;
        for (Map<String, Object> answer : answers) {
            if (Long.valueOf(1L).equals(answer.get("questionId"))) {
                firstAnswer = answer;
                break;
            }
        }

        assertThat(firstAnswer).isNotNull();
        assertThat(firstAnswer).containsKeys("autoScore", "suggestedScore", "scoreSource", "judgeDetail", "acceptedAutoScore");
        assertThat(((Number) firstAnswer.get("autoScore")).doubleValue()).isEqualTo(5.0);
        assertThat(firstAnswer.get("scoreSource")).isEqualTo("AUTO");
        assertThat(firstAnswer.get("acceptedAutoScore")).isEqualTo(true);
        assertThat(firstAnswer.get("judgeDetail")).asString().contains("matched");
    }

    private ExamRequests.SubmitExamRequest submitObjectiveAnswers() {
        return new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        ));
    }

    private ExamRequests.SubmitExamRequest submitFullAnswers() {
        return new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                new ExamRequests.SubmitAnswerItem(2L, List.of("true"), null, null, null),
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        ));
    }

    @SuppressWarnings("unchecked")
    private Long startExamAndGetSubmissionId(Long examId) {
        Map<String, Object> startResult = (Map<String, Object>) examService.startExam(student, examId);
        return ((Number) startResult.get("submissionId")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Long createObjectiveOnlyExam(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode mode) {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "客观题考试-" + System.nanoTime(),
                        "仅含单选题，用于自动结案测试",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(2),
                        30,
                        "PUBLISHED",
                        mode,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        );
        return ((Number) created.get("id")).longValue();
    }

    /** 通过 JDBC 直改截止时间，模拟交卷时已超时；clear 后 service 重新加载实体 */
    private void expireSubmissionDeadline(Long submissionId) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(1), submissionId);
        entityManager.clear();
    }

    /** 回写历史状态，模拟 TD1 上线前遗留的待评分卡单 */
    private void resetSubmissionStatus(Long submissionId, String status) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam_submission SET status = ? WHERE id = ?", status, submissionId);
        entityManager.clear();
    }

    private Integer countScoreRecords(Long examId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class,
                examId,
                student.id()
        );
    }

    private Long createDraftExam(boolean shuffleQuestions) {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "草稿考试-" + System.nanoTime(),
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "DRAFT",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        shuffleQuestions
                )
        );
        return ((Number) created.get("id")).longValue();
    }

    private ExamRequests.UpdateExamRequest buildUpdateRequest(String title,
                                                              Boolean shuffleQuestions,
                                                              List<Long> questionIds) {
        List<ExamRequests.ExamQuestionItem> items = new ArrayList<>();
        int sortOrder = 1;
        for (Long questionId : questionIds) {
            items.add(new ExamRequests.ExamQuestionItem(questionId, sortOrder++, 5.0));
        }
        return new ExamRequests.UpdateExamRequest(
                title,
                "",
                1L,
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(1),
                30,
                null,
                items,
                shuffleQuestions
        );
    }

    private Long createExamWithVisibility(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode mode) {
        return createExamWithVisibility(mode, false);
    }

    // ------------------------------------------------------------------
    // P0 回归：考试详情归属、时间窗、交卷宽限期、列表可操作标记、组卷归属
    // ------------------------------------------------------------------

    @Test
    void teacherDetailRejectsNonOwner() {
        Long examId = createPublishedExamForStudent();

        assertThatThrownBy(() -> examService.getExamDetail(anotherTeacher, examId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40300)
                .hasMessage("无权限查看该考试");
    }

    @Test
    void unassignedTeacherRejectedThenRestoredAllowsAllSevenExamEndpoints() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = 999L;
        jdbcTemplate.update(
                "INSERT INTO exam_submission (id, exam_id, student_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, 2, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                submissionId, examId);

        jdbcTemplate.update("DELETE FROM class_teacher WHERE teacher_user_id = 1");

        List<Runnable> guarded = List.of(
                () -> examService.getExamDetail(teacher, examId),
                () -> examService.changeStatus(teacher, examId, new ExamRequests.ChangeExamStatusRequest("CLOSED")),
                () -> examService.releaseScores(teacher, examId),
                () -> examService.getExamResults(teacher, examId),
                () -> examService.getSubmissionDetail(teacher, submissionId),
                () -> examService.gradeSubmission(teacher, submissionId, new ExamRequests.GradeExamSubmissionRequest(List.of())),
                () -> examService.confirmAnswer(teacher, submissionId,
                        new ExamRequests.ConfirmExamAnswerRequest(1L, "评语", 8D, false))
        );
        for (Runnable call : guarded) {
            assertThatThrownBy(call::run)
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", 40300);
        }

        jdbcTemplate.update("INSERT INTO class_teacher (class_id, teacher_user_id, role, assigned_at) VALUES (1, 1, 'SUBJECT', CURRENT_TIMESTAMP)");

        for (Runnable call : guarded) {
            try {
                call.run();
            } catch (BusinessException ex) {
                assertThat(ex.getCode()).isNotEqualTo(40300);
            }
        }
        assertThat(examService.getExamDetail(teacher, examId)).isNotNull();
        assertThat(examService.getExamResults(teacher, examId)).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherDetailIncludesAnswerJsonForOwner() {
        Long examId = createPublishedExamForStudent();

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");

        assertThat(questions).isNotEmpty();
        assertThat(questions.get(0).get("answerJson")).isNotNull();
        assertThat(questions.get(0).get("analysisText")).isNotNull();
    }

    @Test
    void startExamRejectsBeforeStartAt() {
        Long examId = createExamWithWindow("未开始考试-" + System.nanoTime(),
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(2), 30, "PUBLISHED");

        assertThatThrownBy(() -> examService.startExam(student, examId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("考试尚未开始");
        assertThat(countSubmissions(examId)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentDetailReturnsNoQuestionsBeforeStartAt() {
        Long examId = createExamWithWindow("未开始详情-" + System.nanoTime(),
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(2), 30, "PUBLISHED");

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);

        assertThat(detail.get("notStarted")).isEqualTo(true);
        assertThat((List<?>) detail.get("questions")).isEmpty();
        // 未开始不得回填任何提交与判分字段
        assertThat(detail).doesNotContainKeys("submissionStatus", "totalScore", "resultAvailable");
        // 基础字段仍完整下发，供前端展示考试信息
        assertThat(detail).containsKeys("id", "title", "classId", "className", "startAt", "endAt",
                "durationMinutes", "status", "scoreVisibilityMode", "shuffleQuestions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void teacherDetailKeepsQuestionsBeforeStartAt() {
        Long examId = createExamWithWindow("未开始教师详情-" + System.nanoTime(),
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(2), 30, "PUBLISHED");

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);

        assertThat(detail).doesNotContainKey("notStarted");
        assertThat((List<?>) detail.get("questions")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmAnswerRejectsWhenScoreReleased() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));
        jdbcTemplate.update("UPDATE exam SET score_released = TRUE WHERE id = ?", examId);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> examService.confirmAnswer(
                teacher,
                submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "发布后改分", 8D, false)
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessageContaining("成绩已发布或已到期");
    }

    @Test
    void confirmAnswerRejectsAfterDeadlineModeReachesEndAt() {
        Long examId = createPublishedExamForStudent(ScoreVisibilityMode.AFTER_DEADLINE);
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));
        jdbcTemplate.update("UPDATE exam SET end_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(1), examId);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> examService.confirmAnswer(
                teacher,
                submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "截止后改分", 8D, false)
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessageContaining("成绩已发布或已到期");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmAnswerAllowedWhenScoreVisibilityStillOpen() {
        // 冻结只应对「已发布 / AFTER_DEADLINE 且到期」生效，其余可见性下仍必须允许教师改分
        for (ScoreVisibilityMode mode : List.of(ScoreVisibilityMode.MANUAL_RELEASE, ScoreVisibilityMode.AFTER_TEACHER_CONFIRM)) {
            Long examId = createPublishedExamForStudent(mode);
            Long submissionId = startExamAndGetSubmissionId(examId);
            examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                    new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
            )));

            Map<String, Object> result = (Map<String, Object>) examService.confirmAnswer(
                    teacher,
                    submissionId,
                    new ExamRequests.ConfirmExamAnswerRequest(3L, "未发布允许改分", 8D, false)
            );

            assertThat(result.get("status")).isEqualTo("GRADED");
            assertThat(((Number) result.get("totalScore")).doubleValue()).isEqualTo(8.0);
            assertThat(countScoreRecords(examId)).isEqualTo(1);
        }
    }

    @Test
    void gradedDowngradeRemovesScoreRecord() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(3L, null, "统一错误处理与前端解析", null, null)
        )));
        examService.confirmAnswer(teacher, submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "给分", 8D, false));
        assertThat(countScoreRecords(examId)).isEqualTo(1);

        // 模拟一条未给分的作答（无题目快照按主观题处理），使再次重算时无法全确认
        jdbcTemplate.update(
                "INSERT INTO exam_answer (exam_submission_id, question_id, answer_json, score) VALUES (?, ?, ?, NULL)",
                submissionId, 999L, "{}");
        entityManager.flush();
        entityManager.clear();

        examService.confirmAnswer(teacher, submissionId,
                new ExamRequests.ConfirmExamAnswerRequest(3L, "再次给分", 9D, false));
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_submission WHERE id = ?", String.class, submissionId))
                .isEqualTo("SUBMITTED");
        assertThat(countScoreRecords(examId)).isZero();
    }

    @Test
    void startExamRejectsAfterEndAt() {
        Long examId = createExamWithWindow("已结束考试-" + System.nanoTime(),
                OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(1), 30, "PUBLISHED");

        assertThatThrownBy(() -> examService.startExam(student, examId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("考试已结束");
        assertThat(countSubmissions(examId)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void startExamCapsDeadlineAtToEndAt() {
        OffsetDateTime endAt = OffsetDateTime.now().plusMinutes(10);
        Long examId = createExamWithWindow("即将结束考试-" + System.nanoTime(),
                OffsetDateTime.now().minusMinutes(10), endAt, 30, "PUBLISHED");

        Map<String, Object> result = (Map<String, Object>) examService.startExam(student, examId);
        OffsetDateTime deadlineAt = (OffsetDateTime) result.get("deadlineAt");

        assertThat(deadlineAt).isAfter(endAt.minusSeconds(1));
        assertThat(deadlineAt).isBefore(endAt.plusSeconds(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startExamReturnsExistingSubmissionAfterEndAt() {
        Long examId = createExamWithWindow("结束但有进行中-" + System.nanoTime(),
                OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(1), 30, "PUBLISHED");
        Long submissionId = 910001L;
        jdbcTemplate.update("""
                INSERT INTO exam_submission (id, exam_id, student_id, started_at, deadline_at, status, created_at, updated_at)
                VALUES (?, ?, 2, ?, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, submissionId, examId, OffsetDateTime.now().minusMinutes(40), OffsetDateTime.now().minusMinutes(1));

        Map<String, Object> result = (Map<String, Object>) examService.startExam(student, examId);

        assertThat(((Number) result.get("submissionId")).longValue()).isEqualTo(submissionId);
        assertThat(((Number) result.get("remainingSeconds")).longValue()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void startExamDoesNotExtendDeadlineOnReentry() {
        Long examId = createPublishedExamForStudent();

        Map<String, Object> first = (Map<String, Object>) examService.startExam(student, examId);
        Map<String, Object> second = (Map<String, Object>) examService.startExam(student, examId);

        assertThat(((OffsetDateTime) second.get("deadlineAt")).toInstant().getEpochSecond())
                .isEqualTo(((OffsetDateTime) first.get("deadlineAt")).toInstant().getEpochSecond());
        assertThat(countSubmissions(examId)).isEqualTo(1);
    }

    @Test
    void submitExamRejectsAfterGracePeriod() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                OffsetDateTime.now().minusSeconds(121), submissionId);
        entityManager.clear();

        assertThatThrownBy(() -> examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        ))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("考试已结束，无法提交");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_submission WHERE id = ?", String.class, submissionId))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_answer WHERE exam_submission_id = ?", Integer.class, submissionId)).isZero();
    }

    @Test
    void submitExamAutoSubmitsWithinGracePeriod() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                OffsetDateTime.now().minusSeconds(60), submissionId);
        entityManager.clear();

        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        )));
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_submission WHERE id = ?", String.class, submissionId))
                .isEqualTo("AUTO_SUBMITTED");
        // 交卷会为试卷中每道题落一条答案记录（本夹具为 3 题）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_answer WHERE exam_submission_id = ?", Integer.class, submissionId))
                .isEqualTo(3);
    }

    @Test
    void submitExamMarksSubmittedBeforeDeadline() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);

        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        )));
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_submission WHERE id = ?", String.class, submissionId))
                .isEqualTo("SUBMITTED");
    }

    @Test
    void createExamRejectsForeignQuestionBankItem() {
        insertForeignQuestionBankItem();
        String title = "越权组卷考试-" + System.nanoTime();

        assertThatThrownBy(() -> examService.createExam(teacher, new ExamRequests.CreateExamRequest(
                title, "", 1L, OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1), 30,
                "DRAFT", null, List.of(new ExamRequests.ExamQuestionItem(9001L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40400)
                .hasMessage("题库题目不存在");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE title = ?", Integer.class, title)).isZero();
    }

    @Test
    void updateExamRejectsForeignQuestionAndKeepsOriginals() {
        String title = "待更新考试-" + System.nanoTime();
        Long examId = createExamWithWindow(title, OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(1), 30, "DRAFT");
        insertForeignQuestionBankItem();

        assertThatThrownBy(() -> examService.updateExam(teacher, examId, new ExamRequests.UpdateExamRequest(
                "改名后考试", "", 1L, OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1), 30,
                null, List.of(new ExamRequests.ExamQuestionItem(9001L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40400)
                .hasMessage("题库题目不存在");

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM exam WHERE id = ?", String.class, examId))
                .isEqualTo(title);
        assertThat(jdbcTemplate.queryForList(
                "SELECT question_id FROM exam_question WHERE exam_id = ?", Long.class, examId))
                .containsExactly(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentListFlagsReflectTimeWindow() {
        OffsetDateTime now = OffsetDateTime.now();
        Long futureExamId = createExamWithWindow("未来考试-" + System.nanoTime(),
                now.plusHours(1), now.plusHours(2), 30, "PUBLISHED");
        Long expiredExamId = createExamWithWindow("过期考试-" + System.nanoTime(),
                now.minusHours(2), now.minusHours(1), 30, "PUBLISHED");
        Long openExamId = createExamWithWindow("进行中考试-" + System.nanoTime(),
                now.minusMinutes(10), now.plusHours(1), 30, "PUBLISHED");
        Long resumableExamId = createExamWithWindow("宽限期内考试-" + System.nanoTime(),
                now.minusMinutes(10), now.plusHours(1), 30, "PUBLISHED");
        Long resumableSubmissionId = startExamAndGetSubmissionId(resumableExamId);
        Long expiredSubmissionExamId = createExamWithWindow("超宽限期考试-" + System.nanoTime(),
                now.minusMinutes(10), now.plusHours(1), 30, "PUBLISHED");
        Long expiredSubmissionId = startExamAndGetSubmissionId(expiredSubmissionExamId);
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                now.minusSeconds(60), resumableSubmissionId);
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                now.minusSeconds(121), expiredSubmissionId);
        entityManager.clear();

        List<Map<String, Object>> exams = (List<Map<String, Object>>) examService.listExams(student);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> exam : exams) {
            byId.put(((Number) exam.get("id")).longValue(), exam);
        }

        assertThat(byId.get(futureExamId).get("canStart")).isEqualTo(false);
        assertThat(byId.get(expiredExamId).get("canStart")).isEqualTo(false);
        assertThat(byId.get(openExamId).get("canStart")).isEqualTo(true);
        assertThat(byId.get(resumableExamId).get("canResume")).isEqualTo(true);
        assertThat(byId.get(expiredSubmissionExamId).get("canResume")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createDraftAllowsMissingEndAt() {
        Long examId = createExamWithWindow("草稿缺结束时间-" + System.nanoTime(),
                OffsetDateTime.now().plusHours(1), null, 30, "DRAFT");

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);

        assertThat(detail.get("startAt")).isNotNull();
        assertThat(detail.get("endAt")).isNull();
    }

    @Test
    void createDraftRejectsInvertedWindow() {
        String title = "草稿倒置窗口-" + System.nanoTime();
        OffsetDateTime endAt = OffsetDateTime.now().plusHours(1);

        assertThatThrownBy(() -> examService.createExam(teacher, new ExamRequests.CreateExamRequest(
                title, "", 1L, endAt.plusMinutes(1), endAt, 30, "DRAFT", null,
                List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("开始时间必须早于结束时间");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam WHERE title = ?", Integer.class, title)).isZero();
    }

    @Test
    void createPublishedRejectsMissingEndAt() {
        String title = "发布缺结束时间-" + System.nanoTime();

        assertThatThrownBy(() -> examService.createExam(teacher, new ExamRequests.CreateExamRequest(
                title, "", 1L, OffsetDateTime.now().minusHours(1), null, 30, "PUBLISHED", null,
                List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("请先设置考试结束时间");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam WHERE title = ?", Integer.class, title)).isZero();
    }

    @Test
    void createPublishedRejectsPastEndAt() {
        String title = "发布结束时间已过-" + System.nanoTime();

        assertThatThrownBy(() -> examService.createExam(teacher, new ExamRequests.CreateExamRequest(
                title, "", 1L, OffsetDateTime.now().minusHours(1), OffsetDateTime.now().minusMinutes(1),
                30, "PUBLISHED", null, List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("结束时间必须晚于当前时间");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam WHERE title = ?", Integer.class, title)).isZero();
    }

    @Test
    void createPublishedRejectsInvertedWindow() {
        String title = "发布倒置窗口-" + System.nanoTime();
        OffsetDateTime endAt = OffsetDateTime.now().plusHours(1);

        assertThatThrownBy(() -> examService.createExam(teacher, new ExamRequests.CreateExamRequest(
                title, "", 1L, endAt.plusMinutes(1), endAt, 30, "PUBLISHED", null,
                List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("开始时间必须早于结束时间");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam WHERE title = ?", Integer.class, title)).isZero();
    }

    @Test
    void publishDraftRejectsMissingEndAt() {
        Long examId = createExamWithWindow("发布缺结束时间草稿-" + System.nanoTime(),
                OffsetDateTime.now().minusHours(1), null, 30, "DRAFT");

        assertThatThrownBy(() -> examService.changeStatus(
                teacher, examId, new ExamRequests.ChangeExamStatusRequest("PUBLISHED")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("请先设置考试结束时间");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM exam WHERE id = ?", String.class, examId))
                .isEqualTo("DRAFT");
    }

    @Test
    void publishDraftRejectsPastEndAt() {
        Long examId = createExamWithWindow("发布过期草稿-" + System.nanoTime(),
                OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusMinutes(1), 30, "DRAFT");

        assertThatThrownBy(() -> examService.changeStatus(
                teacher, examId, new ExamRequests.ChangeExamStatusRequest("PUBLISHED")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("结束时间必须晚于当前时间");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM exam WHERE id = ?", String.class, examId))
                .isEqualTo("DRAFT");
    }

    @Test
    void publishDraftRejectsInvertedWindow() {
        Long examId = createExamWithWindow("发布倒置草稿-" + System.nanoTime(),
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1), 30, "DRAFT");
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam SET start_at = ?, end_at = ? WHERE id = ?",
                OffsetDateTime.now().plusHours(2), OffsetDateTime.now().plusHours(1), examId);
        entityManager.clear();

        assertThatThrownBy(() -> examService.changeStatus(
                teacher, examId, new ExamRequests.ChangeExamStatusRequest("PUBLISHED")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("开始时间必须早于结束时间");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM exam WHERE id = ?", String.class, examId))
                .isEqualTo("DRAFT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDraftAllowsMissingEndAt() {
        Long examId = createExamWithWindow("更新缺结束时间-" + System.nanoTime(),
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1), 30, "DRAFT");

        examService.updateExam(teacher, examId, new ExamRequests.UpdateExamRequest(
                "更新缺结束时间", "", 1L, OffsetDateTime.now().plusHours(1), null, 30, null,
                List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null));

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        assertThat(detail.get("endAt")).isNull();
    }

    @Test
    void updateDraftRejectsInvertedWindowWithoutMutation() {
        String title = "更新倒置窗口-" + System.nanoTime();
        Long examId = createExamWithWindow(title, OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(1), 30, "DRAFT");

        assertThatThrownBy(() -> examService.updateExam(teacher, examId, new ExamRequests.UpdateExamRequest(
                "改名后考试", "", 1L, OffsetDateTime.now().plusHours(2), OffsetDateTime.now().plusHours(1),
                30, null, List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("开始时间必须早于结束时间");

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM exam WHERE id = ?", String.class, examId))
                .isEqualTo(title);
        assertThat(jdbcTemplate.queryForList(
                "SELECT question_id FROM exam_question WHERE exam_id = ?", Long.class, examId))
                .containsExactly(1L);
    }

    @Test
    void updatePublishedExamIsRejected() {
        Long examId = createPublishedExamForStudent();

        assertThatThrownBy(() -> examService.updateExam(teacher, examId, new ExamRequests.UpdateExamRequest(
                "已发布不可改", "", 1L, OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1),
                30, null, List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("只有草稿状态的考试可以修改");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startClosedExamWithInProgressSubmissionIsRejectedButDetailResumable() {
        Long examId = createPublishedExamForStudent();
        examService.startExam(student, examId);
        examService.changeStatus(teacher, examId, new ExamRequests.ChangeExamStatusRequest("CLOSED"));

        assertThatThrownBy(() -> examService.startExam(student, examId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("考试未发布，不能开始");

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        assertThat(detail.get("submissionStatus")).isEqualTo("IN_PROGRESS");
        assertThat(detail.get("deadlineAt")).isNotNull();
        assertThat(detail.get("remainingSeconds")).isInstanceOf(Number.class);
    }

    private Long createExamWithWindow(String title, OffsetDateTime startAt, OffsetDateTime endAt,
                                      int durationMinutes, String status) {
        boolean needsRewrite = "PUBLISHED".equals(status)
                && (endAt == null || !endAt.isAfter(OffsetDateTime.now()));
        OffsetDateTime createEndAt = needsRewrite ? OffsetDateTime.now().plusHours(1) : endAt;
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(title, "", 1L, startAt, createEndAt, durationMinutes, status, null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)), null)
        );
        Long examId = ((Number) created.get("id")).longValue();
        if (needsRewrite) {
            entityManager.flush();
            jdbcTemplate.update("UPDATE exam SET start_at = ?, end_at = ? WHERE id = ?", startAt, endAt, examId);
            entityManager.clear();
        }
        return examId;
    }

    private void insertForeignQuestionBankItem() {
        jdbcTemplate.update("""
                INSERT INTO question_bank (id, code, type, stem, difficulty,
                                           options_json, answer_json, analysis_text, created_by, created_at, updated_at, school_id)
                VALUES (9001, 'Q-FOREIGN-9001', 'SINGLE', '他人题目', 'EASY',
                        '["A","B"]', '["A"]', '解析', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """);
    }

    private int countSubmissions(Long examId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_submission WHERE exam_id = ?", Integer.class, examId);
    }

    /** 插入多选 / 填空题库题目，供草稿恢复用例组卷使用。 */
    private void insertDraftQuestionBankFixtures() {
        jdbcTemplate.update("""
                INSERT INTO question_bank (id, code, type, stem, difficulty, options_json,
                                           answer_json, analysis_text, created_by, created_at, updated_at, school_id)
                VALUES (9001, 'Q-DRAFT-MULTI', 'MULTIPLE', '多选题草稿恢复', 'EASY',
                        '["A","B","C","D"]', '["A","C"]', '多选解析', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO question_bank (id, code, type, stem, difficulty, options_json,
                                           answer_json, analysis_text, created_by, created_at, updated_at, school_id)
                VALUES (9002, 'Q-DRAFT-FILL', 'FILL_BLANK', '填空题草稿恢复', 'EASY',
                        NULL, '[["甲","乙"]]', '填空解析', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """);
    }

    /** 组一份含多选与填空题的已发布考试，用于草稿恢复断言。 */
    @SuppressWarnings("unchecked")
    private Long createDraftRestoreExam() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "草稿恢复考试-" + System.nanoTime(),
                        "含多选与填空题",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "PUBLISHED",
                        ScoreVisibilityMode.AFTER_TEACHER_CONFIRM,
                        List.of(
                                new ExamRequests.ExamQuestionItem(9001L, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(9002L, 2, 5.0)
                        ),
                        null
                )
        );
        return ((Number) created.get("id")).longValue();
    }

    private Long createExamWithVisibility(com.opencode.teachingplatform.common.enums.ScoreVisibilityMode mode,
                                          boolean shuffleQuestions) {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "解析考试-" + System.nanoTime(),
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "PUBLISHED",
                        mode,
                        List.of(
                                new ExamRequests.ExamQuestionItem(1L, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(2L, 2, 3.0),
                                new ExamRequests.ExamQuestionItem(3L, 3, 10.0)
                        ),
                        shuffleQuestions
                )
        );
        return ((Number) created.get("id")).longValue();
    }

    private void insertLegacyInlineExam(Long examId, String status) {
        jdbcTemplate.update("""
                INSERT INTO exam (id, title, description, class_id, duration_minutes, status, created_by,
                                  score_visibility_mode, score_released, shuffle_questions, created_at, updated_at, school_id)
                VALUES (?, '历史内联题考试', '', 1, 60, ?, 1, 'IMMEDIATE', FALSE, FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, examId, status);
        jdbcTemplate.update("""
                INSERT INTO exam_question (exam_id, question_id, sort_order, question_score, question_snapshot_json)
                VALUES (?, NULL, 1, 2.0, '{}')
                """, examId);
    }

    private Set<String> getColumnNames(String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toUpperCase());
                }
            }
        }
        return columns;
    }

    private void overwriteExamQuestionSnapshot(Long examId, Long questionId, String snapshotJson) {
        jdbcTemplate.update(
                "UPDATE exam_question SET question_snapshot_json = ? WHERE exam_id = ? AND question_id = ?",
                snapshotJson,
                examId,
                questionId
        );
    }

    private Map<String, Object> findQuestion(List<Map<String, Object>> questions, Long questionId) {
        return questions.stream()
                .filter(item -> questionId.equals(((Number) item.get("questionId")).longValue()))
                .findFirst()
                .orElseThrow();
    }

    private String buildExamSnapshotJson(String stem, int sortOrder, double score) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionBankId", 1L);
        snapshot.put("sourceType", "BANK");
        snapshot.put("questionType", "SINGLE");
        snapshot.put("stem", stem);
        snapshot.put("sortOrder", sortOrder);
        snapshot.put("score", score);
        snapshot.put("optionsJson", "[{\"label\":\"A\",\"content\":\"Snapshot option\"}]");
        snapshot.put("answerJson", "[\"A\"]");
        snapshot.put("scoringConfigJson", "{\"correctAnswer\":\"A\"}");
        snapshot.put("reusableFromBank", true);
        try {
            return new ObjectMapper().writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void createExamRejectsUnassignedClass() {
        CurrentUser unassignedTeacher = new CurrentUser(999L, "t9999", "其他教师", UserRole.TEACHER);

        assertThatThrownBy(() -> examService.createExam(
                unassignedTeacher,
                new ExamRequests.CreateExamRequest(
                        "未授权班级考试",
                        "应被拒绝",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "DRAFT",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40300);
    }

    @Test
    void updateExamRejectsUnassignedClass() {
        Long examId = createPublishedExamForStudent();
        CurrentUser unassignedTeacher = new CurrentUser(999L, "t9999", "其他教师", UserRole.TEACHER);

        assertThatThrownBy(() -> examService.updateExam(
                unassignedTeacher,
                examId,
                new ExamRequests.UpdateExamRequest(
                        "未授权更新",
                        "应被拒绝",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0)),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40300);
    }

    // ------------------------------------------------------------------
    // P0 回归：考试判分遮蔽（TD13–TD15）与重复题目防护（TD37）
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void studentDetailMasksScoreFieldsAcrossVisibilityModes() {
        // AFTER_TEACHER_CONFIRM：已交卷但教师未确认 → 判分字段全部遮蔽
        Long confirmExamId = createPublishedExamForStudent(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        startExamAndGetSubmissionId(confirmExamId);
        examService.submitExam(student, confirmExamId, submitFullAnswers());
        assertStudentDetailScoreHidden(
                (Map<String, Object>) examService.getExamDetail(student, confirmExamId));

        // AFTER_DEADLINE：考试尚未结束 → 判分字段全部遮蔽
        Long deadlineExamId = createExamWithVisibility(ScoreVisibilityMode.AFTER_DEADLINE, false);
        startExamAndGetSubmissionId(deadlineExamId);
        examService.submitExam(student, deadlineExamId, submitFullAnswers());
        assertStudentDetailScoreHidden(
                (Map<String, Object>) examService.getExamDetail(student, deadlineExamId));

        // MANUAL_RELEASE：教师尚未发布成绩 → 判分字段全部遮蔽
        Long manualExamId = createExamWithVisibility(ScoreVisibilityMode.MANUAL_RELEASE, false);
        startExamAndGetSubmissionId(manualExamId);
        examService.submitExam(student, manualExamId, submitFullAnswers());
        assertStudentDetailScoreHidden(
                (Map<String, Object>) examService.getExamDetail(student, manualExamId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentDetailRevealsScoresWhenVisibilityReached() {
        // IMMEDIATE：0 分同样可见，不能被当成“无成绩”而返回 null
        Long immediateExamId = createObjectiveOnlyExam(ScoreVisibilityMode.IMMEDIATE);
        startExamAndGetSubmissionId(immediateExamId);
        examService.submitExam(student, immediateExamId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("B"), null, null, null)
        )));
        Map<String, Object> immediateDetail =
                (Map<String, Object>) examService.getExamDetail(student, immediateExamId);
        assertThat(immediateDetail.get("resultAvailable")).isEqualTo(true);
        assertThat(immediateDetail.get("autoScore")).isNotNull();
        assertThat(((Number) immediateDetail.get("autoScore")).doubleValue()).isEqualTo(0.0);
        assertThat(immediateDetail.get("manualScore")).isNotNull();
        assertThat(((Number) immediateDetail.get("manualScore")).doubleValue()).isEqualTo(0.0);
        assertThat(immediateDetail.get("totalScore")).isNotNull();
        assertThat(((Number) immediateDetail.get("totalScore")).doubleValue()).isEqualTo(0.0);
        Map<String, Object> immediateQuestion = findQuestion(
                (List<Map<String, Object>>) immediateDetail.get("questions"), 1L);
        assertThat(immediateQuestion.get("isCorrect")).isEqualTo(false);
        assertThat(immediateQuestion.get("earnedScore")).isNotNull();
        assertThat(((Number) immediateQuestion.get("earnedScore")).doubleValue()).isEqualTo(0.0);

        // AFTER_TEACHER_CONFIRM：教师确认后可见
        Long confirmExamId = createPublishedExamForStudent(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        Long confirmSubmissionId = startExamAndGetSubmissionId(confirmExamId);
        examService.submitExam(student, confirmExamId, submitFullAnswers());
        examService.gradeSubmission(teacher, confirmSubmissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, 7.0, "回答正确")
                )));
        Map<String, Object> confirmDetail =
                (Map<String, Object>) examService.getExamDetail(student, confirmExamId);
        assertThat(confirmDetail.get("resultAvailable")).isEqualTo(true);
        assertThat(((Number) confirmDetail.get("autoScore")).doubleValue()).isEqualTo(8.0);
        assertThat(((Number) confirmDetail.get("manualScore")).doubleValue()).isEqualTo(7.0);
        assertThat(((Number) confirmDetail.get("totalScore")).doubleValue()).isEqualTo(15.0);
        assertThat(findQuestion((List<Map<String, Object>>) confirmDetail.get("questions"), 1L).get("isCorrect"))
                .isEqualTo(true);

        // AFTER_DEADLINE：结束时间已过 → 可见（须先批改进入 GRADED）
        Long deadlineExamId = createExamWithVisibility(ScoreVisibilityMode.AFTER_DEADLINE, false);
        Long deadlineSubmissionId = startExamAndGetSubmissionId(deadlineExamId);
        examService.submitExam(student, deadlineExamId, submitFullAnswers());
        examService.gradeSubmission(teacher, deadlineSubmissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, 7.0, "回答正确")
                )));
        assertThat(((Map<String, Object>) examService.getExamDetail(student, deadlineExamId))
                .get("resultAvailable")).isEqualTo(false);
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam SET end_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(1), deadlineExamId);
        entityManager.clear();
        Map<String, Object> deadlineDetail =
                (Map<String, Object>) examService.getExamDetail(student, deadlineExamId);
        assertThat(deadlineDetail.get("resultAvailable")).isEqualTo(true);
        assertThat(((Number) deadlineDetail.get("totalScore")).doubleValue()).isEqualTo(15.0);

        // MANUAL_RELEASE：教师发布后可见（须先批改进入 GRADED）
        Long manualExamId = createExamWithVisibility(ScoreVisibilityMode.MANUAL_RELEASE, false);
        Long manualSubmissionId = startExamAndGetSubmissionId(manualExamId);
        examService.submitExam(student, manualExamId, submitFullAnswers());
        examService.gradeSubmission(teacher, manualSubmissionId,
                new ExamRequests.GradeExamSubmissionRequest(List.of(
                        new ExamRequests.GradeExamAnswerItem(3L, 7.0, "回答正确")
                )));
        examService.releaseScores(teacher, manualExamId);
        Map<String, Object> manualDetail =
                (Map<String, Object>) examService.getExamDetail(student, manualExamId);
        assertThat(manualDetail.get("resultAvailable")).isEqualTo(true);
        assertThat(((Number) manualDetail.get("totalScore")).doubleValue()).isEqualTo(15.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonFinalSubmissionStaysInvisibleUnderImmediateAndExpiredDeadline() {
        // IMMEDIATE：含主观题的答卷交卷后仍为 SUBMITTED，未进入最终态 → 一律不可见
        Long immediateExamId = createPublishedExamForStudent(ScoreVisibilityMode.IMMEDIATE);
        startExamAndGetSubmissionId(immediateExamId);
        examService.submitExam(student, immediateExamId, submitFullAnswers());
        Map<String, Object> immediateDetail =
                (Map<String, Object>) examService.getExamDetail(student, immediateExamId);
        assertThat(immediateDetail.get("submissionStatus")).isEqualTo("SUBMITTED");
        assertThat(immediateDetail.get("resultAvailable")).isEqualTo(false);
        assertThat(immediateDetail.get("autoScore")).isNull();
        assertThat(immediateDetail.get("totalScore")).isNull();

        // AFTER_DEADLINE：即使考试已结束，只要提交未结案（SUBMITTED）同样不可见
        Long deadlineExamId = createExamWithVisibility(ScoreVisibilityMode.AFTER_DEADLINE, false);
        startExamAndGetSubmissionId(deadlineExamId);
        examService.submitExam(student, deadlineExamId, submitFullAnswers());
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam SET end_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(1), deadlineExamId);
        entityManager.clear();
        Map<String, Object> deadlineDetail =
                (Map<String, Object>) examService.getExamDetail(student, deadlineExamId);
        assertThat(deadlineDetail.get("submissionStatus")).isEqualTo("SUBMITTED");
        assertThat(deadlineDetail.get("resultAvailable")).isEqualTo(false);
        assertThat(deadlineDetail.get("autoScore")).isNull();
        assertThat(deadlineDetail.get("totalScore")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitResponseMasksScoresWhenScoreHidden() {
        Long examId = createPublishedExamForStudent(ScoreVisibilityMode.AFTER_TEACHER_CONFIRM);
        startExamAndGetSubmissionId(examId);

        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(
                student, examId, submitFullAnswers());

        assertThat(submitResult).containsKeys(
                "submissionId", "status", "resultAvailable", "autoScore", "totalScore", "hasManualGrading");
        assertThat(submitResult.get("resultAvailable")).isEqualTo(false);
        assertThat(submitResult.get("autoScore")).isNull();
        assertThat(submitResult.get("totalScore")).isNull();
        // 状态与人工判分标记不受遮蔽影响
        assertThat(submitResult.get("status")).isEqualTo("SUBMITTED");
        assertThat(submitResult.get("hasManualGrading")).isEqualTo(true);
        assertThat(submitResult.get("submissionId")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitResponseExposesScoresWhenImmediateVisibility() {
        // 仅客观题考试交卷即 GRADED，IMMEDIATE 下响应直接下发分数
        Long examId = createObjectiveOnlyExam(ScoreVisibilityMode.IMMEDIATE);
        startExamAndGetSubmissionId(examId);

        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(
                student, examId, submitObjectiveAnswers());

        assertThat(submitResult.get("status")).isEqualTo("GRADED");
        assertThat(submitResult.get("resultAvailable")).isEqualTo(true);
        assertThat(submitResult.get("gradingInProgress")).isEqualTo(false);
        assertThat(((Number) submitResult.get("autoScore")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) submitResult.get("totalScore")).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void createExamRejectsDuplicateQuestionIds() {
        String title = "重复题目考试-" + System.nanoTime();

        assertThatThrownBy(() -> examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        title,
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        "DRAFT",
                        null,
                        List.of(
                                new ExamRequests.ExamQuestionItem(1L, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(1L, 2, 5.0)
                        ),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("试卷题目不能重复");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam WHERE title = ?", Integer.class, title)).isZero();
    }

    @Test
    void updateExamRejectsDuplicateQuestionIdsAndKeepsOriginalQuestions() {
        String title = "待更新考试-" + System.nanoTime();
        Long examId = createExamWithWindow(title, OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(1), 30, "DRAFT");

        assertThatThrownBy(() -> examService.updateExam(
                teacher,
                examId,
                new ExamRequests.UpdateExamRequest(
                        "改名后考试",
                        "",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        30,
                        null,
                        List.of(
                                new ExamRequests.ExamQuestionItem(1L, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(1L, 2, 5.0)
                        ),
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("试卷题目不能重复");

        // 拒绝路径不得留下任何写入：标题与原有题目保持不变
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM exam WHERE id = ?", String.class, examId)).isEqualTo(title);
        assertThat(jdbcTemplate.queryForList(
                "SELECT question_id FROM exam_question WHERE exam_id = ?", Long.class, examId))
                .containsExactly(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitExamDeduplicatesLegacyDuplicatePaperQuestions() {
        Long examId = createObjectiveOnlyExam(ScoreVisibilityMode.IMMEDIATE);
        // 绕过 createExam 的重复校验直插第二行同 questionId，模拟存量含重复题目的试卷
        // （exam_question 无唯一约束，V23 也不对其加约束）
        jdbcTemplate.update("""
                INSERT INTO exam_question (exam_id, question_id, sort_order, question_score)
                VALUES (?, 1, 2, 5)
                """, examId);
        Long submissionId = startExamAndGetSubmissionId(examId);

        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(
                student, examId, submitObjectiveAnswers());
        entityManager.flush();

        Integer answerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_answer WHERE exam_submission_id = ? AND question_id = 1",
                Integer.class, submissionId);
        Double persistedAutoScore = jdbcTemplate.queryForObject(
                "SELECT auto_score FROM exam_submission WHERE id = ?", Double.class, submissionId);

        // 只落一行答案，且同题只计一次分
        assertThat(answerCount).isEqualTo(1);
        assertThat(((Number) submitResult.get("autoScore")).doubleValue()).isEqualTo(5.0);
        assertThat(persistedAutoScore).isEqualTo(5.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resubmissionReplacesAnswersWithoutResidualRows() {
        Long examId = createObjectiveOnlyExam(ScoreVisibilityMode.IMMEDIATE);
        Long submissionId = startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitObjectiveAnswers());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_answer WHERE exam_submission_id = ?",
                Integer.class, submissionId)).isEqualTo(1);

        // 回置为进行中后再次交卷，验证 deleteByExamSubmissionId 的批量删除不会残留旧答案行
        resetSubmissionStatus(submissionId, "IN_PROGRESS");
        Map<String, Object> secondSubmit = (Map<String, Object>) examService.submitExam(
                student, examId, new ExamRequests.SubmitExamRequest(List.of(
                        new ExamRequests.SubmitAnswerItem(1L, List.of("B"), null, null, null)
                )));
        entityManager.flush();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT question_id, answer_json, score FROM exam_answer WHERE exam_submission_id = ?",
                submissionId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("ANSWER_JSON")).asString().contains("B");
        assertThat(((Number) rows.get(0).get("SCORE")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) secondSubmit.get("autoScore")).doubleValue()).isEqualTo(0.0);
    }

    // ------------------------------------------------------------------
    // P4 草稿与成绩最终态（技术方案 §8.1 / TD2–TD16）
    // ------------------------------------------------------------------

    @Test
    void saveDraftRejectsNonMember() {
        Long examId = createPublishedExamForStudent();
        CurrentUser outsider = new CurrentUser(999L, "20269999", "非本班学生", UserRole.STUDENT);

        assertThatThrownBy(() -> examService.saveDraft(outsider, examId, submitObjectiveAnswers()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40300)
                .hasMessage("无权限保存草稿");
    }

    @Test
    void saveDraftRejectsMissingSubmission() {
        Long examId = createPublishedExamForStudent();

        assertThatThrownBy(() -> examService.saveDraft(student, examId, submitObjectiveAnswers()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40400)
                .hasMessage("未找到考试提交记录");
    }

    @Test
    void saveDraftRejectsSubmittedExam() {
        Long examId = createObjectiveOnlyExam(ScoreVisibilityMode.IMMEDIATE);
        startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitObjectiveAnswers());

        assertThatThrownBy(() -> examService.saveDraft(student, examId, submitObjectiveAnswers()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("考试已提交，无法保存草稿");
    }

    @Test
    void saveDraftRejectsExpiredDeadline() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);
        // 超过交卷宽限期：deadline + 120s 之后
        entityManager.flush();
        jdbcTemplate.update("UPDATE exam_submission SET deadline_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(10), submissionId);
        entityManager.clear();

        assertThatThrownBy(() -> examService.saveDraft(student, examId, submitObjectiveAnswers()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("考试已结束，无法保存草稿");
    }

    @Test
    void saveDraftRejectsQuestionOutsideExam() {
        // 该考试只含 Q1，Q3 不属于本场考试
        Long examId = createObjectiveOnlyExam(null);
        startExamAndGetSubmissionId(examId);

        assertThatThrownBy(() -> examService.saveDraft(student, examId, submitFullAnswers()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40000)
                .hasMessage("题目不属于该考试");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveDraftDedupesSameQuestionAndRestoresDraftInInProgressDetail() {
        Long examId = createPublishedExamForStudent();
        Long submissionId = startExamAndGetSubmissionId(examId);

        // 同一题提交两次，后出现的条目覆盖前面的；savedCount 按去重后计算
        Map<String, Object> saved = (Map<String, Object>) examService.saveDraft(student, examId,
                new ExamRequests.SubmitExamRequest(List.of(
                        new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null),
                        new ExamRequests.SubmitAnswerItem(1L, null, null,
                                "{\"kind\":\"single\",\"selectedKey\":\"B\"}", null)
                )));

        assertThat(saved.get("submissionId")).isEqualTo(submissionId);
        assertThat(saved.get("savedCount")).isEqualTo(1);
        assertThat(saved.get("savedAt")).isNotNull();

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        assertThat(detail.get("submissionStatus")).isEqualTo("IN_PROGRESS");
        assertThat(detail.get("resultAvailable")).isEqualTo(false);
        assertThat(detail.get("gradingInProgress")).isEqualTo(false);
        assertThat(findQuestion(questions, 1L).get("studentAnswer"))
                .isEqualTo("{\"kind\":\"single\",\"selectedKey\":\"B\"}");
        // 作答中不泄露判分、标准答案与解析
        assertThat(findQuestion(questions, 1L))
                .doesNotContainKeys("answerJson", "analysisText", "isCorrect", "earnedScore", "teacherComment");
        // 草稿不写成绩记录，也不改变提交状态
        assertThat(countScoreRecords(examId)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveDraftRestoresMultipleChoiceAndFillBlankDrafts() {
        insertDraftQuestionBankFixtures();
        Long examId = createDraftRestoreExam();
        startExamAndGetSubmissionId(examId);

        String multipleDraft = "{\"kind\":\"multiple\",\"selectedKeys\":[\"A\",\"C\"]}";
        String fillDraft = "{\"kind\":\"fill\",\"blanks\":[{\"index\":1,\"answer\":\"甲\"},{\"index\":2,\"answer\":\"乙\"}]}";
        Map<String, Object> saved = (Map<String, Object>) examService.saveDraft(student, examId,
                new ExamRequests.SubmitExamRequest(List.of(
                        new ExamRequests.SubmitAnswerItem(9001L, null, null, multipleDraft, null),
                        new ExamRequests.SubmitAnswerItem(9002L, null, null, fillDraft, null)
                )));
        assertThat(saved.get("savedCount")).isEqualTo(2);

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        assertThat(findQuestion(questions, 9001L).get("studentAnswer")).isEqualTo(multipleDraft);
        assertThat(findQuestion(questions, 9002L).get("studentAnswer")).isEqualTo(fillDraft);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveDraftClearsAnswerWithoutError() {
        Long examId = createPublishedExamForStudent();
        startExamAndGetSubmissionId(examId);
        examService.saveDraft(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
        )));

        // 清空：文字、选项、结构化答案全部为空
        Map<String, Object> cleared = (Map<String, Object>) examService.saveDraft(student, examId,
                new ExamRequests.SubmitExamRequest(List.of(
                        new ExamRequests.SubmitAnswerItem(1L, null, null, null, null)
                )));
        assertThat(cleared.get("savedCount")).isEqualTo(1);

        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(student, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        assertThat(findQuestion(questions, 1L)).containsKey("studentAnswer");
        assertThat(findQuestion(questions, 1L).get("studentAnswer")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveDraftWithEmptyAnswersReturnsZeroSavedCount() {
        Long examId = createPublishedExamForStudent();
        startExamAndGetSubmissionId(examId);

        Map<String, Object> saved = (Map<String, Object>) examService.saveDraft(
                student, examId, new ExamRequests.SubmitExamRequest(null));
        assertThat(saved.get("savedCount")).isEqualTo(0);
        assertThat(saved.get("submissionId")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitExamReturnsGradingInProgressWhileSubjectivePending() {
        Long examId = createPublishedExamForStudent(ScoreVisibilityMode.IMMEDIATE);
        startExamAndGetSubmissionId(examId);

        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(
                student, examId, submitFullAnswers());

        assertThat(submitResult.get("status")).isEqualTo("SUBMITTED");
        assertThat(submitResult.get("gradingInProgress")).isEqualTo(true);
        assertThat(submitResult.get("resultAvailable")).isEqualTo(false);
    }

    @Test
    void releaseScoresBackfillsMissingRecordIdempotently() {
        Long examId = createObjectiveOnlyExam(ScoreVisibilityMode.MANUAL_RELEASE);
        startExamAndGetSubmissionId(examId);
        examService.submitExam(student, examId, submitObjectiveAnswers());
        entityManager.flush();
        // 模拟历史缺失：清掉交卷时已写的成绩记录，发布时补写
        jdbcTemplate.update("DELETE FROM score_record WHERE business_type = 'EXAM' AND business_id = ?", examId);
        assertThat(countScoreRecords(examId)).isZero();

        examService.releaseScores(teacher, examId);
        assertThat(countScoreRecords(examId)).isEqualTo(1);
        // 重复发布保持幂等，不产生第二条记录
        examService.releaseScores(teacher, examId);
        assertThat(countScoreRecords(examId)).isEqualTo(1);
    }

    @Test
    void releaseScoresRejectsNonOwner() {
        Long examId = createObjectiveOnlyExam(ScoreVisibilityMode.MANUAL_RELEASE);

        assertThatThrownBy(() -> examService.releaseScores(anotherTeacher, examId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40300)
                .hasMessage("无权限发布该考试成绩");
    }

    /**
     * 断言学生详情的判分字段已被遮蔽，但学生自己的作答仍然返回。
     */
    @SuppressWarnings("unchecked")
    private void assertStudentDetailScoreHidden(Map<String, Object> detail) {
        assertThat(detail).containsKeys("autoScore", "manualScore", "totalScore", "resultAvailable");
        assertThat(detail.get("resultAvailable")).isEqualTo(false);
        assertThat(detail.get("autoScore")).isNull();
        assertThat(detail.get("manualScore")).isNull();
        assertThat(detail.get("totalScore")).isNull();
        assertThat(detail.get("submissionStatus")).isEqualTo("SUBMITTED");

        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");
        Map<String, Object> firstQuestion = findQuestion(questions, 1L);
        // 学生答案始终可见（TD15）
        assertThat(firstQuestion.get("studentAnswer")).isNotNull();
        // 逐题判分与正确答案、解析均不泄露
        assertThat(firstQuestion).containsKeys("isCorrect", "earnedScore", "teacherComment");
        assertThat(firstQuestion.get("isCorrect")).isNull();
        assertThat(firstQuestion.get("earnedScore")).isNull();
        assertThat(firstQuestion.get("teacherComment")).isNull();
        assertThat(firstQuestion).doesNotContainKey("answerJson");
        assertThat(firstQuestion).doesNotContainKey("analysisText");
    }
}
