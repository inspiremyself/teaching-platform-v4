package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 新建必填、编辑可省略（省略保持原值）；create 与 update 共用同一套有效性校验。 */
class TaskSubjectValidationTests extends ContentIntegrationTestSupport {

    @Test
    void createRequiresSubjectId() {
        assertThatThrownBy(() -> taskSubjectValidator.requireForCreate(teacher(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40025));
    }

    @Test
    void updateMayOmitSubjectId() {
        assertThat(taskSubjectValidator.requireForUpdate(teacher(), null)).isNull();
    }

    @Test
    void createAndUpdateShareSameValidation() {
        long subjectId = activeSchoolSubjectId();
        assertThatCode(() -> taskSubjectValidator.requireForCreate(teacher(), subjectId))
                .doesNotThrowAnyException();
        assertThat(taskSubjectValidator.requireForUpdate(teacher(), subjectId)).isEqualTo(subjectId);

        // 非本校学科在 create 与 update 两条路径都被拒
        CurrentUser teacher = teacher();
        assertThatThrownBy(() -> taskSubjectValidator.requireForCreate(teacher, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40025));
        assertThatThrownBy(() -> taskSubjectValidator.requireForUpdate(teacher, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40025));
    }

    @Test
    void historicalRowsWithNullSubjectIdRemainReadable() {
        Long nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_experiment WHERE subject_id IS NULL", Long.class);
        assertThat(nullCount).isNotNull();
        assertThatCode(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework WHERE subject_id IS NULL", Long.class))
                .doesNotThrowAnyException();
    }
}
