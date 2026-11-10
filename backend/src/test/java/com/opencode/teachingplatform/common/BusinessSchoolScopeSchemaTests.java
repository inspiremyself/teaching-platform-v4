package com.opencode.teachingplatform.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** V26 迁移后的六表结构、索引、种子保全与派生表 ①② 二分断言。 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:school_scope_schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class BusinessSchoolScopeSchemaTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void sixTablesHaveNotNullSchoolId() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : SchoolScopeFixtures.TABLES) {
                boolean found = false;
                int nullable = Integer.MIN_VALUE;
                for (String actual : actualTables(meta, table)) {
                    try (ResultSet rs = meta.getColumns(null, null, actual, null)) {
                        while (rs.next()) {
                            if ("SCHOOL_ID".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                                found = true;
                                nullable = rs.getInt("NULLABLE");
                            }
                        }
                    }
                }
                assertThat(found).as(table + " 存在 school_id 列").isTrue();
                assertThat(nullable).as(table + " school_id NOT NULL")
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE school_id IS NULL",
                        Integer.class)).as(table + " NULL 行数").isZero();
            }
        }
    }

    @Test
    void sixIndexesExist() throws Exception {
        Set<String> indexNames = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : SchoolScopeFixtures.TABLES) {
                for (String actual : actualTables(meta, table)) {
                    try (ResultSet rs = meta.getIndexInfo(null, null, actual, false, false)) {
                        while (rs.next()) {
                            String name = rs.getString("INDEX_NAME");
                            if (name != null) {
                                indexNames.add(name.toUpperCase());
                            }
                        }
                    }
                }
            }
        }
        for (String index : SchoolScopeFixtures.INDEXES) {
            assertThat(indexNames).as(index).contains(index);
        }
    }

    /** V29：按列组合断言归属维度唯一键，且 code / source_ref 的单列全局唯一已消失。 */
    @Test
    void questionBankCodeUniqueIsOwnerScoped() throws Exception {
        Map<String, List<String>> uniqueColumnsByIndex = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String actual : actualTables(meta, "question_bank")) {
                try (ResultSet rs = meta.getIndexInfo(null, null, actual, false, false)) {
                    while (rs.next()) {
                        if (rs.getBoolean("NON_UNIQUE")) {
                            continue;
                        }
                        String indexName = rs.getString("INDEX_NAME");
                        if (indexName == null) {
                            continue;
                        }
                        int position = rs.getShort("ORDINAL_POSITION");
                        String column = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                        List<String> columns = uniqueColumnsByIndex.computeIfAbsent(indexName, key -> new ArrayList<>());
                        while (columns.size() < position) {
                            columns.add("");
                        }
                        columns.set(position - 1, column);
                    }
                }
            }
        }
        assertThat(uniqueColumnsByIndex.values())
                .contains(List.of("school_id", "created_by", "code"), List.of("school_id", "source_ref"))
                .doesNotContain(List.of("code"), List.of("source_ref"));
    }

    /** H2 对 V9 的 CREATE TABLE IF NOT EXISTS t_experiment 存的表名大小写与其它表不一致，按实际表名回查。 */
    private Set<String> actualTables(DatabaseMetaData meta, String logical) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(logical)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @Test
    void seedRowsSurviveMigration() {
        assertThat(count("SELECT COUNT(*) FROM homework WHERE id IN (-1,-2,-3) AND school_id = 1")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM exam WHERE id IN (-1,-2,-3) AND school_id = 1")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM question_bank WHERE id IN (1,2,3) AND school_id = 1")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM t_experiment WHERE experiment_id IN (1001,1002,1003) AND school_id = 1"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM score_record WHERE id IN (-1,-2,-3) AND school_id = 1")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM plagiarism_task WHERE id IN (-1,-2) AND school_id = 1")).isEqualTo(2);
    }

    /** 只断言 V26 回填的历史行；id >= 9000 是测试夹具按 currentUser.schoolId() 写入的运行时行。 */
    @Test
    void backfillMatchesSource() {
        assertThat(count("SELECT COUNT(*) FROM t_experiment e JOIN class_room cr ON cr.id = e.class_id "
                + "WHERE e.experiment_id < 9000 AND e.school_id <> cr.school_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM homework h JOIN class_room cr ON cr.id = h.class_id "
                + "WHERE h.id < 9000 AND h.school_id <> cr.school_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM exam e JOIN class_room cr ON cr.id = e.class_id "
                + "WHERE e.id < 9000 AND e.school_id <> cr.school_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM question_bank q JOIN sys_user u ON u.id = q.created_by "
                + "WHERE q.id < 9000 AND q.school_id <> u.school_id")).isZero();
    }

    @Test
    void derivedRowsWithBusinessMasterMatch() {
        assertThat(count("SELECT COUNT(*) FROM score_record sr JOIN exam e ON e.id = sr.business_id "
                + "WHERE sr.business_type = 'EXAM' AND sr.school_id <> e.school_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM score_record sr JOIN homework h ON h.id = sr.business_id "
                + "WHERE sr.business_type = 'HOMEWORK' AND sr.school_id <> h.school_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM score_record sr JOIN t_experiment e "
                + "ON e.experiment_id = sr.business_id "
                + "WHERE sr.business_type = 'LAB' AND sr.school_id <> e.school_id")).isZero();
        assertThat(count("SELECT COUNT(*) FROM plagiarism_task p "
                + "JOIN homework_submission hs ON hs.id = p.business_id "
                + "JOIN homework h ON h.id = hs.homework_id "
                + "WHERE p.business_type = 'HOMEWORK' AND p.school_id <> h.school_id")).isZero();
    }

    @Test
    void derivedRowsWithoutBusinessMasterKeepBackfillValue() {
        assertThat(count("SELECT COUNT(*) FROM score_record WHERE business_type = 'LAB' "
                + "AND business_id NOT IN (SELECT experiment_id FROM t_experiment) AND school_id = 1"))
                .as("② 类 score_record 保留回填值").isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM plagiarism_task WHERE business_type = 'LAB' "
                + "AND business_id NOT IN (SELECT id FROM experiment_submission) AND school_id = 1"))
                .as("② 类 plagiarism_task 保留回填值").isGreaterThanOrEqualTo(1);
    }

    @Test
    void noOrphanRowsAfterMigration() {
        assertThat(count("SELECT COUNT(*) FROM homework h LEFT JOIN class_room cr ON cr.id = h.class_id "
                + "WHERE cr.id IS NULL OR cr.school_id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM exam e LEFT JOIN class_room cr ON cr.id = e.class_id "
                + "WHERE cr.id IS NULL OR cr.school_id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM t_experiment e LEFT JOIN class_room cr ON cr.id = e.class_id "
                + "WHERE cr.id IS NULL OR cr.school_id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM question_bank q LEFT JOIN sys_user u ON u.id = q.created_by "
                + "WHERE u.id IS NULL OR u.school_id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM score_record sr LEFT JOIN class_room cr ON cr.id = sr.class_id "
                + "WHERE cr.id IS NULL OR cr.school_id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM plagiarism_task p LEFT JOIN sys_user u ON u.id = p.student_id "
                + "WHERE u.id IS NULL OR u.school_id IS NULL")).isZero();
    }

    private Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
