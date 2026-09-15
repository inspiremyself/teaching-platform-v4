package com.opencode.teachingplatform.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditLogControllerTests extends AdminIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        clearSecurityContext();
    }

    @Test
    void list_scopedToSchool() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO audit_log (school_id, action, resource_type, created_at, updated_at)
                VALUES (999, 'LOGIN_FAILURE', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long otherSchoolRowId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM audit_log", Long.class);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", adminBearer())
                        .param("page", "1")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + otherSchoolRowId + ")]").doesNotExist());
    }

    @Test
    void filterByActionAndDate() throws Exception {
        OffsetDateTime stale = OffsetDateTime.now().minusDays(10);
        jdbcTemplate.update(
                """
                        INSERT INTO audit_log (school_id, user_id, action, resource_type, created_at, updated_at)
                        VALUES (1, ?, 'STUDENT_UPDATE', 'STUDENT', ?, ?)
                        """,
                schoolAdminId(),
                Timestamp.from(stale.toInstant()),
                Timestamp.from(stale.toInstant())
        );
        Long staleRowId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM audit_log", Long.class);

        jdbcTemplate.update("""
                INSERT INTO audit_log (school_id, user_id, action, resource_type, created_at, updated_at)
                VALUES (1, ?, 'STUDENT_UPDATE', 'STUDENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, schoolAdminId());

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", adminBearer())
                        .param("action", "STUDENT_UPDATE")
                        .param("from", today)
                        .param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.items[0].action").value("STUDENT_UPDATE"))
                .andExpect(jsonPath("$.data.items[?(@.id==" + staleRowId + ")]").doesNotExist());
    }

    @Test
    void invalidAction_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", adminBearer())
                        .param("action", "NOT_AN_ACTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
