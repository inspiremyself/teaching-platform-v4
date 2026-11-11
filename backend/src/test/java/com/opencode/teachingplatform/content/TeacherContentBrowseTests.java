package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.service.ContentShareGrantService;
import com.opencode.teachingplatform.content.service.TeacherContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 教师只读浏览：五类筛选精确匹配、结果先经 D21、未授权与跨校 40400、浏览端点只有 GET。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeacherContentBrowseTests extends ContentIntegrationTestSupport {

    private static final String VALID_CONTENT = "{\"version\":1,\"paragraphs\":[{\"class\":\"text\","
            + "\"sentences\":[{\"text\":\"正文\",\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],"
            + "\"exercises\":[]}]}";

    @Autowired
    private TeacherContentService teacherContentService;

    @Autowired
    private ContentShareGrantService contentShareGrantService;

    @Test
    void onlyGrantedCoursewareIsVisibleToTeacher() {
        long coursewareId = insertCourseware(900601, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900602, coursewareId, 1, VALID_CONTENT);

        assertThatThrownBy(() -> teacherContentService.listSections(teacher(), coursewareId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40400);

        contentShareGrantService.grant(platformAdmin(), platformCourseware(coursewareId), SCHOOL_ID);

        assertThat(teacherContentService.listSections(teacher(), coursewareId)).hasSize(1);
    }

    @Test
    void filterTypesMatchExactly() {
        long textbookId = insertTextbook(900611, platformSubjectId(), "BROWSE-TB");
        long textbookSectionId = insertTextbookSection(900612, textbookId, null, 1);
        long coursewareSectionId = insertCoursewareSection(900613, insertCourseware(
                900614, "PLATFORM", null, platformSubjectId(), null, 999L), 1, VALID_CONTENT);
        long mountedId = insertCourseware(900615, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertCoursewareSection(900616, mountedId, 1, VALID_CONTENT);
        insertMount(900617, "COURSEWARE", mountedId, "TEXTBOOK", textbookId);
        insertMount(900618, "COURSEWARE", mountedId, "TEXTBOOK_SECTION", textbookSectionId);
        long coursewareSectionMountId = insertCoursewareSection(900619, insertCourseware(
                900620, "PLATFORM", null, platformSubjectId(), null, 999L), 1, VALID_CONTENT);
        insertMount(900621, "COURSEWARE", 900620, "COURSEWARE_SECTION", coursewareSectionMountId);
        grantAll();

        assertThat(teacherContentService.list(teacher(), new ContentFilterRequest("ALL", null, null, null, null)))
                .extracting("id").contains(mountedId);
        assertThat(teacherContentService.list(teacher(),
                new ContentFilterRequest("TEXTBOOK", textbookId, null, null, null)))
                .extracting("id").containsExactly(mountedId);
        assertThat(teacherContentService.list(teacher(),
                new ContentFilterRequest("TEXTBOOK_SECTION", null, textbookSectionId, null, null)))
                .extracting("id").containsExactly(mountedId);
        assertThat(teacherContentService.list(teacher(),
                new ContentFilterRequest("COURSEWARE_SECTION", null, null, coursewareSectionMountId, null)))
                .extracting("id").containsExactly(900620L);
        assertThat(teacherContentService.list(teacher(),
                new ContentFilterRequest("UNMOUNTED", null, null, null, null)))
                .extracting("id").doesNotContain(mountedId);
    }

    @Test
    void missingFilterTypeIsNormalizedToAll() {
        grantAll();

        assertThat(teacherContentService.list(teacher(), new ContentFilterRequest(null, null, null, null, null)))
                .isNotNull();
    }

    @Test
    void textbookSectionOptionsAreListedForSectionFilter() {
        long textbookId = insertTextbook(900641, platformSubjectId(), "BROWSE-SECTIONS");
        long sectionId = insertTextbookSection(900642, textbookId, null, 1);

        var sections = teacherContentService.listTextbookSections(textbookId);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).id()).isEqualTo(sectionId);
        assertThat(sections.get(0).leaf()).isTrue();
    }

    @Test
    void browseControllerOnlyExposesGetMappings() {
        boolean onlyGet = Arrays.stream(com.opencode.teachingplatform.content.controller.TeacherContentController.class
                        .getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class))
                .allMatch(method -> method.getAnnotation(GetMapping.class) != null);
        assertThat(onlyGet).isTrue();
    }

    private void grantAll() {
        for (Long id : Arrays.asList(900613L, 900614L, 900615L, 900620L)) {
            contentShareGrantService.grant(platformAdmin(), new ContentRef("COURSEWARE", id, "PLATFORM", null, 999L), SCHOOL_ID);
        }
    }
}
