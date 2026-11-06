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
                        OffsetDateTime.now().minusHours(1),
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
        examService.startExam(student, examId);
        examService.submitExam(student, examId, new ExamRequests.SubmitExamRequest(List.of(
                new ExamRequests.SubmitAnswerItem(1L, List.of("A"), null, null, null)
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
    void submitObjectiveOnlyExamUnderManualReleaseWritesNoScoreRecord() {
        Long examId = createObjectiveOnlyExam(
                com.opencode.teachingplatform.common.enums.ScoreVisibilityMode.MANUAL_RELEASE);
        startExamAndGetSubmissionId(examId);
        Map<String, Object> submitResult = (Map<String, Object>) examService.submitExam(student, examId,
                submitObjectiveAnswers());
        entityManager.flush();

        assertThat(submitResult.get("status")).isEqualTo("GRADED");
        assertThat(countScoreRecords(examId)).isZero();
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
                                  score_visibility_mode, score_released, shuffle_questions, created_at, updated_at)
                VALUES (?, '历史内联题考试', '', 1, 60, ?, 1, 'IMMEDIATE', FALSE, FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
}
