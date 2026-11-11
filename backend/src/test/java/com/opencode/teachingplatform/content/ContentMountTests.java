package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.dto.ContentRequests.MountRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentMountTests extends ContentIntegrationTestSupport {

    @Test
    void zeroMountRowsAreLegalAndMultipleTargetsAreAllowed() {
        long textbookA = insertTextbook(900201L, platformSubjectId(), "TB-A");
        long textbookB = insertTextbook(900202L, platformSubjectId(), "TB-B");
        long sectionA = insertTextbookSection(900203L, textbookA, null, 1);
        long coursewareId = insertCourseware(900204L, "PLATFORM", null, platformSubjectId(), null, 999L);
        ContentRef ref = platformCourseware(coursewareId);

        assertThatCode(() -> contentMountService.replaceMounts(platformAdmin(), ref, List.of()))
                .doesNotThrowAnyException();
        assertThat(countMounts("COURSEWARE", coursewareId)).isZero();

        contentMountService.replaceMounts(platformAdmin(), ref, List.of(
                new MountRequest("TEXTBOOK", textbookA),
                new MountRequest("TEXTBOOK", textbookB),
                new MountRequest("TEXTBOOK_SECTION", sectionA)));
        assertThat(countMounts("COURSEWARE", coursewareId)).isEqualTo(3);
    }

    @Test
    void omittingMountsDoesNotChangeRows() {
        long textbookId = insertTextbook(900206L, platformSubjectId(), "TB-A2");
        long coursewareId = insertCourseware(900207L, "PLATFORM", null, platformSubjectId(), null, 999L);
        ContentRef ref = platformCourseware(coursewareId);
        contentMountService.replaceMounts(platformAdmin(), ref, List.of(new MountRequest("TEXTBOOK", textbookId)));
        assertThat(countMounts("COURSEWARE", coursewareId)).isEqualTo(1);

        contentMountService.replaceMounts(platformAdmin(), ref, null);
        assertThat(countMounts("COURSEWARE", coursewareId)).isEqualTo(1);
        assertThat(contentMountRepository.findByContentTypeAndContentId("COURSEWARE", coursewareId))
                .singleElement()
                .satisfies(row -> assertThat(row.getMountTargetId()).isEqualTo(textbookId));
    }

    @Test
    void fullReplacementClearsPreviousRows() {
        long textbookA = insertTextbook(900211L, platformSubjectId(), "TB-C");
        long textbookB = insertTextbook(900212L, platformSubjectId(), "TB-D");
        long coursewareId = insertCourseware(900213L, "PLATFORM", null, platformSubjectId(), null, 999L);
        ContentRef ref = platformCourseware(coursewareId);

        contentMountService.replaceMounts(platformAdmin(), ref, List.of(new MountRequest("TEXTBOOK", textbookA)));
        assertThat(countMounts("COURSEWARE", coursewareId)).isEqualTo(1);

        contentMountService.replaceMounts(platformAdmin(), ref, List.of(new MountRequest("TEXTBOOK", textbookB)));
        assertThat(countMounts("COURSEWARE", coursewareId)).isEqualTo(1);
        assertThat(contentMountRepository.findByContentTypeAndContentId("COURSEWARE", coursewareId))
                .singleElement()
                .satisfies(row -> assertThat(row.getMountTargetId()).isEqualTo(textbookB));
    }

    @Test
    void deletingTargetRemovesMountRowsButKeepsContent() {
        long textbookId = insertTextbook(900221L, platformSubjectId(), "TB-E");
        long coursewareId = insertCourseware(900222L, "PLATFORM", null, platformSubjectId(), null, 999L);
        contentMountService.replaceMounts(platformAdmin(), platformCourseware(coursewareId),
                List.of(new MountRequest("TEXTBOOK", textbookId)));

        contentMountService.removeMountsByTarget("TEXTBOOK", textbookId);
        assertThat(countMounts("COURSEWARE", coursewareId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM courseware WHERE id = ?",
                Long.class, coursewareId)).isEqualTo(1L);
    }

    @Test
    void coursewareCannotMountToItsOwnSection() {
        long coursewareId = insertCourseware(900231L, "PLATFORM", null, platformSubjectId(), null, 999L);
        long sectionId = insertCoursewareSection(900232L, coursewareId, 1, "{}");

        assertThatThrownBy(() -> contentMountService.replaceMounts(
                platformAdmin(), platformCourseware(coursewareId),
                List.of(new MountRequest("COURSEWARE_SECTION", sectionId))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40000));
    }

    @Test
    void invisibleTargetIsRejectedWith40400() {
        long textbookId = insertTextbook(900241L, platformSubjectId(), "TB-F");
        long coursewareId = insertCourseware(900242L, "PLATFORM", null, platformSubjectId(), null, 999L);
        // 课件本身已授权本校，但学校未启用该教材，目标仍不可见
        insertGrant(900243L, "COURSEWARE", coursewareId, SCHOOL_ID, 999L);
        assertThatThrownBy(() -> contentMountService.replaceMounts(teacher(), platformCourseware(coursewareId),
                List.of(new MountRequest("TEXTBOOK", textbookId))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(40400));
    }

    @Test
    void validationFailureRollsBackWholeReplacement() {
        long textbookId = insertTextbook(900251L, platformSubjectId(), "TB-G");
        long coursewareId = insertCourseware(900252L, "PLATFORM", null, platformSubjectId(), null, 999L);
        ContentRef ref = platformCourseware(coursewareId);
        contentMountService.replaceMounts(platformAdmin(), ref, List.of(new MountRequest("TEXTBOOK", textbookId)));

        // 目标不存在导致整次替换失败，原有挂载行不被清空
        assertThatThrownBy(() -> contentMountService.replaceMounts(platformAdmin(), ref,
                List.of(new MountRequest("TEXTBOOK", 999999L))))
                .isInstanceOf(BusinessException.class);
        assertThat(countMounts("COURSEWARE", coursewareId)).isEqualTo(1);
    }
}
