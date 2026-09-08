package com.opencode.teachingplatform.auth.repository;

import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLoginSessionRepository extends JpaRepository<UserLoginSession, Long> {
    Optional<UserLoginSession> findFirstByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, LoginSessionStatus status);

    @EntityGraph(attributePaths = "user")
    Optional<UserLoginSession> findBySessionKey(String sessionKey);
}
