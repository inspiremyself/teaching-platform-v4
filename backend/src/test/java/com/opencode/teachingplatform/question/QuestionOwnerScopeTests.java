package com.opencode.teachingplatform.question;

import com.opencode.teachingplatform.content.ContentIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V31 换键：新键是旧键超集，跨教师同码并存不变、同归属同码被拒、source_ref 按校唯一不变。 */
class QuestionOwnerScopeTests extends ContentIntegrationTestSupport {

    @Test
    void crossTeacherSameCodeStillCoexists() {
        long subjectId = activeSchoolSubjectId();
        insertQuestion(900801L, "SCOPE-A", 1L, "REF-A-1", subjectId);
        insertQuestion(900802L, "SCOPE-A", 5L, "REF-A-2", subjectId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE code = 'SCOPE-A'", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void sameOwnerSameCodeIsRejected() {
        long subjectId = activeSchoolSubjectId();
        insertQuestion(900811L, "SCOPE-B", 1L, "REF-B-1", subjectId);
        assertThatThrownBy(() -> insertQuestion(900812L, "SCOPE-B", 1L, "REF-B-2", subjectId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existingRowsKeepTeacherOwnerScopeAndZeroRewrite() {
        Long teacherScoped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE owner_scope = 'TEACHER'", Long.class);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_bank", Long.class);
        assertThat(teacherScoped).isNotNull();
        assertThat(total).isNotNull();
        assertThat(teacherScoped).isEqualTo(total);
    }

    @Test
    void sourceRefRemainsUniquePerSchool() {
        long subjectId = activeSchoolSubjectId();
        insertQuestion(900821L, "SCOPE-C", 1L, "REF-C-DUP", subjectId);
        assertThatThrownBy(() -> insertQuestion(900822L, "SCOPE-D", 5L, "REF-C-DUP", subjectId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertQuestion(long id, String code, long createdBy, String sourceRef, long subjectId) {
        jdbcTemplate.update("INSERT INTO question_bank "
                        + "(id, code, type, stem, difficulty, answer_json, created_by, school_id, owner_scope, "
                        + "subject_id, source_ref, source_type, created_at, updated_at) "
                        + "VALUES (?, ?, 'SINGLE', '题干', 'EASY', '[\\\"A\\\"]', ?, 1, 'TEACHER', ?, ?, 'MANUAL', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, code, createdBy, subjectId, sourceRef);
    }
}
