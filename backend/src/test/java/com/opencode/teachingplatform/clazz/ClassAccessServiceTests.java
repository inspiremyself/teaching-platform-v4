package com.opencode.teachingplatform.clazz;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.service.ClassAccessService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(ClassAccessService.class)
class ClassAccessServiceTests {

    @Autowired
    private ClassAccessService classAccessService;

    @Test
    void assignedTeacherCanAccessSeededClass() {
        CurrentUser teacher = new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER);
        assertThat(classAccessService.listAssignedClasses(teacher))
                .anyMatch(room -> "SE2026-1".equals(room.getCode()));
        assertThatCode(() -> classAccessService.assertTeacherAssignedToClass(teacher, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void unassignedTeacherGetsForbidden() {
        CurrentUser otherTeacher = new CurrentUser(999L, "t9999", "其他教师", UserRole.TEACHER);
        assertThatThrownBy(() -> classAccessService.assertTeacherAssignedToClass(otherTeacher, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(40300);
    }
}
