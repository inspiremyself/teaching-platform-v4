package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreatePlatformSubjectRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateTextbookRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.CreateTextbookSectionRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.UpdatePlatformSubjectRequest;
import com.opencode.teachingplatform.content.service.PlatformCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台学科字典与教材目录：编码唯一不可变、状态启停、教材必须指向 ACTIVE 平台学科、叶子判定。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformContentSubjectTests extends ContentIntegrationTestSupport {

    @Autowired
    private PlatformCatalogService platformCatalogService;

    @AfterEach
    void restoreSubjectStatus() {
        jdbcTemplate.update("UPDATE subject SET status = 'ACTIVE' WHERE status = 'DISABLED'");
        jdbcTemplate.update("DELETE FROM subject WHERE id >= 900000");
    }

    @Test
    void createRenameAndToggleSubject() {
        var created = platformCatalogService.createSubject(new CreatePlatformSubjectRequest("GEO-TEST", "地理"));
        assertThat(created.code()).isEqualTo("GEO-TEST");
        assertThat(created.status()).isEqualTo("ACTIVE");

        var renamed = platformCatalogService.updateSubject(created.id(), new UpdatePlatformSubjectRequest("地理学"));
        assertThat(renamed.name()).isEqualTo("地理学");
        assertThat(renamed.code()).isEqualTo("GEO-TEST");

        var disabled = platformCatalogService.changeSubjectStatus(created.id(),
                new com.opencode.teachingplatform.content.dto.ContentRequests.ChangeSubjectStatusRequest("DISABLED"));
        assertThat(disabled.status()).isEqualTo("DISABLED");

        var enabled = platformCatalogService.changeSubjectStatus(created.id(),
                new com.opencode.teachingplatform.content.dto.ContentRequests.ChangeSubjectStatusRequest("ACTIVE"));
        assertThat(enabled.status()).isEqualTo("ACTIVE");
    }

    @Test
    void duplicateSubjectCodeRejected() {
        platformCatalogService.createSubject(new CreatePlatformSubjectRequest("GEO-DUP", "地理"));

        assertThatThrownBy(() -> platformCatalogService.createSubject(
                new CreatePlatformSubjectRequest("GEO-DUP", "地理副本")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40902);
    }

    @Test
    void illegalSubjectStatusRejected() {
        assertThatThrownBy(() -> platformCatalogService.changeSubjectStatus(platformSubjectId(),
                new com.opencode.teachingplatform.content.dto.ContentRequests.ChangeSubjectStatusRequest("ARCHIVED")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("学科状态须为 ACTIVE 或 DISABLED");
    }

    @Test
    void textbookRequiresActivePlatformSubject() {
        assertThatThrownBy(() -> platformCatalogService.createTextbook(
                new CreateTextbookRequest(999999L, "GEO-INVALID", "无效教材")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40025);

        Long disabledId = platformSubjectId();
        jdbcTemplate.update("UPDATE subject SET status = 'DISABLED' WHERE id = ?", disabledId);
        assertThatThrownBy(() -> platformCatalogService.createTextbook(
                new CreateTextbookRequest(disabledId, "GEO-DISABLED", "停用学科教材")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40025);
    }

    @Test
    void duplicateTextbookCodeRejected() {
        platformCatalogService.createTextbook(new CreateTextbookRequest(platformSubjectId(), "TB-DUP", "教材一"));

        assertThatThrownBy(() -> platformCatalogService.createTextbook(
                new CreateTextbookRequest(platformSubjectId(), "TB-DUP", "教材二")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40900);
    }

    @Test
    void leafSectionIsDerivedFromChildRows() {
        var textbook = platformCatalogService.createTextbook(
                new CreateTextbookRequest(platformSubjectId(), "TB-LEAF", "叶子教材"));
        var parent = platformCatalogService.createSection(textbook.id(),
                new CreateTextbookSectionRequest(null, "父小节", 1));
        platformCatalogService.createSection(textbook.id(), new CreateTextbookSectionRequest(parent.id(), "子小节", 2));

        var sections = platformCatalogService.listSections(textbook.id());

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).leaf()).isFalse();
        assertThat(sections.get(1).leaf()).isTrue();
    }
}
