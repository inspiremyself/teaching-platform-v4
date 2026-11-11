package com.opencode.teachingplatform.content;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台内容端点族：类级鉴权覆盖 create / update / delete 兄弟操作，素材白名单与挂载全量替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformContentTests {

    private static final String VALID_CONTENT = "{\"version\":1,\"paragraphs\":[{\"class\":\"text\","
            + "\"sentences\":[{\"text\":\"正文\",\"narration\":\"\",\"image\":\"\",\"audio\":\"\"}],"
            + "\"exercises\":[]}]}";

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

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM subject WHERE code LIKE 'GEO_MVC%'");
        jdbcTemplate.update("DELETE FROM content_mount WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware_section WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM courseware WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM textbook_section WHERE id >= 999900");
        jdbcTemplate.update("DELETE FROM textbook WHERE id >= 999900");
    }

    @Test
    void platformAdminCanWriteButTeacherIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/platform-content/subjects")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GEO_MVC\",\"name\":\"地理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("GEO_MVC"));

        mockMvc.perform(post("/api/v1/admin/platform-content/subjects")
                        .header("Authorization", teacherBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GEO_MVC2\",\"name\":\"地理\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coursewareEndpointsCoverCreateUpdateAndDelete() throws Exception {
        mockMvc.perform(post("/api/v1/admin/platform-content/coursewares/generate")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"globalSubjectId\":1,\"textbookSectionId\":1,\"title\":\"鉴权课件\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40025));

        mockMvc.perform(get("/api/v1/admin/platform-content/coursewares")
                        .header("Authorization", platformBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/v1/admin/platform-content/coursewares/999999")
                        .header("Authorization", platformBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void updateReplacesMountsWholesale() throws Exception {
        long coursewareId = 999901L;
        jdbcTemplate.update("INSERT INTO courseware (id, owner_scope, global_subject_id, title, status, created_by, "
                + "created_at, updated_at) VALUES (?, 'PLATFORM', ?, ?, 'DRAFT', 999, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP)", coursewareId, platformSubjectId(), "挂载课件");
        jdbcTemplate.update("INSERT INTO textbook (id, global_subject_id, code, title, created_by, created_at, "
                + "updated_at) VALUES (999902, ?, 'MVC-TB', '教材', 999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                platformSubjectId());
        jdbcTemplate.update("INSERT INTO textbook_section (id, textbook_id, heading, sort_order, created_at, "
                + "updated_at) VALUES (999903, 999902, '叶子小节', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        String body = "{\"title\":\"挂载课件\",\"globalSubjectId\":" + platformSubjectId()
                + ",\"sections\":[{\"heading\":\"小节\",\"sortOrder\":1,\"contentJson\":\""
                + escape(VALID_CONTENT) + "\"}],\"mounts\":[{\"mountType\":\"TEXTBOOK_SECTION\","
                + "\"mountTargetId\":999903}]}";

        mockMvc.perform(put("/api/v1/admin/platform-content/coursewares/{id}", coursewareId)
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mounts.length()").value(1));

        Integer mounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_mount WHERE content_type = 'COURSEWARE' AND content_id = ?",
                Integer.class, coursewareId);
        assert mounts != null && mounts == 1;
    }

    @Test
    void unsupportedAssetTypeRejected() throws Exception {
        long coursewareId = 999911L;
        jdbcTemplate.update("INSERT INTO courseware (id, owner_scope, global_subject_id, title, status, created_by, "
                + "created_at, updated_at) VALUES (?, 'PLATFORM', ?, ?, 'DRAFT', 999, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP)", coursewareId, platformSubjectId(), "素材课件");

        mockMvc.perform(multipart("/api/v1/admin/platform-content/coursewares/{id}/assets", coursewareId)
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", "x".getBytes()))
                        .header("Authorization", platformBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private String platformBearer() {
        Long userId = sysUserRepository.findAllByUsernameAndRole("platform9001", UserRole.PLATFORM_ADMIN)
                .get(0).getId();
        return IntegrationTestAuthSupport.bearerToken(jwtTokenService, sysUserRepository, userLoginSessionRepository,
                userId);
    }

    private String teacherBearer() {
        Long userId = sysUserRepository.findAllByUsernameAndRole("t9001", UserRole.TEACHER).get(0).getId();
        return IntegrationTestAuthSupport.bearerToken(jwtTokenService, sysUserRepository, userLoginSessionRepository,
                userId);
    }

    private String escape(String json) {
        return json.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Long platformSubjectId() {
        return jdbcTemplate.queryForObject("SELECT MIN(id) FROM subject WHERE status = 'ACTIVE'", Long.class);
    }
}
