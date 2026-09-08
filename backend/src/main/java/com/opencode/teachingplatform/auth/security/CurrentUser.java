package com.opencode.teachingplatform.auth.security;

import com.opencode.teachingplatform.common.enums.UserRole;

public record CurrentUser(Long id, String username, String displayName, UserRole role, Long schoolId, String sessionKey) {
    public CurrentUser(Long id, String username, String displayName, UserRole role) {
        this(id, username, displayName, role, 1L, null);
    }
}
