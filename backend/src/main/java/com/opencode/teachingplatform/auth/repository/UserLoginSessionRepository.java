package com.opencode.teachingplatform.auth.repository;

import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface UserLoginSessionRepository extends JpaRepository<UserLoginSession, Long> {
    Optional<UserLoginSession> findFirstByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, LoginSessionStatus status);

    @EntityGraph(attributePaths = "user")
    Optional<UserLoginSession> findBySessionKey(String sessionKey);

    /**
     * 批量踢掉某用户的全部 ACTIVE 会话。
     *
     * <p>用于校管重置密码：用户所有在线设备的下一次请求都会因会话状态非 ACTIVE 被拒绝。</p>
     *
     * @param userId        用户 id
     * @param fromStatus    期望的原始状态（通常为 ACTIVE）
     * @param toStatus      目标状态（通常为 KICKED）
     * @param invalidatedAt 失效时间
     * @return 受影响行数
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE UserLoginSession s
               SET s.status = :toStatus, s.invalidatedAt = :invalidatedAt
             WHERE s.user.id = :userId AND s.status = :fromStatus
            """)
    int kickActiveSessionsByUserId(@Param("userId") Long userId,
                                   @Param("fromStatus") LoginSessionStatus fromStatus,
                                   @Param("toStatus") LoginSessionStatus toStatus,
                                   @Param("invalidatedAt") OffsetDateTime invalidatedAt);

    /**
     * 批量踢掉某用户除指定 sessionKey 外的全部 ACTIVE 会话。
     *
     * <p>用于本人修改密码：保留当前设备的登录态，其余设备强制下线。</p>
     *
     * @param userId        用户 id
     * @param sessionKey    需要保留的当前会话标识
     * @param fromStatus    期望的原始状态（通常为 ACTIVE）
     * @param toStatus      目标状态（通常为 KICKED）
     * @param invalidatedAt 失效时间
     * @return 受影响行数
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE UserLoginSession s
               SET s.status = :toStatus, s.invalidatedAt = :invalidatedAt
             WHERE s.user.id = :userId AND s.status = :fromStatus AND s.sessionKey <> :sessionKey
            """)
    int kickActiveSessionsByUserIdExceptSessionKey(@Param("userId") Long userId,
                                                   @Param("sessionKey") String sessionKey,
                                                   @Param("fromStatus") LoginSessionStatus fromStatus,
                                                   @Param("toStatus") LoginSessionStatus toStatus,
                                                   @Param("invalidatedAt") OffsetDateTime invalidatedAt);
}
