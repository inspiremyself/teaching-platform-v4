package com.opencode.teachingplatform.content;

import com.jayway.jsonpath.JsonPath;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生阅读：班级反查、可见集合、列表 / 正文 / 素材共用同一归属与时间窗判定、已布置生命周期。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentContentReadTests {

    private static final long SCHOOL_ID = 1L;
    private static final long CLASS_ID = 1L;
    private static final long STUDENT_USER_ID = 2L;
    private static final long COURSEWARE_ID = 999901L;
    private static final String CONTENT_JSON = "{\"paragraphs\":[]}";

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

    private final List<String> groupIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String groupId : groupIds) {
            jdbcTemplate.update("DELETE FROM courseware_assignment WHERE assignment_group_id = ?", groupId);
        }
        jdbcTemplate.update("DELETE FROM courseware_assignment WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM content_mount WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware_section WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM content_share_grant WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware WHERE id >= 999900");
    }

    @Test
    void classMemberLookupDrivesVisibility() throws Exception {
        fixture();
        String group = createAndTrack(body());
        MvcResult visibleList = studentGet("/api/v1/student/content/assignments")
                .andExpect(status().isOk()).andReturn();
        List<String> visibleHeadings = JsonPath.read(utf8(visibleList),
                "$.data[?(@.assignmentGroupId == '" + group + "')].sections[*].heading");
        assertThat(visibleHeadings).containsExactly("节-1", "节-2", "节-3");

        jdbcTemplate.update("DELETE FROM class_member WHERE student_user_id = ?", STUDENT_USER_ID);
        try {
            MvcResult emptyList = studentGet("/api/v1/student/content/assignments")
                    .andExpect(status().isOk()).andReturn();
            List<String> visibleGroups = JsonPath.read(utf8(emptyList), "$.data[*].assignmentGroupId");
            assertThat(visibleGroups).doesNotContain(group);
            studentGet(sectionPath(COURSEWARE_ID, sectionId(COURSEWARE_ID, 1)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(40400));
        } finally {
            jdbcTemplate.update("INSERT INTO class_member "
                            + "(id, class_id, student_user_id, joined_at, created_at, updated_at) "
                            + "VALUES (1, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    STUDENT_USER_ID);
        }
    }

    @Test
    void onlyOwnClassAndOwnNamedRowsAreVisible() throws Exception {
        fixture();
        for (long id = 999902L; id <= 999905L; id++) {
            insertCourseware(id, "PUBLISHED", false);
            insertSections(id, 1);
        }
        insertAssignment(999910L, SCHOOL_ID, 999901L, "CLASS", 999901L, 999902L, null,
                "g-other-class", 0, null, null, now());
        insertAssignment(999911L, 9200L, CLASS_ID, "CLASS", CLASS_ID, 999903L, null,
                "g-other-school", 0, null, null, now());
        insertAssignment(999912L, SCHOOL_ID, CLASS_ID, "STUDENT", 999999L, 999904L, null,
                "g-other-student", 0, null, null, now());
        insertAssignment(999913L, SCHOOL_ID, CLASS_ID, "STUDENT", STUDENT_USER_ID, 999905L, null,
                "g-named", 0, null, null, now());
        createAndTrack(body());

        MvcResult result = studentGet("/api/v1/student/content/assignments")
                .andExpect(status().isOk()).andReturn();
        List<Integer> visible = JsonPath.read(utf8(result), "$.data[*].coursewareId");
        assertThat(visible).contains(999901, 999905).doesNotContain(999902, 999903, 999904);

        for (long coursewareId : new long[]{999902L, 999903L, 999904L}) {
            studentGet(sectionPath(coursewareId, sectionId(coursewareId, 1)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(40400));
        }
        studentGet(sectionPath(999905L, sectionId(999905L, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coursewareId").value(999905));
    }

    @Test
    void groupOrderWholeExpansionAndSelectedOrder() throws Exception {
        fixture();
        Timestamp older = Timestamp.valueOf("2026-11-10 00:00:00");
        Timestamp newer = Timestamp.valueOf("2026-11-12 00:00:00");
        insertAssignment(999920L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID, null,
                "g-whole", 0, null, null, older);
        insertAssignment(999921L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID,
                sectionId(COURSEWARE_ID, 3), "g-order", 1, null, null, newer);
        insertAssignment(999922L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID,
                sectionId(COURSEWARE_ID, 1), "g-order", 2, null, null, newer);

        MvcResult result = studentGet("/api/v1/student/content/assignments")
                .andExpect(status().isOk()).andReturn();
        String payload = utf8(result);
        List<String> allGroups = JsonPath.read(payload, "$.data[*].assignmentGroupId");
        assertThat(allGroups.indexOf("g-order")).isLessThan(allGroups.indexOf("g-whole"));
        List<String> ordered = JsonPath.read(payload,
                "$.data[?(@.assignmentGroupId == 'g-order')].sections[*].heading");
        assertThat(ordered).containsExactly("节-3", "节-1");
        List<String> whole = JsonPath.read(payload,
                "$.data[?(@.assignmentGroupId == 'g-whole')].sections[*].heading");
        assertThat(whole).containsExactly("节-1", "节-2", "节-3");
    }

    @Test
    void timeWindowFiltersListAndDirectLinks() throws Exception {
        fixture();
        insertAssignment(999930L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID, null,
                "g-in-window", 0, hoursAgo(1), hoursAhead(1), now());
        insertAssignment(999931L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID, null,
                "g-out-window", 0, hoursAgo(3), hoursAgo(2), now());
        insertAssignment(999932L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID, null,
                "g-no-window", 0, null, null, now());

        MvcResult result = studentGet("/api/v1/student/content/assignments")
                .andExpect(status().isOk()).andReturn();
        List<String> groups = JsonPath.read(utf8(result), "$.data[*].assignmentGroupId");
        assertThat(groups).contains("g-in-window", "g-no-window").doesNotContain("g-out-window");

        studentGet(sectionPath(COURSEWARE_ID, sectionId(COURSEWARE_ID, 1)))
                .andExpect(status().isOk());
    }

    @Test
    void unassignedAndWindowedAssetsAreRejected() throws Exception {
        fixture();
        insertAssignment(999940L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, COURSEWARE_ID, null,
                "g-expired", 0, hoursAgo(3), hoursAgo(2), now());
        studentGet(sectionPath(COURSEWARE_ID, sectionId(COURSEWARE_ID, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
        studentGet("/api/v1/student/content/coursewares/" + COURSEWARE_ID + "/assets/999950")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void assignedContentSurvivesStatusGrantMountAndRegeneration() throws Exception {
        fixture();
        createAndTrack(body());
        jdbcTemplate.update("UPDATE courseware SET status = 'DRAFT' WHERE id = ?", COURSEWARE_ID);
        jdbcTemplate.update("DELETE FROM content_share_grant WHERE content_id = ?", COURSEWARE_ID);
        jdbcTemplate.update("INSERT INTO content_mount "
                        + "(id, content_type, content_id, mount_type, mount_target_id, created_at, updated_at) "
                        + "VALUES (999941, 'COURSEWARE', ?, 'TEXTBOOK', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                COURSEWARE_ID);
        String regenerated = "{\"paragraphs\":[{\"class\":\"note\",\"sentences\":[{\"text\":\"新正文\"}]}]}";
        jdbcTemplate.update("UPDATE courseware_section SET content_json = ? WHERE id = ?",
                regenerated, sectionId(COURSEWARE_ID, 2));

        studentGet(sectionPath(COURSEWARE_ID, sectionId(COURSEWARE_ID, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentJson").value(regenerated))
                .andExpect(jsonPath("$.data.sectionId").value(sectionId(COURSEWARE_ID, 2)));

        mockMvc.perform(delete("/api/v1/admin/platform-content/coursewares/{id}", COURSEWARE_ID)
                        .header("Authorization", platform()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void studentCannotReachTeacherOrPlatformEndpoints() throws Exception {
        fixture();
        studentGet("/api/v1/teacher/content/assignments?classId=1")
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/teacher/content/assignments")
                        .header("Authorization", student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
        studentGet("/api/v1/admin/platform-content/coursewares")
                .andExpect(status().isForbidden());
    }

    private void fixture() {
        insertCourseware(COURSEWARE_ID, "PUBLISHED", true);
        insertSections(COURSEWARE_ID, 3);
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

    private void insertSections(long coursewareId, int count) {
        for (int i = 1; i <= count; i++) {
            jdbcTemplate.update("INSERT INTO courseware_section "
                            + "(id, courseware_id, heading, sort_order, content_json, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    coursewareId * 10 + i, coursewareId, "节-" + i, i, CONTENT_JSON);
        }
    }

    private void insertAssignment(long id, long schoolId, long classId, String targetType, long targetId,
                                  long coursewareId, Long sectionId, String groupId, int displayOrder,
                                  Timestamp startAt, Timestamp endAt, Timestamp createdAt) {
        jdbcTemplate.update("INSERT INTO courseware_assignment "
                        + "(id, school_id, class_id, target_type, target_id, courseware_id, section_id, "
                        + "assignment_group_id, display_order, start_at, end_at, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 999, ?, CURRENT_TIMESTAMP)",
                id, schoolId, classId, targetType, targetId, coursewareId, sectionId, groupId,
                displayOrder, startAt, endAt, createdAt);
    }

    private long sectionId(long coursewareId, int sortOrder) {
        return coursewareId * 10 + sortOrder;
    }

    private String sectionPath(long coursewareId, long sectionId) {
        return "/api/v1/student/content/coursewares/" + coursewareId + "/sections/" + sectionId;
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
        String groupId = JsonPath.read(utf8(result), "$.data.assignmentGroupId");
        groupIds.add(groupId);
        return groupId;
    }

    private ResultActions studentGet(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", student()));
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

    private Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private Timestamp hoursAgo(int hours) {
        return Timestamp.from(Instant.now().minus(hours, ChronoUnit.HOURS));
    }

    private Timestamp hoursAhead(int hours) {
        return Timestamp.from(Instant.now().plus(hours, ChronoUnit.HOURS));
    }

    private String utf8(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
