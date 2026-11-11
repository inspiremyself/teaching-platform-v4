package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSubjectScopeTests extends ContentIntegrationTestSupport {

    @Test
    void platformContentRequiresGlobalSubject() {
        long subjectId = platformSubjectId();
        assertThatCode(() -> contentSubjectScopeService.validate(
                platformAdmin(), "PLATFORM", subjectId, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> contentSubjectScopeService.validate(
                platformAdmin(), "PLATFORM", null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(40025));
    }

    @Test
    void schoolContentAcceptsLocalSubject() {
        long subjectId = activeSchoolSubjectId();
        assertThatCode(() -> contentSubjectScopeService.validate(
                teacher(), "SCHOOL", null, subjectId)).doesNotThrowAnyException();
    }

    @Test
    void subjectColumnsMutuallyExclusive() {
        long globalId = platformSubjectId();
        long localId = activeSchoolSubjectId();
        assertThatThrownBy(() -> contentSubjectScopeService.validate(
                platformAdmin(), "PLATFORM", globalId, localId))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> contentSubjectScopeService.validate(
                teacher(), "SCHOOL", globalId, localId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void subjectFilterScopedByD21() {
        long coursewareId = insertCourseware(900101L, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertGrant(900111L, "COURSEWARE", coursewareId, SCHOOL_ID, 999L);

        // 教师端先经 D21 过滤：已授权 PLATFORM 课件按分域归属列精确命中
        assertThat(contentFilterService.filter(teacher(),
                new com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest(
                        "ALL", null, null, null, platformSubjectId())))
                .extracting(ref -> ref.contentId())
                .contains(coursewareId);
        // 学科按分域归属列精确匹配：不匹配的学科不返回该内容（不按挂载目标反推）
        assertThat(contentFilterService.filter(teacher(),
                new com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest(
                        "ALL", null, null, null, 999999L)))
                .extracting(ref -> ref.contentId())
                .doesNotContain(coursewareId);
    }
}
