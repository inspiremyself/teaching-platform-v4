package com.opencode.teachingplatform.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

/** V31 布置记录 schema：列非空、新唯一键 / 索引、旧键不存在、分组顺序快照。 */
class CoursewareAssignmentSchemaTests extends ContentIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    void requiredColumnsAreNotNull() throws Exception {
        Map<String, Boolean> nullable = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : actualTableNames(meta, "courseware_assignment")) {
                try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                    while (rs.next()) {
                        nullable.put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                                rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    }
                }
            }
        }
        for (String column : List.of("school_id", "class_id", "target_type", "target_id",
                "assignment_group_id", "display_order")) {
            assertThat(nullable).as(column).containsKey(column);
            assertThat(nullable.get(column)).as(column + " NOT NULL").isFalse();
        }
        assertThat(nullable.get("section_id")).as("section_id 可空 = 整份").isTrue();
    }

    @Test
    void newUniqueKeyAndIndexesExist() throws Exception {
        Set<String> indexNames = new HashSet<>();
        Map<String, List<String>> uniqueColumns = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            for (String table : actualTableNames(meta, "courseware_assignment")) {
                try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name == null) {
                            continue;
                        }
                        String upper = name.toUpperCase(Locale.ROOT);
                        indexNames.add(upper);
                        if (!rs.getBoolean("NON_UNIQUE")) {
                            int position = rs.getShort("ORDINAL_POSITION");
                            String column = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                            List<String> columns = uniqueColumns.computeIfAbsent(upper, k -> new ArrayList<>());
                            while (columns.size() < position) {
                                columns.add("");
                            }
                            columns.set(position - 1, column);
                        }
                    }
                }
            }
        }
        assertThat(indexNames).contains("IDX_ASSIGNMENT_TARGET", "IDX_ASSIGNMENT_COURSEWARE",
                "IDX_ASSIGNMENT_SCHOOL_CLASS");
        assertThat(indexNames).doesNotContain("UK_ASSIGNMENT_GROUP_ORDER");
        assertThat(uniqueColumns.values()).contains(
                List.of("assignment_group_id", "target_type", "target_id", "display_order"));
    }

    @Test
    void groupOrderSnapshotRoundTripsInAscendingDisplayOrder() {
        long cw = insertCourseware(900501L, "PLATFORM", null, platformSubjectId(), null, 999L);
        String group = "11111111-1111-4111-8111-111111111111";
        insertAssignment(900502L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, 901L, group, 3);
        insertAssignment(900503L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, 902L, group, 1);
        insertAssignment(900504L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, 903L, group, 2);

        assertThat(coursewareAssignmentRepository.findBySchoolAndClass(SCHOOL_ID, CLASS_ID))
                .extracting(row -> row.getDisplayOrder())
                .containsExactly(1, 2, 3);
    }

    @Test
    void wholeCoursewareRowHasNullSectionIdAndZeroDisplayOrder() {
        long cw = insertCourseware(900511L, "PLATFORM", null, platformSubjectId(), null, 999L);
        String group = "22222222-2222-4222-8222-222222222222";
        insertAssignment(900512L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, null, group, 0);

        assertThat(coursewareAssignmentRepository.findBySchoolAndClass(SCHOOL_ID, CLASS_ID))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getSectionId()).isNull();
                    assertThat(row.getDisplayOrder()).isZero();
                    assertThat(row.getTargetType()).isEqualTo("CLASS");
                    assertThat(row.getTargetId()).isEqualTo(CLASS_ID);
                });
        insertCoursewareSection(900513L, cw, 2, "{}");
        insertCoursewareSection(900514L, cw, 1, "{}");
        assertThat(coursewareSectionRepository.findByCoursewareIdOrderBySortOrderAsc(cw))
                .extracting(section -> section.getSortOrder())
                .containsExactly(1, 2);
    }

    @Test
    void rowsAreFilteredBySchool() {
        long cw = insertCourseware(900521L, "PLATFORM", null, platformSubjectId(), null, 999L);
        insertAssignment(900522L, SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, null,
                "33333333-3333-4333-8333-333333333333", 0);
        // 另一学校是一次独立布置，使用不同 assignment_group_id
        insertAssignment(900523L, OTHER_SCHOOL_ID, CLASS_ID, "CLASS", CLASS_ID, cw, null,
                "44444444-4444-4444-8444-444444444444", 0);

        assertThat(coursewareAssignmentRepository.findBySchoolAndClass(SCHOOL_ID, CLASS_ID))
                .hasSize(1)
                .allSatisfy(row -> assertThat(row.getSchoolId()).isEqualTo(SCHOOL_ID));
    }
}
