package com.opencode.teachingplatform.material;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.file.SeedMaterialInitializer;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import com.opencode.teachingplatform.material.entity.CourseMaterial;
import com.opencode.teachingplatform.material.repository.CourseMaterialRepository;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaterialControllerTests {

    @TempDir
    static Path storageRoot;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseMaterialRepository courseMaterialRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @Autowired
    private SeedMaterialInitializer seedMaterialInitializer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage-root", () -> storageRoot.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        courseMaterialRepository.deleteAll();
        if (storageRoot != null && Files.exists(storageRoot)) {
            try (var paths = Files.walk(storageRoot)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(storageRoot))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void teacherCanUploadMaterialAndMetadataIsStored() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "architecture-notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "real material bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .param("title", "体系结构导读")
                        .param("category", "COURSE")
                        .param("description", "第 1 讲")
                        .param("visibility", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("体系结构导读"))
                .andExpect(jsonPath("$.data.fileName").value("architecture-notes.txt"))
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());

        List<CourseMaterial> materials = courseMaterialRepository.findAll();
        assertThat(materials).hasSize(1);

        CourseMaterial material = materials.getFirst();
        assertThat(material.getUploaderUserId()).isEqualTo(1L);
        assertThat(material.getFilePath()).isNotBlank();
        assertThat(Files.readString(storageRoot.resolve(material.getFilePath()))).isEqualTo("real material bytes");
    }

    @Test
    void studentCanListVisibleMaterialsOnly() throws Exception {
        long visibleMaterialId = insertMaterial("公共资料", "all.txt", 1L, "ALL", "公共资料 bytes");
        insertMaterial("教师资料", "teacher.txt", 1L, "TEACHER", "教师资料 bytes");

        mockMvc.perform(get("/api/v1/materials")
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(visibleMaterialId))
                .andExpect(jsonPath("$.data[0].title").value("公共资料"));
    }

    @Test
    void downloadFailsWhenMaterialIsNotVisibleToCurrentStudent() throws Exception {
        long hiddenMaterialId = insertMaterial("教师专用", "hidden.txt", 1L, "TEACHER", "教师专用 bytes");

        mockMvc.perform(get("/api/v1/materials/{id}/download", hiddenMaterialId)
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void visibleDownloadReturnsStoredBytes() throws Exception {
        long visibleMaterialId = insertMaterial("公共资料", "download.txt", 1L, "ALL", "download bytes");

        mockMvc.perform(get("/api/v1/materials/{id}/download", visibleMaterialId)
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''download.txt")))
                .andExpect(content().bytes("download bytes".getBytes()));
    }

    @Test
    void seededVisibleMaterialDownloadReturnsBytes() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO course_material (id, title, category, description, file_path, file_name, uploader_user_id, visibility, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                200L,
                "实验安全规范",
                "NOTICE",
                "实验前必读",
                "materials/lab-safety.txt",
                "lab-safety.txt",
                1L,
                "ALL"
        );
        seedMaterialInitializer.run(new DefaultApplicationArguments(new String[0]));
        byte[] seededBytes = new ClassPathResource("seed-files/materials/lab-safety.txt").getInputStream().readAllBytes();

        mockMvc.perform(get("/api/v1/materials/{id}/download", 200L)
                        .header("Authorization", bearerToken(studentUser(2L, "20260001", "演示学生"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''lab-safety.txt")))
                .andExpect(content().bytes(seededBytes));
    }

    @Test
    void teacherCanListOnlyOwnMaterials() throws Exception {
        long ownMaterialId = insertMaterial("我的资料", "mine.txt", 1L, "ALL", "mine bytes");
        insertMaterial("他人资料", "others.txt", 9L, "ALL", "others bytes");

        mockMvc.perform(get("/api/v1/materials")
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ownMaterialId));
    }

    @Test
    void teacherCanUpdateOwnMaterialMetadata() throws Exception {
        long materialId = insertMaterial("我的资料", "mine.txt", 1L, "ALL", "mine bytes");

        mockMvc.perform(put("/api/v1/materials/{id}", materialId)
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "更新后的资料",
                                  "category": "NOTICE",
                                  "description": "更新说明",
                                  "visibility": "TEACHER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("更新后的资料"))
                .andExpect(jsonPath("$.data.visibility").value("TEACHER"));
    }

    @Test
    void teacherCannotDeleteOthersMaterial() throws Exception {
        long materialId = insertMaterial("他人资料", "others.txt", 9L, "ALL", "others bytes");

        mockMvc.perform(delete("/api/v1/materials/{id}", materialId)
                        .header("Authorization", bearerToken(teacherUser(1L, "t9001", "演示教师"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private long insertMaterial(String title, String fileName, Long uploaderUserId, String visibility, String fileContent) throws Exception {
        Path relativePath = Path.of("seed", fileName);
        Path fullPath = storageRoot.resolve(relativePath);
        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, fileContent);

        jdbcTemplate.update(
                """
                INSERT INTO course_material (title, category, description, file_path, file_name, uploader_user_id, visibility, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                title,
                "COURSE",
                title + " desc",
                relativePath.toString().replace('\\', '/'),
                fileName,
                uploaderUserId,
                visibility
        );
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM course_material", Long.class);
        assertThat(id).isNotNull();
        return id;
    }

    private String bearerToken(SysUser user) {
        return IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                user.getId()
        );
    }

    private SysUser teacherUser(Long id, String username, String displayName) {
        return buildUser(id, username, displayName, UserRole.TEACHER);
    }

    private SysUser studentUser(Long id, String username, String displayName) {
        return buildUser(id, username, displayName, UserRole.STUDENT);
    }

    private SysUser buildUser(Long id, String username, String displayName, UserRole role) {
        SysUser user = new SysUser() {
            @Override
            public Long getId() {
                return id;
            }
        };
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("{noop}123456");
        return user;
    }
}
