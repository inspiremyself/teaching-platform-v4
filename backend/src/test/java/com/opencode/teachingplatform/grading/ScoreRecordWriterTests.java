package com.opencode.teachingplatform.grading;

import com.opencode.teachingplatform.analysis.entity.ScoreRecord;
import com.opencode.teachingplatform.analysis.repository.ScoreRecordRepository;
import com.opencode.teachingplatform.common.enums.BusinessType;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.grading.service.ScoreRecordWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 成绩记录统一写入口测试：覆盖先查后写、幂等更新、默认评分时间与并发冲突兜底。
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(ScoreRecordWriter.class)
class ScoreRecordWriterTests {

    @Autowired
    private ScoreRecordWriter scoreRecordWriter;

    @Autowired
    private ScoreRecordRepository scoreRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void writeCreatesScoreRecordWhenKeyAbsent() {
        OffsetDateTime gradedAt = OffsetDateTime.parse("2026-11-06T09:30:00+08:00");

        ScoreRecord saved = scoreRecordWriter.write(BusinessType.HOMEWORK, 101L, 2L, 1L, 88D, gradedAt);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBusinessType()).isEqualTo(BusinessType.HOMEWORK);
        assertThat(saved.getBusinessId()).isEqualTo(101L);
        assertThat(saved.getStudentId()).isEqualTo(2L);
        assertThat(saved.getClassId()).isEqualTo(1L);
        assertThat(saved.getScore()).isEqualTo(88D);
        assertThat(saved.getGradedAt()).isEqualTo(gradedAt);

        Optional<ScoreRecord> reloaded = scoreRecordRepository
                .findByBusinessTypeAndBusinessIdAndStudentId(BusinessType.HOMEWORK, 101L, 2L);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.orElseThrow().getId()).isEqualTo(saved.getId());
    }

    @Test
    void writeTwiceWithSameKeyUpdatesExistingRowWithoutInsertingAnother() {
        OffsetDateTime firstAt = OffsetDateTime.parse("2026-11-06T09:30:00+08:00");
        OffsetDateTime secondAt = OffsetDateTime.parse("2026-11-06T10:00:00+08:00");

        ScoreRecord first = scoreRecordWriter.write(BusinessType.EXAM, 102L, 2L, 1L, 80D, firstAt);
        ScoreRecord second = scoreRecordWriter.write(BusinessType.EXAM, 102L, 2L, 1L, 95D, secondAt);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM score_record WHERE business_type = 'EXAM' AND business_id = ? AND student_id = ?",
                Integer.class, 102L, 2L);

        assertThat(rowCount).isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getScore()).isEqualTo(95D);
        assertThat(second.getGradedAt()).isEqualTo(secondAt);
    }

    @Test
    void writeFallsBackToCurrentTimeWhenGradedAtIsNull() {
        OffsetDateTime before = OffsetDateTime.now().minusMinutes(1);

        ScoreRecord saved = scoreRecordWriter.write(BusinessType.LAB, 103L, 2L, 1L, 76D, null);

        assertThat(saved.getGradedAt()).isNotNull();
        assertThat(saved.getGradedAt()).isAfter(before);
    }

    @Test
    void uniqueConstraintViolationIsTranslatedToBusinessConflict() {
        // 并发场景下先查未命中、写入时唯一约束冲突，必须转 40900 而不是 500。
        ScoreRecordRepository mockedRepository = mock(ScoreRecordRepository.class);
        when(mockedRepository.findByBusinessTypeAndBusinessIdAndStudentId(BusinessType.HOMEWORK, 104L, 2L))
                .thenReturn(Optional.empty());
        when(mockedRepository.saveAndFlush(any(ScoreRecord.class)))
                .thenThrow(new DataIntegrityViolationException("uk_score_record_business_student"));
        ScoreRecordWriter unitWriter = new ScoreRecordWriter(mockedRepository);

        assertThatThrownBy(() -> unitWriter.write(BusinessType.HOMEWORK, 104L, 2L, 1L, 66D, OffsetDateTime.now()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(40900);
                    assertThat(ex.getMessage()).isEqualTo("成绩记录并发冲突，请重试");
                });
    }
}
