package com.opencode.teachingplatform.testsupport;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;

import java.time.OffsetDateTime;

public final class IntegrationTestAuthSupport {

    private IntegrationTestAuthSupport() {
    }

    public static String bearerToken(
            JwtTokenService jwtTokenService,
            SysUserRepository sysUserRepository,
            UserLoginSessionRepository userLoginSessionRepository,
            Long userId
    ) {
        SysUser persistedUser = sysUserRepository.findById(userId).orElseThrow();
        String sessionKey = "integration-test-session-" + persistedUser.getId();
        UserLoginSession session = userLoginSessionRepository.findBySessionKey(sessionKey).orElseGet(() -> {
            UserLoginSession newSession = new UserLoginSession();
            newSession.setUser(persistedUser);
            newSession.setSessionKey(sessionKey);
            newSession.setStatus(LoginSessionStatus.ACTIVE);
            newSession.setExpiresAt(OffsetDateTime.now().plusHours(8));
            return userLoginSessionRepository.save(newSession);
        });
        if (session.getStatus() != LoginSessionStatus.ACTIVE || session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(LoginSessionStatus.ACTIVE);
            session.setExpiresAt(OffsetDateTime.now().plusHours(8));
            userLoginSessionRepository.save(session);
        }
        return "Bearer " + jwtTokenService.issueToken(persistedUser, sessionKey);
    }
}
