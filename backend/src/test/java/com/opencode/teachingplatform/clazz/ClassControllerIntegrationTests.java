package com.opencode.teachingplatform.clazz;

import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @Test
    void createClassEndpointRemoved() throws Exception {
        Long teacherId = sysUserRepository.findByUsernameAndRole("t9001", UserRole.TEACHER).orElseThrow().getId();
        String bearer = IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                teacherId
        );

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("Request method 'POST' is not supported"));
    }
}
