package com.opencode.teachingplatform.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 双库建表与 ddl-auto 校验：H2 侧 content_json 为 TEXT/CLOB 且 NOT NULL，1MB 单节正文写入成功。 */
class CoursewareContentColumnTests extends ContentIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    void contentJsonColumnIsNotNull() throws Exception {
        boolean found = false;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : actualTableNames(meta, "courseware_section")) {
                try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                    while (rs.next()) {
                        if (!"content_json".equals(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT))) {
                            continue;
                        }
                        found = true;
                        assertThat(rs.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNoNulls);
                        assertThat(rs.getInt("DATA_TYPE")).isIn(Types.CLOB, Types.LONGVARCHAR,
                                Types.VARCHAR, Types.LONGNVARCHAR);
                    }
                }
            }
        }
        assertThat(found).as("courseware_section.content_json 存在").isTrue();
    }

    @Test
    void oneMegabyteSectionBodyCanBePersisted() {
        long cw = insertCourseware(900701L, "PLATFORM", null, platformSubjectId(), null, 999L);
        String body = "x".repeat(1_048_576);
        insertCoursewareSection(900702L, cw, 1, body);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT LENGTH(content_json) FROM courseware_section WHERE id = ?",
                Long.class, 900702L)).isEqualTo(1_048_576L);
        assertThat(coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(cw)).hasSize(1);
    }
}
