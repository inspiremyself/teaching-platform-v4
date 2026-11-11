package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentShareGrantTests extends ContentIntegrationTestSupport {

    @Test
    void duplicateGrantIsRejectedWith40900() {
        long cw = insertCourseware(900401L, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertGrant(900402L, "COURSEWARE", cw, SCHOOL_ID, 999L);

        assertThatThrownBy(() -> contentShareGrantService.grant(
                platformAdmin(), platformCourseware(cw), SCHOOL_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40900));
    }

    @Test
    void revokeDeletesOnlySingleRowAndDeleteContentClearsAllGrants() {
        long cw = insertCourseware(900411L, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertGrant(900412L, "COURSEWARE", cw, SCHOOL_ID, 999L);
        insertGrant(900413L, "COURSEWARE", cw, OTHER_SCHOOL_ID, 999L);

        contentShareGrantService.revoke(platformAdmin(), "COURSEWARE", cw, SCHOOL_ID);
        assertThat(contentShareGrantRepository.existsBySchoolIdAndContentTypeAndContentId(
                SCHOOL_ID, "COURSEWARE", cw)).isFalse();
        assertThat(contentShareGrantRepository.existsBySchoolIdAndContentTypeAndContentId(
                OTHER_SCHOOL_ID, "COURSEWARE", cw)).isTrue();

        contentShareGrantService.deleteAllForContent(platformAdmin(), "COURSEWARE", cw);
        assertThat(contentShareGrantRepository.findBySchoolIdAndContentType(OTHER_SCHOOL_ID, "COURSEWARE"))
                .isEmpty();
    }

    @Test
    void schoolOwnedContentCannotBeSharedAcrossSchools() {
        long cw = insertCourseware(900421L, "SCHOOL", SCHOOL_ID, null, activeSchoolSubjectId(), TEACHER_ID);
        assertThatThrownBy(() -> contentShareGrantService.grant(platformAdmin(),
                new com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef(
                        "COURSEWARE", cw, "SCHOOL", SCHOOL_ID, TEACHER_ID), OTHER_SCHOOL_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40400));
    }

    @Test
    void onlyPlatformAdminCanWriteGrants() {
        long cw = insertCourseware(900431L, "PLATFORM", null, platformSubjectId(), null, 999L);
        assertThatThrownBy(() -> contentShareGrantService.grant(
                teacher(), platformCourseware(cw), SCHOOL_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40300));
    }

    @Test
    void unauthorizedPlatformContentIsInvisibleWith40400() {
        long cw = insertCourseware(900441L, "PLATFORM", null, platformSubjectId(), null, 999L);
        assertThatThrownBy(() -> contentVisibilityService.assertVisible(teacher(), platformCourseware(cw)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40400));
    }
}
