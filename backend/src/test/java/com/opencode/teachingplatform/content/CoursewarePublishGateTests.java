package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.service.PlatformContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发布四项门禁：学科归属 / 叶子挂载 / 小节树 / 产物，任一不满足都拒发布且状态不变。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CoursewarePublishGateTests extends ContentIntegrationTestSupport {

    static final String VALID_CONTENT = "{\"version\":1,\"paragraphs\":[{\"class\":\"text\","
            + "\"sentences\":[{\"text\":\"正文\",\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],"
            + "\"exercises\":[]}]}";

    @Autowired
    private PlatformContentService platformContentService;

    @Test
    void publishRejectedWhenNoLeafMount() {
        long coursewareId = insertCourseware(900101, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900102, coursewareId, 1, VALID_CONTENT);

        assertThatThrownBy(() -> platformContentService.publish(coursewareId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("发布前至少挂载一个叶子教材小节");

        assertThat(statusOf(coursewareId)).isEqualTo("DRAFT");
        assertThat(publishedAtOf(coursewareId)).isNull();
    }

    @Test
    void publishRejectedWhenMountedToNonLeafSection() {
        long textbookId = insertTextbook(900111, platformSubjectId(), "PUB-NONLEAF");
        long parentId = insertTextbookSection(900112, textbookId, null, 1);
        long childId = insertTextbookSection(900113, textbookId, parentId, 2);
        long coursewareId = insertCourseware(900114, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900115, coursewareId, 1, VALID_CONTENT);
        insertMount(900116, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", parentId);

        assertThatThrownBy(() -> platformContentService.publish(coursewareId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("发布前至少挂载一个叶子教材小节");

        assertThat(statusOf(coursewareId)).isEqualTo("DRAFT");
    }

    @Test
    void publishRejectedWhenGlobalSubjectDisabled() {
        long coursewareId = insertCourseware(900121, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900122, coursewareId, 1, VALID_CONTENT);
        long textbookId = insertTextbook(900123, platformSubjectId(), "PUB-DISABLED");
        long leafId = insertTextbookSection(900124, textbookId, null, 1);
        insertMount(900125, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", leafId);
        jdbcTemplate.update("UPDATE subject SET status = 'DISABLED' WHERE id = ?", platformSubjectId());

        assertThatThrownBy(() -> platformContentService.publish(coursewareId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40025);

        assertThat(statusOf(coursewareId)).isEqualTo("DRAFT");
    }

    @Test
    void publishRejectedWhenArtifactIncomplete() {
        long textbookId = insertTextbook(900131, platformSubjectId(), "PUB-ARTIFACT");
        long leafId = insertTextbookSection(900132, textbookId, null, 1);
        long coursewareId = insertCourseware(900133, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900134, coursewareId, 1, "{}");
        insertMount(900135, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", leafId);

        assertThatThrownBy(() -> platformContentService.publish(coursewareId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("小节正文协议版本必须为 1");

        assertThat(statusOf(coursewareId)).isEqualTo("DRAFT");
    }

    @Test
    void publishSucceedsWithLeafMountAndValidArtifact() {
        long textbookId = insertTextbook(900141, platformSubjectId(), "PUB-OK");
        long leafId = insertTextbookSection(900142, textbookId, null, 1);
        long coursewareId = insertCourseware(900143, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900144, coursewareId, 1, VALID_CONTENT);
        insertMount(900145, "COURSEWARE", coursewareId, "TEXTBOOK_SECTION", leafId);

        platformContentService.publish(coursewareId);

        assertThat(statusOf(coursewareId)).isEqualTo("PUBLISHED");
        assertThat(publishedAtOf(coursewareId)).isNotNull();
    }

    private String statusOf(long coursewareId) {
        return jdbcTemplate.queryForObject("SELECT status FROM courseware WHERE id = ?", String.class, coursewareId);
    }

    private Object publishedAtOf(long coursewareId) {
        return jdbcTemplate.queryForObject("SELECT published_at FROM courseware WHERE id = ?", Object.class, coursewareId);
    }
}
