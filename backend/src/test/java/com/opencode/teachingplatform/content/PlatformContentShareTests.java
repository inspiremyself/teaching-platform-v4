package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.GrantSchoolsRequest;
import com.opencode.teachingplatform.content.service.PlatformContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 按学校集合授权：仅已发布可授权、可多次增删、重复与不存在的学校分别 409 / 404、删内容同事务清授权。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformContentShareTests extends ContentIntegrationTestSupport {

    private static final String VALID_CONTENT = "{\"version\":1,\"paragraphs\":[{\"class\":\"text\","
            + "\"sentences\":[{\"text\":\"正文\",\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],"
            + "\"exercises\":[]}]}";

    @Autowired
    private PlatformContentService platformContentService;

    @Test
    void grantRequiresPublishedCourseware() {
        long coursewareId = insertCourseware(900501, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900502, coursewareId, 1, VALID_CONTENT);

        assertThatThrownBy(() -> platformContentService.grant(platformAdmin(), coursewareId,
                new GrantSchoolsRequest(List.of(SCHOOL_ID))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先发布再授权");
    }

    @Test
    void grantAndRevokeBySchool() {
        long coursewareId = publishedCourseware(900511);

        var grants = platformContentService.grant(platformAdmin(), coursewareId,
                new GrantSchoolsRequest(List.of(SCHOOL_ID)));
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).schoolId()).isEqualTo(SCHOOL_ID);

        platformContentService.revoke(platformAdmin(), coursewareId, SCHOOL_ID);
        assertThat(platformContentService.listGrants(coursewareId)).isEmpty();
    }

    @Test
    void duplicateGrantRejected() {
        long coursewareId = publishedCourseware(900521);
        platformContentService.grant(platformAdmin(), coursewareId, new GrantSchoolsRequest(List.of(SCHOOL_ID)));

        assertThatThrownBy(() -> platformContentService.grant(platformAdmin(), coursewareId,
                new GrantSchoolsRequest(List.of(SCHOOL_ID))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40900);
    }

    @Test
    void unknownSchoolRejected() {
        long coursewareId = publishedCourseware(900531);

        assertThatThrownBy(() -> platformContentService.grant(platformAdmin(), coursewareId,
                new GrantSchoolsRequest(List.of(987654L))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40400);
    }

    @Test
    void deleteCoursewareClearsGrants() {
        long coursewareId = publishedCourseware(900541);
        platformContentService.grant(platformAdmin(), coursewareId, new GrantSchoolsRequest(List.of(SCHOOL_ID)));
        assertThat(countRows("content_share_grant")).isEqualTo(1);

        platformContentService.delete(platformAdmin(), coursewareId);

        assertThat(countRows("content_share_grant")).isZero();
    }

    private long publishedCourseware(long id) {
        long textbookId = insertTextbook(id + 1, platformSubjectId(), "SHARE-" + id);
        long leafId = insertTextbookSection(id + 2, textbookId, null, 1);
        long coursewareId = insertCourseware(id + 3, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(id + 4, coursewareId, 1, VALID_CONTENT);
        insertMount(id + 5, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", leafId);
        platformContentService.publish(coursewareId);
        return coursewareId;
    }
}
