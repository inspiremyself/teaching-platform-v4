package com.opencode.teachingplatform.clazz;

import com.opencode.teachingplatform.admin.service.AdminTeacherService;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.SchoolScopeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校管端教师列表与任教班级计数的学校维度回归。
 *
 * <p>同一教师可同时任教 A / B 两校班级，计数必须只统计其所属学校的班级，
 * 列表也必须只返回本校教师。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ClassTeacherSchoolScopeTests {

    private static final Long SCOPED_TEACHER_ID = 9301L;
    private static final Long SCHOOL_B_TEACHER_ID = 9304L;
    private static final Long CLASS_A_SCOPED = 9100L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminTeacherService adminTeacherService;

    @BeforeEach
    void setUp() {
        SchoolScopeFixtures.seedSecondSchool(jdbc);
        jdbc.update("DELETE FROM class_teacher WHERE teacher_user_id = ?", SCOPED_TEACHER_ID);
        jdbc.update("DELETE FROM sys_user WHERE id = ?", SCOPED_TEACHER_ID);
        jdbc.update("DELETE FROM class_room WHERE id = ?", CLASS_A_SCOPED);
        jdbc.update("INSERT INTO sys_user (id, username, password_hash, display_name, role, status, school_id) "
                        + "VALUES (?, 't9301', '{noop}test', '跨校任教教师', 'TEACHER', 'ACTIVE', ?)",
                SCOPED_TEACHER_ID, SchoolScopeFixtures.SCHOOL_A);
        jdbc.update("INSERT INTO class_room (id, name, code, status, school_id) "
                        + "VALUES (?, 'A校班级', 'CLASS-A-9301', 'ACTIVE', ?)",
                CLASS_A_SCOPED, SchoolScopeFixtures.SCHOOL_A);
        jdbc.update("INSERT INTO class_teacher (class_id, teacher_user_id, role, assigned_at) VALUES (?, ?, 'SUBJECT', CURRENT_TIMESTAMP)",
                CLASS_A_SCOPED, SCOPED_TEACHER_ID);
        jdbc.update("INSERT INTO class_teacher (class_id, teacher_user_id, role, assigned_at) VALUES (?, ?, 'SUBJECT', CURRENT_TIMESTAMP)",
                SchoolScopeFixtures.CLASS_B, SCOPED_TEACHER_ID);
        jdbc.update("DELETE FROM sys_user WHERE id = ?", SCHOOL_B_TEACHER_ID);
        jdbc.update("INSERT INTO sys_user (id, username, password_hash, role, display_name, status, school_id) "
                        + "VALUES (?, 't9304', '{noop}test', 'TEACHER', 'B校教师', 'ACTIVE', ?)",
                SCHOOL_B_TEACHER_ID, SchoolScopeFixtures.SCHOOL_B);
    }

    @Test
    void activeClassCountExcludesOtherSchool() {
        List<Map<String, Object>> teachers = adminTeacherService.listTeachers(admin(SchoolScopeFixtures.SCHOOL_A), null);

        Map<String, Object> scoped = teachers.stream()
                .filter(view -> SCOPED_TEACHER_ID.equals(view.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("A 校教师列表缺少 id = " + SCOPED_TEACHER_ID));
        assertThat(scoped.get("classCount")).isEqualTo(1L);
    }

    @Test
    void teacherListKeepsOwnSchoolOnly() {
        List<Long> schoolAIds = teacherIds(adminTeacherService.listTeachers(admin(SchoolScopeFixtures.SCHOOL_A), null));
        List<Long> schoolBIds = teacherIds(adminTeacherService.listTeachers(admin(SchoolScopeFixtures.SCHOOL_B), null));

        assertThat(schoolAIds).contains(SCOPED_TEACHER_ID).doesNotContain(SCHOOL_B_TEACHER_ID);
        assertThat(schoolBIds).contains(SCHOOL_B_TEACHER_ID).doesNotContain(SCOPED_TEACHER_ID);
    }

    private List<Long> teacherIds(List<Map<String, Object>> teachers) {
        return teachers.stream().map(view -> (Long) view.get("id")).toList();
    }

    private CurrentUser admin(Long schoolId) {
        return new CurrentUser(3L, "admin" + schoolId, "校管" + schoolId, UserRole.SCHOOL_ADMIN, schoolId, "session-a" + schoolId);
    }
}
