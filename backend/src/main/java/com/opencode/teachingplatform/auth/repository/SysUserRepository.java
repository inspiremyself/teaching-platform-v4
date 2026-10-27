package com.opencode.teachingplatform.auth.repository;

import com.opencode.teachingplatform.auth.entity.SysUser;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.common.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsernameAndRole(String username, UserRole role);
    Optional<SysUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsBySchoolIdAndUsername(Long schoolId, String username);
    List<SysUser> findBySchoolIdAndRole(Long schoolId, UserRole role);
    List<SysUser> findBySchoolIdAndRoleAndStatus(Long schoolId, UserRole role, UserStatus status);
    Optional<SysUser> findByIdAndSchoolId(Long id, Long schoolId);
    Optional<SysUser> findByIdAndSchoolIdAndRole(Long id, Long schoolId, UserRole role);

    /**
     * 按关键词模糊匹配用户 id（姓名或用户名，忽略大小写）。
     *
     * <p>跨字段 OR 无法用派生查询表达，因此用 {@code @Query}；
     * 统一 {@code lower(...) like} 是为了让 MySQL（默认 ci 排序规则）与 H2（默认区分大小写）行为一致。
     * 入参 {@code pattern} 需由调用方拼好，形如 {@code %keyword%} 且已转小写。</p>
     */
    @Query("""
            select u.id from SysUser u
            where lower(u.displayName) like :pattern or lower(u.username) like :pattern
            """)
    List<Long> findIdsByKeyword(@Param("pattern") String pattern);
}
