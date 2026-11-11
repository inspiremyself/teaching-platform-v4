package com.opencode.teachingplatform.content;

import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentFilterRequest;
import com.opencode.teachingplatform.content.dto.ContentRequests.ContentRef;
import com.opencode.teachingplatform.content.repository.ContentMountRepository;
import com.opencode.teachingplatform.content.repository.ContentShareGrantRepository;
import com.opencode.teachingplatform.content.repository.CoursewareAssignmentRepository;
import com.opencode.teachingplatform.content.repository.CoursewareSectionRepository;
import com.opencode.teachingplatform.content.service.ContentFilterService;
import com.opencode.teachingplatform.content.service.ContentMountService;
import com.opencode.teachingplatform.content.service.ContentShareGrantService;
import com.opencode.teachingplatform.content.service.ContentSubjectScopeService;
import com.opencode.teachingplatform.content.service.ContentVisibilityService;
import com.opencode.teachingplatform.content.service.CoursewareAssignmentTargetService;
import com.opencode.teachingplatform.content.service.TaskSubjectValidator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 内容目录域集成测试底座：真实 H2 + Flyway 上下文，测试数据用显式 id 造数并由测试事务整体回滚。
 *
 * <p>不 mock 任何领域服务与仓储，保证挂载 / 授权 / 筛选 / 布置目标断言落在真实 schema 上。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class ContentIntegrationTestSupport {

    protected static final long SCHOOL_ID = 1L;
    protected static final long OTHER_SCHOOL_ID = 9200L;
    protected static final long CLASS_ID = 1L;
    protected static final long STUDENT_ID = 2L;
    protected static final long TEACHER_ID = 1L;

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ContentVisibilityService contentVisibilityService;
    @Autowired
    protected ContentSubjectScopeService contentSubjectScopeService;
    @Autowired
    protected ContentMountService contentMountService;
    @Autowired
    protected ContentShareGrantService contentShareGrantService;
    @Autowired
    protected ContentFilterService contentFilterService;
    @Autowired
    protected CoursewareAssignmentTargetService coursewareAssignmentTargetService;
    @Autowired
    protected TaskSubjectValidator taskSubjectValidator;
    @Autowired
    protected ContentMountRepository contentMountRepository;
    @Autowired
    protected ContentShareGrantRepository contentShareGrantRepository;
    @Autowired
    protected CoursewareAssignmentRepository coursewareAssignmentRepository;
    @Autowired
    protected CoursewareSectionRepository coursewareSectionRepository;

    @BeforeEach
    void clearContentRows() {
        jdbcTemplate.update("DELETE FROM courseware_assignment WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM content_share_grant WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM content_mount WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM courseware_section WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM courseware WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM school_textbook WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM textbook_section WHERE id >= 900000");
        jdbcTemplate.update("DELETE FROM textbook WHERE id >= 900000");
    }

    protected CurrentUser teacher() {
        return new CurrentUser(TEACHER_ID, "t9001", "演示教师", UserRole.TEACHER, SCHOOL_ID, null);
    }

    protected CurrentUser platformAdmin() {
        return new CurrentUser(999L, "platform9001", "平台超管", UserRole.PLATFORM_ADMIN, null, null);
    }

    protected CurrentUser schoolAdmin() {
        return new CurrentUser(4L, "admin9001", "演示校管", UserRole.SCHOOL_ADMIN, SCHOOL_ID, null);
    }

    protected long activeSchoolSubjectId() {
        Long id = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM school_subject WHERE school_id = ? AND status = 'ACTIVE'",
                Long.class, SCHOOL_ID);
        if (id == null) {
            throw new IllegalStateException("测试库缺少本校 ACTIVE 学科种子");
        }
        return id;
    }

    protected long platformSubjectId() {
        Long id = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM subject WHERE status = 'ACTIVE'", Long.class);
        if (id == null) {
            throw new IllegalStateException("测试库缺少平台 ACTIVE 学科种子");
        }
        return id;
    }

    protected long insertTextbook(long id, long globalSubjectId, String code) {
        jdbcTemplate.update("INSERT INTO textbook (id, global_subject_id, code, title, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, globalSubjectId, code, "教材-" + code, 999L);
        return id;
    }

    protected long insertTextbookSection(long id, long textbookId, Long parentId, int sortOrder) {
        jdbcTemplate.update("INSERT INTO textbook_section "
                        + "(id, textbook_id, parent_id, heading, sort_order, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, textbookId, parentId, "小节-" + sortOrder, sortOrder);
        return id;
    }

    protected void enableTextbook(long id, long schoolId, long textbookId) {
        jdbcTemplate.update("INSERT INTO school_textbook (id, school_id, textbook_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, schoolId, textbookId);
    }

    protected long insertCourseware(long id, String ownerScope, Long ownerSchoolId,
                                    Long globalSubjectId, Long schoolSubjectId, Long createdBy) {
        jdbcTemplate.update("INSERT INTO courseware "
                        + "(id, owner_scope, owner_school_id, global_subject_id, school_subject_id, title, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, ownerScope, ownerSchoolId, globalSubjectId, schoolSubjectId, "课件-" + id, createdBy);
        return id;
    }

    protected long insertCoursewareSection(long id, long coursewareId, int sortOrder, String contentJson) {
        jdbcTemplate.update("INSERT INTO courseware_section "
                        + "(id, courseware_id, heading, sort_order, content_json, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, coursewareId, "节-" + sortOrder, sortOrder, contentJson);
        return id;
    }

    protected long insertMount(long id, String contentType, long contentId, String mountType, long targetId) {
        jdbcTemplate.update("INSERT INTO content_mount "
                        + "(id, content_type, content_id, mount_type, mount_target_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, contentType, contentId, mountType, targetId);
        return id;
    }

    protected void insertGrant(long id, String contentType, long contentId, long schoolId, long grantedBy) {
        jdbcTemplate.update("INSERT INTO content_share_grant "
                        + "(id, content_type, content_id, school_id, granted_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, contentType, contentId, schoolId, grantedBy);
    }

    protected void insertAssignment(long id, long schoolId, long classId, String targetType, long targetId,
                                    long coursewareId, Long sectionId, String groupId, int displayOrder) {
        // 一次布置的多行共享同一 created_at，保证组内按 display_order 稳定升序
        jdbcTemplate.update("INSERT INTO courseware_assignment "
                        + "(id, school_id, class_id, target_type, target_id, courseware_id, section_id, "
                        + "assignment_group_id, display_order, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-11-11 00:00:00', "
                        + "TIMESTAMP '2026-11-11 00:00:00')",
                id, schoolId, classId, targetType, targetId, coursewareId, sectionId,
                groupId, displayOrder, TEACHER_ID);
    }

    protected ContentRef platformCourseware(long contentId) {
        return new ContentRef("COURSEWARE", contentId, "PLATFORM", null, 999L);
    }

    protected ContentFilterRequest allFilter() {
        return new ContentFilterRequest("ALL", null, null, null, null);
    }

    /** H2 DATABASE_TO_LOWER=TRUE 下元数据表名大小写不统一，按实际表名回查后再取列/索引。 */
    protected Set<String> actualTableNames(DatabaseMetaData meta, String logical) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.toLowerCase(Locale.ROOT).equals(logical.toLowerCase(Locale.ROOT))) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    protected long countRows(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id >= 900000", Long.class);
        return count == null ? 0L : count;
    }

    protected long countMounts(String contentType, long contentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_mount WHERE content_type = ? AND content_id = ?",
                Long.class, contentType, contentId);
        return count == null ? 0L : count;
    }
}
