package com.opencode.teachingplatform.content;

import com.jayway.jsonpath.JsonPath;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生素材端点的 Range/206 协议层，以及阶段 3 两个 readAsset 端点的 200 全量回归。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoursewareAssetRangeTests {

    private static final long SCHOOL_ID = 1L;
    private static final long CLASS_ID = 1L;
    private static final long COURSEWARE_ID = 999901L;
    private static final long ASSET_ID = 999950L;
    private static final String ASSET_PATH = "stage4-range/range.bin";
    private static final byte[] FULL_BYTES = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final byte[] PART_BYTES = {0, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private SysUserRepository sysUserRepository;
    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;
    @Value("${app.file-storage-root}")
    private String storageRoot;

    private final List<String> groupIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        Path file = Path.of(storageRoot, ASSET_PATH);
        Files.createDirectories(file.getParent());
        Files.write(file, FULL_BYTES);
        insertCourseware(COURSEWARE_ID, "PUBLISHED", true);
        jdbcTemplate.update("INSERT INTO courseware_section "
                        + "(id, courseware_id, heading, sort_order, content_json, created_at, updated_at) "
                        + "VALUES (999902, ?, '节-1', 1, '{\"paragraphs\":[]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                COURSEWARE_ID);
        insertAsset(ASSET_ID, COURSEWARE_ID, ASSET_PATH);
    }

    @AfterEach
    void tearDown() throws IOException {
        for (String groupId : groupIds) {
            jdbcTemplate.update("DELETE FROM courseware_assignment WHERE assignment_group_id = ?", groupId);
        }
        jdbcTemplate.update("DELETE FROM courseware_assignment WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware_asset WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware_section WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM content_share_grant WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware WHERE id >= 999900");
        Files.deleteIfExists(Path.of(storageRoot, ASSET_PATH));
    }

    @Test
    void singleValidRangeReturns206WithHeaders() throws Exception {
        createAndTrack(body());
        studentAssetGet(COURSEWARE_ID, ASSET_ID, "bytes=0-3")
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-3/8"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(PART_BYTES));
        studentAssetGet(COURSEWARE_ID, ASSET_ID, "bytes=4-")
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 4-7/8"))
                .andExpect(content().bytes(new byte[]{4, 5, 6, 7}));
    }

    @Test
    void missingInvalidOrOutOfRangeReturns200FullBytes() throws Exception {
        createAndTrack(body());
        List<String> ranges = List.of("", "bytes=0-1,3-4", "bytes=abc", "bytes=0-99999",
                "bytes=99999999999999999999999-", "bytes=5-2", "bytes=8-9");
        for (String range : ranges) {
            studentAssetGet(COURSEWARE_ID, ASSET_ID, range)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Accept-Ranges", "bytes"))
                    .andExpect(header().doesNotExist("Content-Range"))
                    .andExpect(content().bytes(FULL_BYTES));
        }
    }

    @Test
    void assetOutsideVisibleAssignmentOrWindowReturns40400() throws Exception {
        studentAssetGet(COURSEWARE_ID, ASSET_ID, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        insertAssignment(999951L, "g-expired",
                Timestamp.from(Instant.now().minus(3, ChronoUnit.HOURS)),
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        studentAssetGet(COURSEWARE_ID, ASSET_ID, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        jdbcTemplate.update("DELETE FROM courseware_assignment WHERE id = 999951");
        createAndTrack(body());
        insertCourseware(999903L, "PUBLISHED", false);
        insertAsset(999952L, 999903L, ASSET_PATH);
        studentAssetGet(COURSEWARE_ID, 999952L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void stage3AssetEndpointsStillReturn200FullBytes() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/content/coursewares/{coursewareId}/assets/{assetId}",
                        COURSEWARE_ID, ASSET_ID)
                        .header("Authorization", teacher()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Range"))
                .andExpect(content().bytes(FULL_BYTES));
        mockMvc.perform(get("/api/v1/admin/platform-content/coursewares/{coursewareId}/assets/{assetId}",
                        COURSEWARE_ID, ASSET_ID)
                        .header("Authorization", platform()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Range"))
                .andExpect(content().bytes(FULL_BYTES));
    }

    private void insertCourseware(long id, String status, boolean grant) {
        jdbcTemplate.update("INSERT INTO courseware "
                        + "(id, owner_scope, global_subject_id, title, status, created_by, created_at, updated_at) "
                        + "VALUES (?, 'PLATFORM', ?, ?, ?, 999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, platformSubjectId(), "阶段4课件-" + id, status);
        if (grant) {
            jdbcTemplate.update("INSERT INTO content_share_grant "
                            + "(id, content_type, content_id, school_id, granted_by, created_at, updated_at) "
                            + "VALUES (?, 'COURSEWARE', ?, ?, 999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    id + 20000L, id, SCHOOL_ID);
        }
    }

    private void insertAsset(long id, long coursewareId, String filePath) {
        jdbcTemplate.update("INSERT INTO courseware_asset "
                        + "(id, courseware_id, kind, file_path, mime, size_bytes, created_at, updated_at) "
                        + "VALUES (?, ?, 'IMAGE', ?, 'application/octet-stream', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, coursewareId, filePath);
    }

    private void insertAssignment(long id, String groupId, Timestamp startAt, Timestamp endAt) {
        jdbcTemplate.update("INSERT INTO courseware_assignment "
                        + "(id, school_id, class_id, target_type, target_id, courseware_id, section_id, "
                        + "assignment_group_id, display_order, start_at, end_at, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'CLASS', ?, ?, NULL, ?, 0, ?, ?, 999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, SCHOOL_ID, CLASS_ID, CLASS_ID, COURSEWARE_ID, groupId, startAt, endAt);
    }

    private String body() {
        return "{\"coursewareId\":" + COURSEWARE_ID + ",\"sectionIds\":[],\"targets\":"
                + "[{\"classId\":" + CLASS_ID + ",\"studentIds\":[]}]}";
    }

    private String createAndTrack(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/content/assignments")
                        .header("Authorization", teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        String groupId = JsonPath.read(json, "$.data.assignmentGroupId");
        groupIds.add(groupId);
        return groupId;
    }

    private ResultActions studentAssetGet(long coursewareId, long assetId, String range) throws Exception {
        MockHttpServletRequestBuilder builder = get(
                "/api/v1/student/content/coursewares/{coursewareId}/assets/{assetId}",
                coursewareId, assetId).header("Authorization", student());
        if (range != null) {
            builder.header("Range", range);
        }
        return mockMvc.perform(builder);
    }

    private String teacher() {
        return bearer("t9001", UserRole.TEACHER);
    }

    private String student() {
        return bearer("20260001", UserRole.STUDENT);
    }

    private String platform() {
        return bearer("platform9001", UserRole.PLATFORM_ADMIN);
    }

    private String bearer(String username, UserRole role) {
        Long userId = sysUserRepository.findAllByUsernameAndRole(username, role).get(0).getId();
        return IntegrationTestAuthSupport.bearerToken(jwtTokenService, sysUserRepository,
                userLoginSessionRepository, userId);
    }

    private long platformSubjectId() {
        Long id = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM subject WHERE status = 'ACTIVE'", Long.class);
        return id == null ? 1L : id;
    }
}
