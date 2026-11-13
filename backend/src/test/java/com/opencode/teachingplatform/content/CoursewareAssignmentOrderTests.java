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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 一次布置的顺序快照：选节按请求数组下标写 display_order、整份按 sort_order 展开，
 * 课件小节排序与正文重新生成都不回写已布置行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoursewareAssignmentOrderTests {

    private static final long SCHOOL_ID = 1L;
    private static final long CLASS_ID = 1L;
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
        jdbcTemplate.update("DELETE FROM courseware_section WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM content_share_grant WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware WHERE id >= 999900");
    }

    @Test
    void selectedSectionsKeepRequestedOrderAndWholeAssignmentExpandsAllSections() throws Exception {
        fixture();
        long s2 = sectionId(2);
        long s5 = sectionId(5);
        long s6 = sectionId(6);

        String first = createAndTrack(body("[" + s2 + "," + s5 + "," + s6 + "]", classTargets()));
        assertThat(first).hasSize(36);
        assertThat(assignmentRows(first)).isEqualTo(3L);
        MvcResult selectedList = studentGet("/api/v1/student/content/assignments")
                .andExpect(status().isOk()).andReturn();
        List<String> selected = JsonPath.read(utf8(selectedList),
                "$.data[?(@.assignmentGroupId == '" + first + "')].sections[*].heading");
        assertThat(selected).containsExactly("节-2", "节-5", "节-6");

        String reordered = createAndTrack(body("[" + s6 + "," + s2 + "," + s5 + "]", classTargets()));
        assertThat(sectionIdsOf(reordered)).containsExactly(s6, s2, s5);

        String whole = createAndTrack(body("[]", classTargets()));
        assertThat(assignmentRows(whole)).isEqualTo(1L);
        assertThat(nullSectionRows(whole)).isEqualTo(1L);
        MvcResult wholeList = studentGet("/api/v1/student/content/assignments")
                .andExpect(status().isOk()).andReturn();
        List<String> headings = JsonPath.read(utf8(wholeList),
                "$.data[?(@.assignmentGroupId == '" + whole + "')].sections[*].heading");
        assertThat(headings).containsExactly("节-1", "节-2", "节-3", "节-4", "节-5", "节-6");

        jdbcTemplate.update("UPDATE courseware_section SET sort_order = ?, content_json = ? WHERE id = ?",
                99, "{\"paragraphs\":[{\"class\":\"text\",\"sentences\":[]}]}", s2);
        assertThat(sectionIdsOf(first)).containsExactly(s2, s5, s6);
    }

    @Test
    void rowCountMatchesTargetsTimesSelectedSections() throws Exception {
        fixture();
        String group = createAndTrack(body("[" + sectionId(1) + "," + sectionId(3) + "]", classTargets()));
        assertThat(assignmentRows(group)).isEqualTo(2L);
        MvcResult result = postAssignment(body("[" + sectionId(4) + "]", classTargets()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowCount").value(1))
                .andReturn();
        track(result);
    }

    @Test
    void invalidBodiesAreRejectedWithoutAnyRow() throws Exception {
        fixture();
        insertCourseware(999902L, "PUBLISHED", false);
        insertSections(999902L, 1);
        long foreignSection = 999902L * 10 + 1;
        long s2 = sectionId(2);
        long before = allAssignmentRows();

        List<String> invalid = List.of(
                "{\"sectionIds\":[],\"targets\":[{\"classId\":1,\"studentIds\":[]}]}",
                "{\"coursewareId\":" + COURSEWARE_ID + ",\"targets\":[{\"classId\":1,\"studentIds\":[]}]}",
                "{\"coursewareId\":" + COURSEWARE_ID + ",\"sectionIds\":[]}",
                "{\"coursewareId\":" + COURSEWARE_ID + ",\"sectionIds\":[],\"targets\":"
                        + "[{\"classId\":1,\"studentIds\":[]}],\"displayOrder\":1}",
                body("[" + s2 + "," + s2 + "]", classTargets()),
                body("[" + foreignSection + "]", classTargets()),
                body("[]", "[]"),
                body("[]", "[{\"classId\":1,\"studentIds\":[]},{\"classId\":1,\"studentIds\":[]}]"),
                body("[]", "[{\"classId\":1,\"studentIds\":[2,2]}]"));

        for (String json : invalid) {
            MvcResult result = postAssignment(json).andReturn();
            assertThat(result.getResponse().getStatus()).as("请求体 %s 应被拒绝", json).isEqualTo(400);
            assertThat(JsonPath.<Integer>read(result.getResponse().getContentAsString(), "$.code"))
                    .as("请求体 %s 的错误码", json).isEqualTo(40000);
        }
        assertThat(allAssignmentRows()).isEqualTo(before);
    }

    private void fixture() {
        insertCourseware(COURSEWARE_ID, "PUBLISHED", true);
        insertSections(COURSEWARE_ID, 6);
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

    private long sectionId(int sortOrder) {
        return COURSEWARE_ID * 10 + sortOrder;
    }

    private String classTargets() {
        return "[{\"classId\":" + CLASS_ID + ",\"studentIds\":[]}]";
    }

    private String body(String sectionIds, String targets) {
        return "{\"coursewareId\":" + COURSEWARE_ID + ",\"sectionIds\":" + sectionIds
                + ",\"targets\":" + targets + "}";
    }

    private ResultActions postAssignment(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/teacher/content/assignments")
                .header("Authorization", teacher())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createAndTrack(String body) throws Exception {
        MvcResult result = postAssignment(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return track(result);
    }

    private String track(MvcResult result) throws Exception {
        String groupId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.assignmentGroupId");
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

    private long assignmentRows(String groupId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courseware_assignment WHERE assignment_group_id = ?", Long.class, groupId);
        return count == null ? 0L : count;
    }

    private long nullSectionRows(String groupId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courseware_assignment WHERE assignment_group_id = ? AND section_id IS NULL",
                Long.class, groupId);
        return count == null ? 0L : count;
    }

    private long allAssignmentRows() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM courseware_assignment", Long.class);
        return count == null ? 0L : count;
    }

    private List<Long> sectionIdsOf(String groupId) {
        return jdbcTemplate.queryForList(
                "SELECT section_id FROM courseware_assignment WHERE assignment_group_id = ? ORDER BY display_order",
                Long.class, groupId);
    }

    private String utf8(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
