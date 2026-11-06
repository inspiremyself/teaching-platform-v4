package com.opencode.teachingplatform.grading;

import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.common.enums.ActivityStatus;
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.enums.ScoreVisibilityMode;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import com.opencode.teachingplatform.homework.entity.Homework;
import com.opencode.teachingplatform.homework.entity.HomeworkQuestion;
import com.opencode.teachingplatform.homework.repository.HomeworkQuestionRepository;
import com.opencode.teachingplatform.homework.repository.HomeworkRepository;
import com.opencode.teachingplatform.question.entity.QuestionAttachment;
import com.opencode.teachingplatform.question.entity.QuestionBank;
import com.opencode.teachingplatform.question.repository.QuestionBankRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ScoreRecordWriter.class)
class GradingPersistenceTests {

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private HomeworkQuestionRepository homeworkQuestionRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private ScoreRecordRepository scoreRecordRepository;

    @Autowired
    private ScoreRecordWriter scoreRecordWriter;

    @Test
    void homeworkQuestionSupportsInlineSnapshotWithoutQuestionBankId() {
        Homework homework = new Homework();
        homework.setTitle("内联题作业");
        homework.setDescription("用于验证快照题保存");
        homework.setClassId(1L);
        homework.setCreatedBy(1L);
        homework.setStatus(ActivityStatus.DRAFT);
        homework.setScoreVisibilityMode(ScoreVisibilityMode.MANUAL_RELEASE);
        Homework savedHomework = homeworkRepository.saveAndFlush(homework);

        HomeworkQuestion question = new HomeworkQuestion();
        question.setHomeworkId(savedHomework.getId());
        question.setQuestionId(null);
        question.setSortOrder(1);
        question.setQuestionScore(15D);
        question.setQuestionSnapshotJson("{\"stem\":\"请说明架构分层边界\"}");

        HomeworkQuestion savedQuestion = homeworkQuestionRepository.saveAndFlush(question);
        entityManager.clear();

        Homework reloadedHomework = homeworkRepository.findById(savedHomework.getId()).orElseThrow();
        HomeworkQuestion reloadedQuestion = homeworkQuestionRepository.findById(savedQuestion.getId()).orElseThrow();

        assertThat(reloadedHomework.getScoreVisibilityMode()).isEqualTo(ScoreVisibilityMode.MANUAL_RELEASE);
        assertThat(reloadedQuestion.getQuestionId()).isNull();
        assertThat(reloadedQuestion.getQuestionSnapshotJson()).isEqualTo("{\"stem\":\"请说明架构分层边界\"}");
    }

    @Test
    void questionBankAndAttachmentPersistNewGradingFields() {
        jdbcTemplate.execute("ALTER TABLE question_bank ALTER COLUMN id RESTART WITH 100");

        QuestionBank questionBank = new QuestionBank();
        questionBank.setCode("QB-GRADING-001");
        questionBank.setType("SUBJECTIVE");
        questionBank.setStem("解释统一评分模型的价值");
        questionBank.setDifficulty("MEDIUM");
        questionBank.setDefaultScore(10);
        questionBank.setAnswerJson("{\"keywords\":[\"一致性\"]}");
        questionBank.setScoringConfigJson("{\"mode\":\"RECOMMEND_ONLY\"}");
        questionBank.setCreatedBy(1L);
        QuestionBank savedQuestionBank = questionBankRepository.saveAndFlush(questionBank);

        QuestionAttachment attachment = new QuestionAttachment();
        attachment.setOwnerType("QUESTION_BANK");
        attachment.setOwnerId(savedQuestionBank.getId());
        attachment.setFileName("rubric.pdf");
        attachment.setFilePath("/uploads/questions/rubric.pdf");
        attachment.setMediaType("application/pdf");
        attachment.setSortOrder(1);
        entityManager.persist(attachment);
        entityManager.flush();
        Long attachmentId = attachment.getId();

        entityManager.clear();

        QuestionBank reloadedQuestionBank = questionBankRepository.findById(savedQuestionBank.getId()).orElseThrow();
        QuestionAttachment reloadedAttachment = entityManager.find(QuestionAttachment.class, attachmentId);

        assertThat(reloadedQuestionBank.getScoringConfigJson()).isEqualTo("{\"mode\":\"RECOMMEND_ONLY\"}");
        assertThat(reloadedAttachment).isNotNull();
        assertThat(reloadedAttachment.getOwnerType()).isEqualTo("QUESTION_BANK");
        assertThat(reloadedAttachment.getOwnerId()).isEqualTo(savedQuestionBank.getId());
        assertThat(reloadedAttachment.getFileName()).isEqualTo("rubric.pdf");
        assertThat(reloadedAttachment.getFilePath()).isEqualTo("/uploads/questions/rubric.pdf");
    }

    /**
     * 考试进入最终态（GRADED）时立即写成绩记录，且同一
     * {@code (business_type, business_id, student_id)} 只有一行；
     * 教师重新评分或重复发布时只更新分数，不新增记录。
     *
     * <p>SUBMITTED / AUTO_SUBMITTED 阶段 ExamService 不调用本写入器，
     * 对应断言在 {@code ExamServiceTests} 中覆盖（成绩表保持为空）。</p>
     */
    @Test
    void examScoreRecordIsWrittenOnGradingAndUpdatedInPlaceOnRepublish() {
        OffsetDateTime gradedAt = OffsetDateTime.now();
        scoreRecordWriter.write(BusinessType.EXAM, 8001L, 2L, 1L, 12D, gradedAt);
        entityManager.clear();

        assertThat(examScoreRecordCount(8001L, 2L)).isEqualTo(1);

        scoreRecordWriter.write(BusinessType.EXAM, 8001L, 2L, 1L, 15D, gradedAt.plusMinutes(1));
        entityManager.clear();

        assertThat(examScoreRecordCount(8001L, 2L)).isEqualTo(1);
        ScoreRecord record = scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentId(BusinessType.EXAM, 8001L, 2L)
                .orElseThrow();
        assertThat(record.getScore()).isEqualTo(15D);
        assertThat(record.getClassId()).isEqualTo(1L);
    }

    private Integer examScoreRecordCount(Long examId, Long studentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class,
                examId,
                studentId
        );
    }

}
