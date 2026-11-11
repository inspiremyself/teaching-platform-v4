package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.SectionPayload;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdateCoursewareRequest;
import com.opencode.teachingplatform.content.service.PlatformContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单节正文容量：1,048,576 字节恰好放行、+1 字节拒绝，超限不落库。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CoursewareContentSizeTests extends ContentIntegrationTestSupport {

    private static final int LIMIT = 1_048_576;

    @Autowired
    private PlatformContentService platformContentService;

    @Test
    void exactlyOneMegabyteIsAccepted() {
        long coursewareId = insertCourseware(900301, "PLATFORM", null, platformSubjectId(), null, 999L);
        String content = contentOfBytes(LIMIT);
        assertThat(content.getBytes(StandardCharsets.UTF_8).length).isEqualTo(LIMIT);

        platformContentService.update(platformAdmin(), coursewareId, request(content));

        assertThat(sectionCount(coursewareId)).isEqualTo(1);
    }

    @Test
    void oneByteOverLimitIsRejectedWithoutRowChange() {
        long coursewareId = insertCourseware(900311, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900312, coursewareId, 1, smallContent());
        String content = contentOfBytes(LIMIT + 1);

        assertThatThrownBy(() -> platformContentService.update(platformAdmin(), coursewareId, request(content)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单节正文超过 1MB 上限");

        assertThat(sectionCount(coursewareId)).isEqualTo(1);
    }

    private UpdateCoursewareRequest request(String content) {
        return new UpdateCoursewareRequest("课件", null, platformSubjectId(),
                List.of(new SectionPayload(null, "小节", 1, content)), null);
    }

    private int sectionCount(long coursewareId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courseware_section WHERE courseware_id = ?", Integer.class, coursewareId);
        return count == null ? 0 : count;
    }

    private String smallContent() {
        return "{\"version\":1,\"paragraphs\":[{\"class\":\"text\",\"sentences\":[{\"text\":\"正文\","
                + "\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],\"exercises\":[]}]}";
    }

    private String contentOfBytes(int totalBytes) {
        String head = "{\"version\":1,\"paragraphs\":[{\"class\":\"text\",\"sentences\":[{\"text\":\"";
        String tail = "\",\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],\"exercises\":[]}]}";
        int filler = totalBytes - head.getBytes(StandardCharsets.UTF_8).length
                - tail.getBytes(StandardCharsets.UTF_8).length;
        return head + "a".repeat(filler) + tail;
    }
}
