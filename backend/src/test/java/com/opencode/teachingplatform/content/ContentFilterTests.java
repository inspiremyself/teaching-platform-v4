package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 五类筛选精确匹配、不展开树；结果集先经 D21 过滤；教材只作筛选目标。 */
class ContentFilterTests extends ContentIntegrationTestSupport {

    @Test
    void byTextbookDoesNotExpandToOtherTextbooksOfSameSubject() {
        long subjectId = platformSubjectId();
        long textbookA = insertTextbook(900301L, subjectId, "TB-H");
        long textbookB = insertTextbook(900302L, subjectId, "TB-I");
        long cwA = insertCourseware(900303L, "PLATFORM", null, subjectId, null, 999L);
        long cwB = insertCourseware(900304L, "PLATFORM", null, subjectId, null, 999L);
        insertGrant(900305L, "COURSEWARE", cwA, SCHOOL_ID, 999L);
        insertGrant(900306L, "COURSEWARE", cwB, SCHOOL_ID, 999L);
        insertMount(900307L, "COURSEWARE", cwA, "TEXTBOOK", textbookA);
        insertMount(900308L, "COURSEWARE", cwB, "TEXTBOOK", textbookB);

        List<Long> ids = contentFilterService.filter(teacher(),
                        new ContentFilterRequest("TEXTBOOK", textbookA, null, null, null))
                .stream().map(ref -> ref.contentId()).toList();
        assertThat(ids).contains(cwA).doesNotContain(cwB);
    }

    @Test
    void bySectionDoesNotExpandSubtree() {
        long subjectId = platformSubjectId();
        long textbookId = insertTextbook(900311L, subjectId, "TB-J");
        long parent = insertTextbookSection(900312L, textbookId, null, 1);
        long child = insertTextbookSection(900313L, textbookId, parent, 1);
        long cwParent = insertCourseware(900314L, "PLATFORM", null, subjectId, null, 999L);
        long cwChild = insertCourseware(900315L, "PLATFORM", null, subjectId, null, 999L);
        insertGrant(900316L, "COURSEWARE", cwParent, SCHOOL_ID, 999L);
        insertGrant(900317L, "COURSEWARE", cwChild, SCHOOL_ID, 999L);
        insertMount(900318L, "COURSEWARE", cwParent, "TEXTBOOK_SECTION", parent);
        insertMount(900319L, "COURSEWARE", cwChild, "TEXTBOOK_SECTION", child);

        assertThat(contentFilterService.filter(teacher(),
                        new ContentFilterRequest("TEXTBOOK_SECTION", null, parent, null, null))
                .stream().map(ref -> ref.contentId()).toList())
                .contains(cwParent).doesNotContain(cwChild);
    }

    @Test
    void unmountedOnlyReturnsRowsWithoutMounts() {
        long subjectId = platformSubjectId();
        long textbookId = insertTextbook(900321L, subjectId, "TB-K");
        long cwMounted = insertCourseware(900322L, "PLATFORM", null, subjectId, null, 999L);
        long cwFree = insertCourseware(900323L, "PLATFORM", null, subjectId, null, 999L);
        insertGrant(900324L, "COURSEWARE", cwMounted, SCHOOL_ID, 999L);
        insertGrant(900325L, "COURSEWARE", cwFree, SCHOOL_ID, 999L);
        insertMount(900326L, "COURSEWARE", cwMounted, "TEXTBOOK", textbookId);

        List<Long> unmounted = contentFilterService.filter(teacher(),
                        new ContentFilterRequest("UNMOUNTED", null, null, null, null))
                .stream().map(ref -> ref.contentId()).toList();
        assertThat(unmounted).contains(cwFree).doesNotContain(cwMounted);
        assertThat(contentFilterService.filter(teacher(), allFilter())
                .stream().map(ref -> ref.contentId()).toList())
                .contains(cwMounted, cwFree);
    }

    @Test
    void resultSetIsFilteredByD21BeforeReturning() {
        long subjectId = platformSubjectId();
        long cwAuthorized = insertCourseware(900331L, "PLATFORM", null, subjectId, null, 999L);
        long cwUnauthorized = insertCourseware(900332L, "PLATFORM", null, subjectId, null, 999L);
        insertGrant(900333L, "COURSEWARE", cwAuthorized, SCHOOL_ID, 999L);

        List<Long> ids = contentFilterService.filter(teacher(), allFilter())
                .stream().map(ref -> ref.contentId()).toList();
        assertThat(ids).contains(cwAuthorized).doesNotContain(cwUnauthorized);
    }

    @Test
    void subjectFilterUsesOwnershipColumnsAndTextbooksAreOnlyTargets() {
        long subjectId = platformSubjectId();
        long textbookId = insertTextbook(900341L, subjectId, "TB-L");
        long cw = insertCourseware(900342L, "PLATFORM", null, subjectId, null, 999L);
        insertGrant(900343L, "COURSEWARE", cw, SCHOOL_ID, 999L);
        insertMount(900344L, "COURSEWARE", cw, "TEXTBOOK", textbookId);

        assertThat(contentFilterService.filter(teacher(),
                        new ContentFilterRequest("ALL", null, null, null, subjectId))
                .stream().map(ref -> ref.contentId()).toList())
                .contains(cw);
        assertThat(contentFilterService.filter(teacher(), allFilter()))
                .allSatisfy(ref -> assertThat(ref.contentType()).isIn("COURSEWARE", "QUESTION"));
    }
}
