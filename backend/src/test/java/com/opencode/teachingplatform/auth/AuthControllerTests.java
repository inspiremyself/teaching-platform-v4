package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.controller.AuthController;
import com.opencode.teachingplatform.auth.service.AuthService;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.auth.security.JwtAuthenticationFilter;
import com.opencode.teachingplatform.auth.security.SecurityConfig;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void schoolAdminCanLoginAndReadCurrentUserWithSchoolInfo() throws Exception {
        when(authService.login(any(com.opencode.teachingplatform.auth.dto.LoginRequest.class))).thenReturn(java.util.Map.of(
                "token", "admin-token",
                "user", java.util.Map.of(
                        "id", 4,
                        "username", "admin9001",
                        "displayName", "演示校管",
                        "role", "SCHOOL_ADMIN",
                        "schoolId", 1,
                        "schoolName", "演示学校"
                ),
                "permissions", java.util.List.of("CLASS_READ", "CLASS_WRITE")
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin9001",
                                  "password": "123456",
                                  "role": "SCHOOL_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("SCHOOL_ADMIN"))
                .andExpect(jsonPath("$.data.user.schoolId").value(1))
                .andExpect(jsonPath("$.data.user.schoolName").value("演示学校"));

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                new CurrentUser(4L, "admin9001", "演示校管", UserRole.SCHOOL_ADMIN, 1L, "admin-session"),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"))
        );

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try {
            when(authService.getCurrentUserProfile(any())).thenReturn(java.util.Map.of(
                    "id", 4,
                    "username", "admin9001",
                    "displayName", "演示校管",
                    "role", "SCHOOL_ADMIN",
                    "schoolId", 1,
                    "schoolName", "演示学校",
                    "mustChangePassword", false,
                    "permissions", java.util.List.of("CLASS_READ", "CLASS_WRITE")
            ));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.schoolId").value(1))
                    .andExpect(jsonPath("$.data.schoolName").value("演示学校"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherPermissionsExcludeClassWriteOnMeEndpoint() throws Exception {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try {
            when(authService.getCurrentUserProfile(any())).thenReturn(java.util.Map.of(
                    "id", 1,
                    "username", "t9001",
                    "displayName", "演示教师",
                    "role", "TEACHER",
                    "mustChangePassword", false,
                    "permissions", java.util.List.of("CLASS_READ", "MATERIAL_READ")
            ));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.permissions").isArray())
                    .andExpect(jsonPath("$.data.permissions", not(hasItem("CLASS_WRITE"))));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void teacherCanLoginAndReadCurrentUser() throws Exception {
        when(authService.login(any(com.opencode.teachingplatform.auth.dto.LoginRequest.class))).thenReturn(java.util.Map.of(
                "token", "teacher-token",
                "user", java.util.Map.of(
                        "id", 1,
                        "username", "t9001",
                        "displayName", "演示教师",
                        "role", "TEACHER"
                ),
                "permissions", java.util.List.of("CLASS_READ")
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "t9001",
                                  "password": "123456",
                                  "role": "TEACHER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.token").value("teacher-token"))
                .andExpect(jsonPath("$.data.user.username").value("t9001"))
                .andExpect(jsonPath("$.data.user.role").value("TEACHER"));

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try {
            when(authService.getCurrentUserProfile(any())).thenReturn(java.util.Map.of(
                    "id", 1,
                    "username", "t9001",
                    "displayName", "演示教师",
                    "role", "TEACHER",
                    "email", "teacher@university.edu",
                    "phone", "+86 13800000000",
                    "officeHours", "周一 14:00-16:00",
                    "bio", "负责课程与实验指导",
                    "mustChangePassword", false,
                    "permissions", java.util.List.of("CLASS_READ")
            ));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("OK"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.data.username").value("t9001"))
                    .andExpect(jsonPath("$.data.role").value("TEACHER"))
                    .andExpect(jsonPath("$.data.email").value("teacher@university.edu"))
                    .andExpect(jsonPath("$.data.permissions").isArray());
        } finally {
            SecurityContextHolder.clearContext();
        }

    }

    @Test
    void loginFailsWhenRoleDoesNotMatch() throws Exception {
        when(authService.login(any(com.opencode.teachingplatform.auth.dto.LoginRequest.class)))
                .thenThrow(new com.opencode.teachingplatform.common.exception.BusinessException(40101, "用户名、密码或角色错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "t9001",
                                  "password": "123456",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名、密码或角色错误"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void loginReturnsConflictWhenAccountAlreadyLoggedInElsewhere() throws Exception {
        when(authService.login(any(com.opencode.teachingplatform.auth.dto.LoginRequest.class)))
                .thenThrow(new com.opencode.teachingplatform.common.exception.BusinessException(40901, "该账号已在其他设备登录，继续登录将使旧设备下线"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "t9001",
                                  "password": "123456",
                                  "role": "TEACHER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("该账号已在其他设备登录，继续登录将使旧设备下线"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        when(authService.login(any(com.opencode.teachingplatform.auth.dto.LoginRequest.class)))
                .thenThrow(new com.opencode.teachingplatform.common.exception.BusinessException(40101, "用户名、密码或角色错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "t9001",
                                  "password": "123456",
                                  "role": "TEACHER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名、密码或角色错误"));
    }

    @Test
    void currentUserCanUpdateProfile() throws Exception {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                new CurrentUser(1L, "t9001", "演示教师", UserRole.TEACHER),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
        when(authService.updateProfile(any(), any())).thenReturn(java.util.Map.of(
                "id", 1,
                "username", "t9001",
                "displayName", "王老师",
                "role", "TEACHER",
                "email", "teacher@university.edu",
                "phone", "+86 13800000000",
                "officeHours", "周一 14:00-16:00",
                "bio", "负责课程与实验指导",
                "mustChangePassword", false,
                "permissions", java.util.List.of("CLASS_READ")
        ));
        when(authService.logout(any())).thenReturn(java.util.Map.of("loggedOut", true));

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try {
            mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/auth/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      \"displayName\": \"王老师\",
                                      \"email\": \"teacher@university.edu\",
                                      \"phone\": \"+86 13800000000\",
                                      \"officeHours\": \"周一 14:00-16:00\",
                                      \"bio\": \"负责课程与实验指导\"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.displayName").value("王老师"))
                    .andExpect(jsonPath("$.data.email").value("teacher@university.edu"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
