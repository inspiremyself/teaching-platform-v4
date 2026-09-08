package com.opencode.teachingplatform.admin;

import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

abstract class AdminIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtTokenService jwtTokenService;

    @Autowired
    protected SysUserRepository sysUserRepository;

    @Autowired
    protected UserLoginSessionRepository userLoginSessionRepository;

    protected void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected Long schoolAdminId() {
        return sysUserRepository.findByUsernameAndRole("admin9001", UserRole.SCHOOL_ADMIN).orElseThrow().getId();
    }

    protected Long teacherId(String username) {
        return sysUserRepository.findByUsernameAndRole(username, UserRole.TEACHER).orElseThrow().getId();
    }

    protected String adminBearer() {
        return bearer(schoolAdminId());
    }

    protected String bearer(Long userId) {
        return IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                userId
        );
    }
}
