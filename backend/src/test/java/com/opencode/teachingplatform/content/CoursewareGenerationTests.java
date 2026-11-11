package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.GenerateCoursewareRequest;
import com.opencode.teachingplatform.content.service.CoursewareAiGateway;
import com.opencode.teachingplatform.content.service.CoursewareGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AI 生成：显式触发只落草稿、重新生成保持小节 id 稳定、解析失败与未配置都不落库。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CoursewareGenerationTests extends ContentIntegrationTestSupport {

    private static final String AI_RESPONSE = "{\"sections\":[{\"heading\":\"第一节\",\"content\":"
            + "{\"version\":1,\"paragraphs\":[{\"class\":\"text\",\"sentences\":[{\"text\":\"生成的句子\","
            + "\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],\"exercises\":[]}]}}]}";

    @Autowired
    private CoursewareGenerationService coursewareGenerationService;

    @MockBean
    private CoursewareAiGateway coursewareAiGateway;

    @Test
    void firstGenerationCreatesDraftOnly() {
        when(coursewareAiGateway.isConfigured()).thenReturn(true);
        when(coursewareAiGateway.complete(anyString(), anyString())).thenReturn(AI_RESPONSE);
        long textbookId = insertTextbook(900401, platformSubjectId(), "GEN-NEW");
        long textbookSectionId = insertTextbookSection(900402, textbookId, null, 1);

        var detail = coursewareGenerationService.generate(platformAdmin(),
                new GenerateCoursewareRequest(null, platformSubjectId(), textbookSectionId, "生成课件", null));

        assertThat(detail.courseware().status()).isEqualTo("DRAFT");
        assertThat(detail.sections()).hasSize(1);
        assertThat(detail.courseware().publishedAt()).isNull();
        assertThat(countRows("content_share_grant")).isZero();
        assertThat(countRows("content_mount")).isZero();
        assertThat(countRows("courseware")).isEqualTo(1);
    }

    @Test
    void regenerationKeepsSectionIdsAndDoesNotCreateNewCourseware() {
        when(coursewareAiGateway.isConfigured()).thenReturn(true);
        when(coursewareAiGateway.complete(anyString(), anyString())).thenReturn(AI_RESPONSE);
        long textbookId = insertTextbook(900411, platformSubjectId(), "GEN-AGAIN");
        long textbookSectionId = insertTextbookSection(900412, textbookId, null, 1);
        long coursewareId = insertCourseware(900413, "PLATFORM", null, platformSubjectId(), null, 999L);
        long sectionId = insertCoursewareSection(900414, coursewareId, 1, smallContent());

        var detail = coursewareGenerationService.generate(platformAdmin(),
                new GenerateCoursewareRequest(coursewareId, platformSubjectId(), textbookSectionId, "重生成", null));

        assertThat(detail.courseware().id()).isEqualTo(coursewareId);
        assertThat(detail.courseware().status()).isEqualTo("DRAFT");
        assertThat(detail.sections()).hasSize(1);
        assertThat(detail.sections().get(0).id()).isEqualTo(sectionId);
        assertThat(detail.sections().get(0).contentJson()).contains("生成的句子");
        assertThat(countRows("courseware")).isEqualTo(1);
        assertThat(countRows("courseware_section")).isEqualTo(1);
    }

    @Test
    void invalidModelOutputFailsWithoutWritingRows() {
        when(coursewareAiGateway.isConfigured()).thenReturn(true);
        when(coursewareAiGateway.complete(anyString(), anyString())).thenReturn("not-json");
        long textbookId = insertTextbook(900421, platformSubjectId(), "GEN-BAD");
        long textbookSectionId = insertTextbookSection(900422, textbookId, null, 1);

        assertThatThrownBy(() -> coursewareGenerationService.generate(platformAdmin(),
                new GenerateCoursewareRequest(null, platformSubjectId(), textbookSectionId, "失败课件", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 生成结果解析失败，未写入任何数据");

        assertThat(countRows("courseware")).isZero();
        assertThat(countRows("courseware_section")).isZero();
    }

    @Test
    void notConfiguredGatewayFailsWithoutWritingRows() {
        when(coursewareAiGateway.isConfigured()).thenReturn(false);
        when(coursewareAiGateway.complete(anyString(), anyString()))
                .thenThrow(new BusinessException(40000, "AI 生成服务未配置，请联系管理员"));
        long textbookId = insertTextbook(900431, platformSubjectId(), "GEN-NOCFG");
        long textbookSectionId = insertTextbookSection(900432, textbookId, null, 1);

        assertThatThrownBy(() -> coursewareGenerationService.generate(platformAdmin(),
                new GenerateCoursewareRequest(null, platformSubjectId(), textbookSectionId, "未配置", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 生成服务未配置，请联系管理员");

        assertThat(countRows("courseware")).isZero();
    }

    private String smallContent() {
        return "{\"version\":1,\"paragraphs\":[{\"class\":\"text\",\"sentences\":[{\"text\":\"旧正文\","
                + "\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],\"exercises\":[]}]}";
    }
}
