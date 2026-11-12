package com.opencode.teachingplatform.common.security;

import com.opencode.teachingplatform.admin.controller.AdminSubjectController;
import com.opencode.teachingplatform.auth.controller.AuthController;
import com.opencode.teachingplatform.exam.controller.ExamController;
import com.opencode.teachingplatform.lab.controller.LabController;
import com.opencode.teachingplatform.material.controller.MaterialController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerAuthorizationTests {

    @Test
    void everyEndpointIsCoveredByMethodOrClassLevelPreAuthorize() throws Exception {
        List<String> uncovered = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            boolean classLevel = controller.isAnnotationPresent(PreAuthorize.class);
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                if (controller.equals(AuthController.class)) {
                    continue;
                }
                if (!classLevel && AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) == null) {
                    uncovered.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(uncovered).isEmpty();
    }

    @Test
    void authControllerIsTheOnlyAnnotationException() throws Exception {
        assertThat(controllers()).contains(AuthController.class);
        assertThat(AuthController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
    }

    @Test
    void fixedAuthorizationValuesMatchTechnicalSpec() {
        assertThat(valueOf(ExamController.class, "list")).isEqualTo("hasAnyRole('TEACHER','STUDENT')");
        assertThat(valueOf(MaterialController.class, "list"))
                .isEqualTo("hasAnyRole('TEACHER','STUDENT','SCHOOL_ADMIN')");
        assertThat(valueOf(MaterialController.class, "download"))
                .isEqualTo("hasAnyRole('TEACHER','STUDENT','SCHOOL_ADMIN')");
        assertThat(valueOf(LabController.class, "previewAnswerImage"))
                .isEqualTo("hasAnyRole('TEACHER','STUDENT')");
        assertThat(valueOf(AdminSubjectController.class, "list"))
                .isEqualTo("hasAnyRole('SCHOOL_ADMIN','TEACHER')");
        assertThat(valueOf(AdminSubjectController.class, "create"))
                .isEqualTo("hasRole('SCHOOL_ADMIN')");
        assertThat(valueOf(AdminSubjectController.class, "update"))
                .isEqualTo("hasRole('SCHOOL_ADMIN')");
        assertThat(valueOf(AdminSubjectController.class, "changeStatus"))
                .isEqualTo("hasRole('SCHOOL_ADMIN')");
        assertThat(valueOf(AdminSubjectController.class, "listPlatform"))
                .isEqualTo("hasRole('SCHOOL_ADMIN')");
        assertThat(valueOf(AdminSubjectController.class, "enablePlatform"))
                .isEqualTo("hasRole('SCHOOL_ADMIN')");
    }

    @Test
    void platformSchoolControllerIsClassLevelPlatformAdminOnly() {
        Class<?> controller = com.opencode.teachingplatform.admin.controller.PlatformSchoolController.class;
        PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
        assertThat(controller.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/admin/schools");
    }

    private List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.opencode.teachingplatform")) {
            result.add(Class.forName(definition.getBeanClassName()));
        }
        return result;
    }

    private String valueOf(Class<?> controller, String methodName) {
        for (Method method : controller.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            assertThat(annotation).as(controller.getSimpleName() + "#" + methodName).isNotNull();
            return annotation.value();
        }
        throw new AssertionError("method not found: " + controller.getSimpleName() + "#" + methodName);
    }
}
