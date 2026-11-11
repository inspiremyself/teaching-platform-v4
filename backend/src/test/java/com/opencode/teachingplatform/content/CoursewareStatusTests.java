package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateCoursewareRequest;
import com.opencode.teachingplatform.content.service.ContentShareGrantService;
import com.opencode.teachingplatform.content.service.PlatformContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态机与生命周期：发布写发布时间、下架置回 DRAFT、已发布禁止编辑、有布置引用禁删。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CoursewareStatusTests extends ContentIntegrationTestSupport {

    private static final String VALID_CONTENT = "{\"version\":1,\"paragraphs\":[{\"class\":\"text\","
            + "\"sentences\":[{\"text\":\"正文\",\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],"
            + "\"exercises\":[]}]}";

    @Autowired
    private PlatformContentService platformContentService;

    @Autowired
    private ContentShareGrantService contentShareGrantService;

    @Test
    void publishWritesTimestampAndUnpublishClearsIt() {
        long coursewareId = draftCourseware(900201);

        platformContentService.publish(coursewareId);
        assertThat(statusOf(coursewareId)).isEqualTo("PUBLISHED");
        assertThat(publishedAtOf(coursewareId)).isNotNull();

        platformContentService.unpublish(coursewareId);
        assertThat(statusOf(coursewareId)).isEqualTo("DRAFT");
        assertThat(publishedAtOf(coursewareId)).isNull();
    }

    @Test
    void republishOverwritesTimestamp() {
        long coursewareId = draftCourseware(900211);

        platformContentService.publish(coursewareId);
        Object first = publishedAtOf(coursewareId);
        platformContentService.unpublish(coursewareId);
        platformContentService.publish(coursewareId);

        assertThat(publishedAtOf(coursewareId)).isNotNull();
        assertThat(publishedAtOf(coursewareId)).isNotEqualTo(first);
    }

    @Test
    void publishedCoursewareCannotBeEdited() {
        long coursewareId = draftCourseware(900221);
        platformContentService.publish(coursewareId);

        UpdateCoursewareRequest request = new UpdateCoursewareRequest("改标题", null, platformSubjectId(),
                List.of(new com.opencode.teachingplatform.content.dto.ContentRequests.SectionPayload(
                        null, "小节", 1, VALID_CONTENT)), null);

        assertThatThrownBy(() -> platformContentService.update(platformAdmin(), coursewareId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先下架再编辑");
    }

    @Test
    void deleteRejectedWhenCoursewareAssigned() {
        long coursewareId = draftCourseware(900231);
        insertAssignment(900232, 1L, 1L, "CLASS", 1L, coursewareId, null, "grp-1", 1);

        assertThatThrownBy(() -> platformContentService.delete(platformAdmin(), coursewareId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("课件已被布置，只能下架");

        assertThat(countRows("courseware")).isEqualTo(1);
    }

    @Test
    void deleteRemovesGrantsAndChildRows() {
        long coursewareId = draftCourseware(900241);
        insertGrant(900242, "COURSEWARE", coursewareId, SCHOOL_ID, 999L);
        insertMount(900243, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", 1L);

        platformContentService.delete(platformAdmin(), coursewareId);

        assertThat(countRows("courseware")).isZero();
        assertThat(countRows("content_share_grant")).isZero();
        assertThat(countMounts("COURSEWARE", coursewareId)).isZero();
    }

    private long draftCourseware(long id) {
        long textbookId = insertTextbook(id + 2, platformSubjectId(), "ST-" + id);
        long leafId = insertTextbookSection(id + 3, textbookId, null, 1);
        long coursewareId = insertCourseware(id, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(id + 1, coursewareId, 1, VALID_CONTENT);
        insertMount(id + 4, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", leafId);
        return coursewareId;
    }

    private String statusOf(long coursewareId) {
        return jdbcTemplate.queryForObject("SELECT status FROM courseware WHERE id = ?", String.class, coursewareId);
    }

    private Object publishedAtOf(long coursewareId) {
        return jdbcTemplate.queryForObject("SELECT published_at FROM courseware WHERE id = ?", Object.class, coursewareId);
    }
}
