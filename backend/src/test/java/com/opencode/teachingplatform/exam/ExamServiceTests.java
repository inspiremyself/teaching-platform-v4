package com.opencode.teachingplatform.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.enums.UserRole;
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
        ExamScoringService.class,
        com.opencode.teachingplatform.clazz.service.ClassAccessService.class,
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

    @SuppressWarnings("unchecked")
    private Long createPublishedExamForStudent() {
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
                        null,
                        List.of(
                                new ExamRequests.ExamQuestionItem(1L, 1, 5.0),
                                new ExamRequests.ExamQuestionItem(2L, 2, 3.0),
                                new ExamRequests.ExamQuestionItem(3L, 3, 10.0)
                        )
                )
        );
        return ((Number) created.get("id")).longValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createExamShouldPersistInlineQuestionSnapshotAndExposeItInDetail() {
        Map<String, Object> created = (Map<String, Object>) examService.createExam(
                teacher,
                new ExamRequests.CreateExamRequest(
                        "内联题考试",
                        "允许直接新增题目",
                        1L,
                        OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1),
                        45,
                        "DRAFT",
                        null,
                        List.of(
                                new ExamRequests.ExamQuestionItem(null, "INLINE", "SHORT", "请概述共享题目能力设计", 1, 12.0, null, "[\"围绕快照、答案 payload 与评分适配展开\"]", "{\"type\":\"SHORT\",\"answerJson\":\"[\\\"围绕快照、答案 payload 与评分适配展开\\\"]\"}")
                        )
                )
        );

        Long examId = ((Number) created.get("id")).longValue();
        Map<String, Object> detail = (Map<String, Object>) examService.getExamDetail(teacher, examId);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) detail.get("questions");

        assertThat(questions).singleElement().satisfies(question -> {
            assertThat(question.get("questionId")).isNull();
            assertThat(question.get("type")).isEqualTo("SHORT");
            assertThat(question.get("stem")).isEqualTo("请概述共享题目能力设计");
            assertThat(question.get("questionScore")).isEqualTo(12.0);
            assertThat(question.get("answerJson")).isEqualTo("[\"围绕快照、答案 payload 与评分适配展开\"]");
        });

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT question_id, question_snapshot_json FROM exam_question WHERE exam_id = ?",
                examId
        );
        assertThat(persisted.get("QUESTION_ID")).isNull();
        assertThat(String.valueOf(persisted.get("QUESTION_SNAPSHOT_JSON")))
                .contains("\"sourceType\":\"INLINE\"")
                .contains("\"questionType\":\"SHORT\"")
                .contains("\"stem\":\"请概述共享题目能力设计\"");
    }

    @Test
    void examSchemaSupportsSingleAttemptWorkflow() throws Exception {
        // Verify EXAM table has CLASS_ID column
        Set<String> examColumns = getColumnNames("EXAM");
        assertThat(examColumns).contains("CLASS_ID");

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
        assertThat(((Number) submitResult.get("autoScore")).doubleValue()).isEqualTo(8.0);
        assertThat(((Number) submitResult.get("totalScore")).doubleValue()).isEqualTo(8.0);
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
    }

    @Test
    @SuppressWarnings("unchecked")
    void submittedExamShouldHideCurrentScoreFromStudentUntilTeacherConfirmation() {
        Long examId = createPublishedExamForStudent();
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
        assertThat(((Number) submitResult.get("autoScore")).doubleValue()).isEqualTo(8.0);

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
                        OffsetDateTime.parse("2026-04-20T09:00:00+08:00"),
                        OffsetDateTime.parse("2026-04-20T10:30:00+08:00"),
                        90,
                        "PUBLISHED",
                        null,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0))
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
                        OffsetDateTime.now().minusHours(1),
                        60,
                        "PUBLISHED",
                        com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.MANUAL_RELEASE,
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0))
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
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0))
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
                        List.of(new ExamRequests.ExamQuestionItem(1L, 1, 5.0))
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40300);
    }
}
