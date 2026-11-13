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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教师布置端点：目标展开、严格字段白名单、逐个班级鉴权、可用性门禁、时间窗与按班读取。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeacherContentAssignmentTests {

    private static final long SCHOOL_ID = 1L;
    private static final long CLASS_ID = 1L;
    private static final long STUDENT_USER_ID = 2L;
    private static final long SECOND_CLASS_ID = 999901L;
    private static final long UNASSIGNED_CLASS_ID = 999902L;
    private static final long OTHER_SCHOOL_CLASS_ID = 999903L;
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
        jdbcTemplate.update("DELETE FROM class_teacher WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM class_member WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM class_room WHERE id >= 999900");
    }

    @Test
    void targetsExpandIntoExpectedRows() throws Exception {
        fixture();
        insertSecondClass();
        String oneSection = "[" + sectionId(1) + "]";

        String classWide = createAndTrack(body(oneSection, classTargets()));
        assertThat(targetRows(classWide)).containsExactly("CLASS:1:1:1");

        String multiple = createAndTrack(body(oneSection, twoClassTargets()));
        assertThat(targetRows(multiple)).containsExactly(
                "CLASS:1:1:1", "CLASS:" + SECOND_CLASS_ID + ":" + SECOND_CLASS_ID + ":1");

        String named = createAndTrack(body(oneSection, namedTargets()));
        assertThat(targetRows(named)).containsExactly("STUDENT:2:1:1");

        String mixed = createAndTrack(body("[" + sectionId(1) + "," + sectionId(2) + "]", mixedTargets()));
        assertThat(targetRows(mixed)).containsExactly(
                "STUDENT:2:1:1", "STUDENT:2:1:2",
                "CLASS:" + SECOND_CLASS_ID + ":" + SECOND_CLASS_ID + ":1",
                "CLASS:" + SECOND_CLASS_ID + ":" + SECOND_CLASS_ID + ":2");
        assertThat(assignmentRows(mixed)).isEqualTo(4L);
    }

    @Test
    void postAndGetEnforceClassAccess() throws Exception {
        fixture();
        insertOtherClasses();

        postAssignment(body("[]", "[{\"classId\":" + UNASSIGNED_CLASS_ID + ",\"studentIds\":[]}]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        teacherGet(UNASSIGNED_CLASS_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        postAssignment(body("[]", "[{\"classId\":" + OTHER_SCHOOL_CLASS_ID + ",\"studentIds\":[]}]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        teacherGet(OTHER_SCHOOL_CLASS_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void failedTargetResolutionRollsBackWholeRequest() throws Exception {
        fixture();
        insertOtherClasses();
        long before = allAssignmentRows();
        postAssignment(body("[]", "[{\"classId\":1,\"studentIds\":[]},"
                        + "{\"classId\":" + UNASSIGNED_CLASS_ID + ",\"studentIds\":[]}]"))
                .andExpect(status().isForbidden());
        assertThat(allAssignmentRows()).isEqualTo(before);
    }

    @Test
    void availabilityGateAndWindowValidation() throws Exception {
        fixture();
        long ungranted = 999904L;
        insertCourseware(ungranted, "PUBLISHED", false);
        insertSections(ungranted, 1);
        postAssignment("{\"coursewareId\":" + ungranted + ",\"sectionIds\":[],\"targets\":"
                        + classTargets() + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        long draft = 999905L;
        insertCourseware(draft, "DRAFT", true);
        insertSections(draft, 1);
        postAssignment("{\"coursewareId\":" + draft + ",\"sectionIds\":[],\"targets\":"
                        + classTargets() + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        postAssignment(withWindow("2026-11-13T10:00:00Z", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        postAssignment(withWindow(null, "2026-11-13T11:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        postAssignment(withWindow("2026-11-13T11:00:00Z", "2026-11-13T10:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        createAndTrack(withWindow("2026-11-13T10:00:00Z", "2026-11-13T11:00:00Z"));
    }

    @Test
    void windowedAssignmentsAreStillReturnedByTeacherGet() throws Exception {
        fixture();
        String group = createAndTrack(withWindow("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"));
        MvcResult result = teacherGet(CLASS_ID).andExpect(status().isOk()).andReturn();
        List<String> groups = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data[?(@.assignmentGroupId == '" + group + "')].assignmentGroupId");
        assertThat(groups).containsExactly(group);
    }

    @Test
    void listByClassReturnsGroupOrder() throws Exception {
        fixture();
        long s2 = sectionId(2);
        long s5 = sectionId(5);
        String group = createAndTrack(body("[" + s2 + "," + s5 + "]", classTargets()));
        MvcResult result = teacherGet(CLASS_ID).andExpect(status().isOk()).andReturn();
        List<Integer> orders = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data[?(@.assignmentGroupId == '" + group + "')].displayOrder");
        assertThat(orders).containsExactlyInAnyOrder(1, 2);
        assertThat(sectionIdsOf(group)).containsExactly(s2, s5);
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

    private void insertSecondClass() {
        insertClassRoom(SECOND_CLASS_ID, SCHOOL_ID, "STAGE4-2");
        jdbcTemplate.update("INSERT INTO class_teacher "
                        + "(id, class_id, teacher_user_id, role, assigned_at, created_at, updated_at) "
                        + "VALUES (?, ?, 1, 'SUBJECT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SECOND_CLASS_ID, SECOND_CLASS_ID);
    }

    private void insertOtherClasses() {
        insertClassRoom(UNASSIGNED_CLASS_ID, SCHOOL_ID, "STAGE4-3");
        insertClassRoom(OTHER_SCHOOL_CLASS_ID, 9200L, "STAGE4-4");
    }

    private void insertClassRoom(long id, long schoolId, String code) {
        jdbcTemplate.update("INSERT INTO class_room "
                        + "(id, name, code, status, school_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, "阶段4班级-" + code, code, schoolId);
    }

    private long sectionId(int sortOrder) {
        return COURSEWARE_ID * 10 + sortOrder;
    }

    private String classTargets() {
        return "[{\"classId\":" + CLASS_ID + ",\"studentIds\":[]}]";
    }

    private String twoClassTargets() {
        return "[{\"classId\":" + CLASS_ID + ",\"studentIds\":[]},"
                + "{\"classId\":" + SECOND_CLASS_ID + ",\"studentIds\":[]}]";
    }

    private String namedTargets() {
        return "[{\"classId\":" + CLASS_ID + ",\"studentIds\":[" + STUDENT_USER_ID + "]}]";
    }

    private String mixedTargets() {
        return "[{\"classId\":" + CLASS_ID + ",\"studentIds\":[" + STUDENT_USER_ID + "]},"
                + "{\"classId\":" + SECOND_CLASS_ID + ",\"studentIds\":[]}]";
    }

    private String body(String sectionIds, String targets) {
        return "{\"coursewareId\":" + COURSEWARE_ID + ",\"sectionIds\":" + sectionIds
                + ",\"targets\":" + targets + "}";
    }

    private String withWindow(String startAt, String endAt) {
        StringBuilder json = new StringBuilder("{\"coursewareId\":").append(COURSEWARE_ID)
                .append(",\"sectionIds\":[],\"targets\":").append(classTargets());
        if (startAt != null) {
            json.append(",\"startAt\":\"").append(startAt).append("\"");
        }
        if (endAt != null) {
            json.append(",\"endAt\":\"").append(endAt).append("\"");
        }
        return json.append("}").toString();
    }

    private ResultActions postAssignment(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/teacher/content/assignments")
                .header("Authorization", teacher())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions teacherGet(long classId) throws Exception {
        return mockMvc.perform(get("/api/v1/teacher/content/assignments")
                .param("classId", String.valueOf(classId))
                .header("Authorization", teacher()));
    }

    private String createAndTrack(String body) throws Exception {
        MvcResult result = postAssignment(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String groupId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.assignmentGroupId");
        groupIds.add(groupId);
        return groupId;
    }

    private String teacher() {
        Long userId = sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER).get(0).getId();
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

    private long allAssignmentRows() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM courseware_assignment", Long.class);
        return count == null ? 0L : count;
    }

    private List<String> targetRows(String groupId) {
        return jdbcTemplate.query(
                "SELECT target_type, target_id, class_id, display_order FROM courseware_assignment "
                        + "WHERE assignment_group_id = ? ORDER BY class_id, display_order",
                (rs, rowNum) -> rs.getString(1) + ":" + rs.getLong(2) + ":" + rs.getLong(3)
                        + ":" + rs.getInt(4),
                groupId);
    }

    private List<Long> sectionIdsOf(String groupId) {
        return jdbcTemplate.queryForList(
                "SELECT section_id FROM courseware_assignment WHERE assignment_group_id = ? ORDER BY display_order",
                Long.class, groupId);
    }
}
