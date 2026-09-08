package com.opencode.teachingplatform.clazz;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
import com.opencode.teachingplatform.clazz.controller.ClassController;
import com.opencode.teachingplatform.clazz.service.ClassService;
import com.opencode.teachingplatform.common.api.PagedResult;
import com.opencode.teachingplatform.common.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class ClassControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClassService classService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void teacherCanListAssignedClassesWithMyAssignment() throws Exception {
        UsernamePasswordAuthenticationToken authenticationToken = authenticateTeacher(1L, "t9001");
        Map<String, Object> myAssignment = new LinkedHashMap<>();
        myAssignment.put("role", "SUBJECT");
        myAssignment.put("subjectName", "软件体系结构");
        when(classService.listTeacherClasses(any(CurrentUser.class))).thenReturn(new PagedResult<>(List.of(
                Map.of(
                        "id", 1,
                        "name", "软件工程 2026-1 班",
                        "code", "SE2026-1",
                        "status", "ACTIVE",
                        "studentCount", 1,
                        "myAssignment", myAssignment
                )
        ), 1, 10, 1));

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try {
            mockMvc.perform(get("/api/v1/classes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items[0].code").value("SE2026-1"))
                    .andExpect(jsonPath("$.data.items[0].myAssignment.role").value("SUBJECT"))
                    .andExpect(jsonPath("$.data.items[0].myAssignment.subjectName").value("软件体系结构"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken authenticateTeacher(Long id, String username) {
        CurrentUser currentUser = new CurrentUser(id, username, "演示教师", UserRole.TEACHER);
        return new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
    }
}
